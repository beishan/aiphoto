#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker/docker-compose.yml"
ACTION="${1:-deploy}"
ENV_FILE="${2:-${PROJECT_DIR}/docker/.env.production}"
STATE_FILE="${3:-${PROJECT_DIR}/.memoryvault-previous-images}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-memoryvault}"
BACKUP_VOLUME="${BACKUP_VOLUME:-memoryvault-backups}"
DEPLOY_STATE_VOLUME="${DEPLOY_STATE_VOLUME:-memoryvault-deploy-state}"
BACKUP_RETENTION_COUNT="${BACKUP_RETENTION_COUNT:-10}"
HEALTH_RETRIES="${HEALTH_RETRIES:-120}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"
IMAGE_RETENTION_COUNT="${IMAGE_RETENTION_COUNT:-2}"

if docker compose version >/dev/null 2>&1; then
    COMPOSE_COMMAND=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_COMMAND=(docker-compose)
else
    echo "错误：未找到 docker compose 或 docker-compose。" >&2
    exit 1
fi

if [[ ! -r "${ENV_FILE}" ]]; then
    echo "错误：生产环境变量文件不可读：${ENV_FILE}" >&2
    exit 1
fi

env_file_value() {
    local key="$1"
    sed -n "s/^${key}=//p" "${ENV_FILE}" | tail -n 1
}

effective_value() {
    local key="$1"
    local shell_value="${!key:-}"
    if [[ -n "${shell_value}" ]]; then
        printf '%s' "${shell_value}"
    else
        env_file_value "${key}"
    fi
}

validate_configuration() {
    local key value
    local required=(
        DB_PASSWORD REDIS_PASSWORD RABBITMQ_PASSWORD MINIO_PASSWORD JWT_SECRET
        PHOTO_LIBRARY_PATH AI_MODELS_PATH
    )

    for key in "${required[@]}"; do
        value="$(effective_value "${key}")"
        if [[ -z "${value}" || "${value}" == *"请替换"* ]]; then
            echo "错误：请在 Jenkins Secret file 中配置 ${key}。" >&2
            return 1
        fi
    done

    for key in PHOTO_LIBRARY_PATH AI_MODELS_PATH; do
        value="$(effective_value "${key}")"
        if [[ "${value}" != /* || "${value}" == "/" || "${value}" == *".."* ]]; then
            echo "错误：${key} 必须是 NAS 上的安全绝对路径：${value}" >&2
            return 1
        fi
    done

    value="$(effective_value JWT_SECRET)"
    if ((${#value} < 32)); then
        echo "错误：JWT_SECRET 至少需要 32 个字符。" >&2
        return 1
    fi

    if [[ ! "${IMAGE_RETENTION_COUNT}" =~ ^[1-9][0-9]*$ ]]; then
        echo "错误：IMAGE_RETENTION_COUNT 必须是大于 0 的整数。" >&2
        return 1
    fi
}

compose() {
    "${COMPOSE_COMMAND[@]}" \
        --project-name "${COMPOSE_PROJECT_NAME}" \
        --env-file "${ENV_FILE}" \
        --file "${COMPOSE_FILE}" \
        "$@"
}

container_image() {
    docker inspect --format '{{.Config.Image}}' "$1" 2>/dev/null || true
}

container_health() {
    docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$1" 2>/dev/null || echo missing
}

record_previous_images() {
    local backend_image frontend_image ai_image
    backend_image="$(container_image memoryvault-backend)"
    frontend_image="$(container_image memoryvault-frontend)"
    ai_image="$(container_image memoryvault-ai)"

    {
        printf 'BACKEND_IMAGE=%q\n' "${backend_image}"
        printf 'FRONTEND_IMAGE=%q\n' "${frontend_image}"
        printf 'AI_IMAGE=%q\n' "${ai_image}"
    } > "${STATE_FILE}"

    echo "已记录部署前镜像："
    echo "  backend=${backend_image:-<首次部署>}"
    echo "  frontend=${frontend_image:-<首次部署>}"
    echo "  ai=${ai_image:-<首次部署>}"
}

backup_database() {
    local backup_name
    if ! docker inspect memoryvault-postgres >/dev/null 2>&1; then
        echo "PostgreSQL 容器尚未运行，跳过首次部署前备份。"
        return 0
    fi

    backup_name="memoryvault-$(date '+%Y%m%d-%H%M%S').dump"
    docker volume create "${BACKUP_VOLUME}" >/dev/null
    echo "正在备份 PostgreSQL：${backup_name}"
    docker exec memoryvault-postgres pg_dump -U memoryvault -d memoryvault -Fc |
        docker run --rm -i -v "${BACKUP_VOLUME}:/backups" alpine:3.20 \
            sh -c "cat > '/backups/${backup_name}'"

    docker run --rm -v "${BACKUP_VOLUME}:/backups" alpine:3.20 \
        sh -c "ls -1t /backups/memoryvault-*.dump 2>/dev/null | awk 'NR > ${BACKUP_RETENTION_COUNT}' | while IFS= read -r file; do rm -f \"\$file\"; done"
}

wait_for_containers() {
    local attempt container_name status all_healthy
    local containers=(
        memoryvault-postgres memoryvault-redis memoryvault-rabbitmq
        memoryvault-minio memoryvault-ai memoryvault-backend memoryvault-frontend
    )

    for ((attempt = 1; attempt <= HEALTH_RETRIES; attempt++)); do
        all_healthy=true
        echo "容器健康检查 ${attempt}/${HEALTH_RETRIES}"
        for container_name in "${containers[@]}"; do
            status="$(container_health "${container_name}")"
            echo "  ${container_name}: ${status}"
            [[ "${status}" == healthy ]] || all_healthy=false
        done
        [[ "${all_healthy}" == true ]] && return 0
        sleep "${HEALTH_INTERVAL_SECONDS}"
    done
    return 1
}

persist_release_state() {
    local current_state_file
    current_state_file="$(mktemp)"
    {
        printf 'BACKEND_IMAGE=%q\n' "$(container_image memoryvault-backend)"
        printf 'FRONTEND_IMAGE=%q\n' "$(container_image memoryvault-frontend)"
        printf 'AI_IMAGE=%q\n' "$(container_image memoryvault-ai)"
    } > "${current_state_file}"

    docker volume create "${DEPLOY_STATE_VOLUME}" >/dev/null
    if [[ -s "${STATE_FILE}" ]]; then
        docker run --rm -i -v "${DEPLOY_STATE_VOLUME}:/state" alpine:3.20 \
            sh -c 'cat > /state/previous.env' < "${STATE_FILE}"
    fi
    docker run --rm -i -v "${DEPLOY_STATE_VOLUME}:/state" alpine:3.20 \
        sh -c 'cat > /state/current.env' < "${current_state_file}"
    rm -f "${current_state_file}"
}

rollback_from_state() {
    local previous_backend previous_frontend previous_ai
    if [[ ! -r "${STATE_FILE}" ]]; then
        echo "错误：找不到回滚状态文件：${STATE_FILE}" >&2
        return 1
    fi

    # 状态文件只由 record_previous_images 生成，值已做 shell 转义。
    # shellcheck disable=SC1090
    source "${STATE_FILE}"
    previous_backend="${BACKEND_IMAGE:-}"
    previous_frontend="${FRONTEND_IMAGE:-}"
    previous_ai="${AI_IMAGE:-}"
    if [[ -z "${previous_backend}" || -z "${previous_frontend}" || -z "${previous_ai}" ]]; then
        echo "没有完整的上一版本镜像，无法自动回滚（首次部署时正常）。" >&2
        return 1
    fi

    export BACKEND_IMAGE="${previous_backend}"
    export FRONTEND_IMAGE="${previous_frontend}"
    export AI_IMAGE="${previous_ai}"
    echo "正在回滚到：${BACKEND_IMAGE}, ${FRONTEND_IMAGE}, ${AI_IMAGE}"
    compose up -d --no-build ai-service backend frontend
    if ! wait_for_containers; then
        compose logs --no-color --tail=200 ai-service backend frontend || true
        return 1
    fi
    echo "已恢复上一版本。"
}

cleanup_repository_images() {
    local repository="$1" current_image="$2" retained_previous=0 image_ref
    local previous_limit=$((IMAGE_RETENTION_COUNT - 1))
    while IFS= read -r image_ref; do
        [[ -n "${image_ref}" && "${image_ref}" != *':<none>' ]] || continue
        if [[ "${image_ref}" == "${current_image}" ]]; then
            continue
        fi
        if ((retained_previous < previous_limit)); then
            retained_previous=$((retained_previous + 1))
            continue
        fi
        echo "清理旧镜像：${image_ref}"
        docker image rm "${image_ref}" >/dev/null 2>&1 || true
    done < <(docker image ls "${repository}" --format '{{.Repository}}:{{.Tag}}' | awk '!seen[$0]++')
}

cleanup_dangling_images() {
    echo "清理 MemoryVault 悬空镜像。"
    docker image prune --force \
        --filter 'label=com.memoryvault.managed=true' >/dev/null 2>&1 || true
}

deploy_release() {
    record_previous_images
    backup_database
    echo "正在更新 MemoryVault 服务。"
    if ! compose up -d --remove-orphans; then
        compose logs --no-color --tail=300 || true
        rollback_from_state || true
        return 1
    fi
    if ! wait_for_containers; then
        compose logs --no-color --tail=300 || true
        rollback_from_state || true
        return 1
    fi
    persist_release_state
    echo "MemoryVault 新版本部署成功。"
}

validate_configuration

case "${ACTION}" in
    validate)
        compose config --quiet
        echo "Docker Compose 配置校验通过。"
        ;;
    test)
        docker build --file "${PROJECT_DIR}/backend/Dockerfile" --target test \
            --tag "memoryvault-backend-test:${RELEASE_TAG:-local}" "${PROJECT_DIR}/backend"
        ;;
    build)
        compose build postgres backend frontend ai-service
        ;;
    deploy)
        deploy_release
        ;;
    health)
        wait_for_containers
        ;;
    cleanup)
        cleanup_repository_images memoryvault-backend "$(container_image memoryvault-backend)"
        cleanup_repository_images memoryvault-frontend "$(container_image memoryvault-frontend)"
        cleanup_repository_images memoryvault-ai "$(container_image memoryvault-ai)"
        cleanup_dangling_images
        ;;
    rollback)
        rollback_from_state
        ;;
    logs)
        compose logs --no-color --tail=200
        ;;
    *)
        echo "用法：$0 {validate|test|build|deploy|health|cleanup|rollback|logs} [env-file] [state-file]" >&2
        exit 2
        ;;
esac
