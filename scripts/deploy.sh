#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker/docker-compose.yml"
COMPOSE_GPU_FILE="${PROJECT_DIR}/docker/docker-compose.gpu.yml"
ACTION="${1:-deploy}"
ENV_FILE="${2:-${PROJECT_DIR}/docker/.env.production}"
STATE_FILE="${3:-${PROJECT_DIR}/.aiphoto-previous-images}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-aiphoto}"
BACKUP_VOLUME="${BACKUP_VOLUME:-aiphoto-backups}"
DEPLOY_STATE_VOLUME="${DEPLOY_STATE_VOLUME:-aiphoto-deploy-state}"
BACKUP_RETENTION_COUNT="${BACKUP_RETENTION_COUNT:-10}"
HEALTH_RETRIES="${HEALTH_RETRIES:-120}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"

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

gpu_enabled() {
    case "$(effective_value ENABLE_GPU)" in
        1 | true | TRUE | yes | YES) return 0 ;;
        *) return 1 ;;
    esac
}

validate_configuration() {
    local key value
    local required=(
        APP_VERSION DB_PASSWORD JWT_SECRET PHOTO_LIBRARY_PATH AI_MODELS_PATH STORAGE_DATA_PATH
    )

    for key in "${required[@]}"; do
        value="$(effective_value "${key}")"
        if [[ -z "${value}" || "${value}" == *"请替换"* ]]; then
            echo "错误：请在 Jenkins Secret file 中配置 ${key}。" >&2
            return 1
        fi
    done

    for key in PHOTO_LIBRARY_PATH AI_MODELS_PATH STORAGE_DATA_PATH; do
        value="$(effective_value "${key}")"
        if [[ "${value}" != /* || "${value}" == "/" || "${value}" == *".."* ]]; then
            echo "错误：${key} 必须是 NAS 上的安全绝对路径：${value}" >&2
            return 1
        fi
    done

    value="$(effective_value JWT_SECRET)"
    if ((${#value} < 32)); then
        echo "错误：JWT_SECRET 至少需要 32 个字符（纯文本，无需 Base64）。" >&2
        return 1
    fi

    value="$(effective_value APP_VERSION)"
    if [[ ! "${value}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "错误：APP_VERSION 必须使用语义化版本号（例如 0.2.2）：${value}" >&2
        return 1
    fi

    value="$(effective_value ENABLE_GPU)"
    if [[ -n "${value}" && ! "${value}" =~ ^(0|1|true|false|TRUE|FALSE|yes|no|YES|NO)$ ]]; then
        echo "错误：ENABLE_GPU 必须是 true 或 false。" >&2
        return 1
    fi
}

storage_mounts() {
    docker inspect --format '{{range .Mounts}}{{printf "%s=%s\n" .Destination .Source}}{{end}}' aiphoto-backend 2>/dev/null |
        awk -F= '$1 == "/storage-pools" || $1 ~ /^\/storage-pools\/[^/]+$/'
}

saved_storage_mounts() {
    if docker volume inspect "${DEPLOY_STATE_VOLUME}" >/dev/null 2>&1; then
        docker run --rm --pull never -v "${DEPLOY_STATE_VOLUME}:/state:ro" alpine:3.20 \
            sh -ec 'if [ -f /state/storage-mappings ]; then cat /state/storage-mappings; fi'
    fi
}

previous_storage_mounts() {
    local saved
    saved="$(saved_storage_mounts)" || return 1
    if [[ -n "${saved}" ]]; then
        printf '%s\n' "${saved}"
    else
        storage_mounts || true
    fi
}

persist_storage_mounts() {
    local mounts
    mounts="$(storage_mounts)" || return 1
    docker volume create "${DEPLOY_STATE_VOLUME}" >/dev/null
    printf '%s\n' "${mounts}" | docker run --rm -i --pull never -v "${DEPLOY_STATE_VOLUME}:/state" alpine:3.20 \
        sh -ec 'tmp=$(mktemp /state/storage-mappings.XXXXXX); trap "rm -f \"$tmp\"" EXIT; cat > "$tmp"; mv -f "$tmp" /state/storage-mappings' || return 1
    local named
    named="$(sed -n 's|^/storage-pools/||p' <<< "${mounts}")"
    printf '%s' "${named}"
}

storage_mappings() {
    local configured line mounts
    configured="${STORAGE_POOL_MAPPINGS:-}"
    if [[ -n "${configured//[[:space:]]/}" ]]; then
        printf '%s\n' "${configured}"
    else
        mounts="$(previous_storage_mounts)" || return 1
        while IFS= read -r line; do
            if [[ "${line}" == /storage-pools/* ]]; then printf '%s\n' "${line#/storage-pools/}"; fi
        done <<< "${mounts}"
    fi
}

render_storage_override() {
    local mappings="$1" legacy="$2" line name source names="" legacy_enabled=false
    local -A seen=()
    local -a sources=() targets=()
    if [[ -n "${legacy}" ]]; then sources+=("${legacy}"); targets+=(/storage-pools); legacy_enabled=true; fi
    while IFS= read -r line; do
        line="${line%$'\r'}"
        [[ "${line}" =~ ^[[:space:]]*$ ]] && continue
        name="${line%%=*}"
        source="${line#*=}"
        if [[ "${line}" != *=* || ! "${name}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]{0,39}$ || -n "${seen[$name]:-}" ]]; then
            echo "错误：存储映射必须使用唯一别名，每行格式为 别名=/NAS/目录。" >&2
            return 1
        fi
        seen["${name}"]=1
        names+="${names:+,}${name}"
        sources+=("${source}")
        targets+=("/storage-pools/${name}")
    done <<< "${mappings}"

    local index
    for source in "${sources[@]}"; do
        if [[ "${source}" != /* || "${source}" == / || "${source}" == *//* ||
              "${source}" == *'/../'* || "${source}" == */.. || "${source}" == *'/./'* ||
              "${source}" == */. || "${source}" == *$'\r'* || "${source}" == *$'\n'* || "${source}" == *$'\t'* ]]; then
            echo "错误：存储映射必须指向安全的 NAS 绝对目录。" >&2
            return 1
        fi
    done
    printf '%s\n' 'x-pool-env: &pool-env' '  STORAGE_POOL_DIRECTORY: /storage-pools' '  STORAGE_CONTROL_DIRECTORY: /storage-control'
    printf "  STORAGE_POOL_NAMES: '%s'\n  STORAGE_POOL_LEGACY: '%s'\n" "${names}" "${legacy_enabled}"
    printf '%s\n' 'x-pool-volumes: &pool-volumes' '  - storage_control:/storage-control'
    for index in "${!sources[@]}"; do
        source="${sources[index]}"
        source="${source//\$/\$\$}"
        source="${source//\'/\'\'}"
        printf "  - type: bind\n    source: '%s'\n    target: '%s'\n    bind:\n      create_host_path: false\n" "${source}" "${targets[index]}"
    done
    printf '%s\n' 'services:' '  backend:' '    environment: *pool-env' '    volumes: *pool-volumes' \
        '  ai-service:' '    environment: *pool-env' '    volumes: *pool-volumes' 'volumes:' '  storage_control:' '    name: aiphoto-storage-control'
}

legacy_storage_pool() {
    local path mounts
    path="$(effective_value STORAGE_POOL_PATH)"
    if [[ -n "${path}" ]]; then
        if [[ -z "${STORAGE_POOL_PATH:-}" && ( "${path}" == \"*\" || "${path}" == \'*\' ) ]]; then
            path="${path:1:-1}"
        fi
        printf '%s' "${path}"
    else
        mounts="$(previous_storage_mounts)" || return 1
        sed -n 's|^/storage-pools=||p' <<< "${mounts}"
    fi
}

compose() (
    local compose_files=(--file "${COMPOSE_FILE}")
    if gpu_enabled; then
        compose_files+=(--file "${COMPOSE_GPU_FILE}")
    fi
    local mappings legacy override
    mappings="$(storage_mappings)"
    legacy="$(legacy_storage_pool)"
    if [[ -n "${mappings}" || -n "${legacy}" ]]; then
        override="$(mktemp)"
        trap 'rm -f -- "${override}"' EXIT
        render_storage_override "${mappings}" "${legacy}" > "${override}" || exit 1
        compose_files+=(--file "${override}")
    fi

    "${COMPOSE_COMMAND[@]}" \
        --project-name "${COMPOSE_PROJECT_NAME}" \
        --env-file "${ENV_FILE}" \
        "${compose_files[@]}" \
        "$@"
)

container_image() {
    docker inspect --format '{{.Config.Image}}' "$1" 2>/dev/null || true
}

validate_migrated_storage() {
    local active pool mounted_pool image
    if ! docker volume inspect aiphoto-storage-control >/dev/null 2>&1; then
        return 0
    fi
    active="$(docker run --rm --pull never -v aiphoto-storage-control:/storage-control:ro alpine:3.20 \
        sh -c 'for file in /storage-control/*.active; do if [ -f "$file" ]; then cat "$file" || exit 1; printf "\\n"; fi; done')" || return 1
    [[ -n "${active}" ]] || return 0
    pool="$(legacy_storage_pool)" || return 1
    local mappings line name source
    mappings="$(storage_mappings)" || return 1
    mounted_pool="$(storage_mounts)" || mounted_pool=""
    if [[ -z "${mounted_pool}" ]]; then
        mounted_pool="$(saved_storage_mounts)" || return 1
    fi
    if [[ -z "${mounted_pool}" ]]; then
        echo "错误：无法确认已迁移数据使用的存储映射。" >&2
        return 1
    fi
    while IFS='=' read -r name source; do
        if [[ "${name}" == /storage-pools && "${pool%/}" == "${source%/}" ]]; then continue; fi
        line="${name#/storage-pools/}=${source%/}"
        if [[ "${name}" == /storage-pools/* ]] && printf '%s\n' "${mappings}" | sed 's/\r$//;s|/$||' | grep -Fx -- "${line}" >/dev/null; then
            continue
        fi
        echo "错误：已有存储迁移，不能移除或更换现有路径映射：${name}" >&2
        return 1
    done <<< "${mounted_pool}"
    local mapping alias existing active_path
    while IFS= read -r mapping; do
        alias="${mapping%%=*}"
        [[ -n "${alias}" ]] || continue
        existing=false
        while IFS='=' read -r name source; do
            if [[ "${name}" == "/storage-pools/${alias}" ]]; then existing=true; break; fi
        done <<< "${mounted_pool}"
        [[ "${existing}" == true ]] && continue
        while IFS= read -r active_path; do
            if [[ "${active_path}" == "/storage-pools/${alias}" || "${active_path}" == "/storage-pools/${alias}/"* ]]; then
                echo "错误：新挂载别名会遮盖已迁移数据：${alias}" >&2
                return 1
            fi
        done <<< "${active}"
    done <<< "${mappings}"
    for image in "$@"; do
        if [[ "$(docker image inspect --format '{{index .Config.Labels "com.aiphoto.storage-migration"}}' "${image}")" != true ]]; then
            echo "错误：镜像 ${image} 不支持已迁移的存储位置，无法安全回滚。" >&2
            return 1
        fi
    done
}

container_health() {
    docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$1" 2>/dev/null || echo missing
}

record_previous_images() {
    local backend_image frontend_image ai_image
    backend_image="$(container_image aiphoto-backend)"
    frontend_image="$(container_image aiphoto-frontend)"
    ai_image="$(container_image aiphoto-ai)"

    # 首次品牌升级时，从旧容器读取上一版镜像，继续支持自动回滚。
    [[ -n "${backend_image}" ]] || backend_image="$(container_image memoryvault-backend)"
    [[ -n "${frontend_image}" ]] || frontend_image="$(container_image memoryvault-frontend)"
    [[ -n "${ai_image}" ]] || ai_image="$(container_image memoryvault-ai)"

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
    local backup_name postgres_container=aiphoto-postgres postgres_status
    if ! docker inspect "${postgres_container}" >/dev/null 2>&1; then
        postgres_container=memoryvault-postgres
    fi
    if ! docker inspect "${postgres_container}" >/dev/null 2>&1; then
        echo "PostgreSQL 容器尚未运行，跳过首次部署前备份。"
        return 0
    fi

    postgres_status="$(docker inspect --format '{{.State.Status}}' "${postgres_container}" 2>/dev/null || true)"
    if [[ "${postgres_status}" == "created" ]]; then
        echo "PostgreSQL 容器只已创建但尚未启动，跳过首次部署前备份。"
        return 0
    fi
    if [[ "${postgres_status}" != "running" ]]; then
        echo "错误：PostgreSQL 容器当前状态为 ${postgres_status:-unknown}，无法在部署前安全备份。" >&2
        return 1
    fi

    backup_name="aiphoto-$(date '+%Y%m%d-%H%M%S').dump"
    docker volume create "${BACKUP_VOLUME}" >/dev/null
    echo "正在备份 PostgreSQL：${backup_name}"
    docker exec "${postgres_container}" pg_dump -U memoryvault -d memoryvault -Fc |
        docker run --rm -i -v "${BACKUP_VOLUME}:/backups" alpine:3.20 \
            sh -c "cat > '/backups/${backup_name}'"

    docker run --rm -v "${BACKUP_VOLUME}:/backups" alpine:3.20 \
        sh -c "ls -1t /backups/aiphoto-*.dump 2>/dev/null | awk 'NR > ${BACKUP_RETENTION_COUNT}' | while IFS= read -r file; do rm -f \"\$file\"; done"
}

stop_legacy_containers() {
    local container_name
    for container_name in memoryvault-frontend memoryvault-backend memoryvault-ai memoryvault-postgres; do
        if [[ "$(docker inspect --format '{{.State.Running}}' "${container_name}" 2>/dev/null || true)" == true ]]; then
            echo "停止旧名称容器：${container_name}"
            docker stop "${container_name}" >/dev/null
        fi
    done
}

remove_legacy_containers() {
    local container_name
    for container_name in memoryvault-frontend memoryvault-backend memoryvault-ai memoryvault-postgres; do
        if docker inspect "${container_name}" >/dev/null 2>&1; then
            echo "移除已迁移的旧名称容器：${container_name}"
            docker rm "${container_name}" >/dev/null
        fi
    done
}

wait_for_containers() {
    local attempt container_name status all_healthy
    local containers=(
        aiphoto-postgres aiphoto-ai aiphoto-backend aiphoto-frontend
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
        printf 'BACKEND_IMAGE=%q\n' "$(container_image aiphoto-backend)"
        printf 'FRONTEND_IMAGE=%q\n' "$(container_image aiphoto-frontend)"
        printf 'AI_IMAGE=%q\n' "$(container_image aiphoto-ai)"
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
    validate_migrated_storage "${previous_backend}" "${previous_ai}" || return 1
    echo "正在回滚到：${BACKEND_IMAGE}, ${FRONTEND_IMAGE}, ${AI_IMAGE}"
    compose up -d --no-build ai-service backend frontend
    if ! wait_for_containers; then
        compose logs --no-color --tail=200 ai-service backend frontend || true
        return 1
    fi
    echo "已恢复上一版本。"
}

cleanup_release_images() {
    local BACKEND_IMAGE=__missing__ FRONTEND_IMAGE=__missing__ AI_IMAGE=__missing__
    local previous_state container_name current_image in_use repository candidates image_ref label failed=0
    local -a repositories=(
        aiphoto-backend aiphoto-frontend aiphoto-ai aiphoto-postgres aiphoto-backend-test
        memoryvault-backend memoryvault-frontend memoryvault-ai memoryvault-postgres memoryvault-backend-test
    )
    local -a protected_images=()

    if [[ -r "${STATE_FILE}" ]]; then
        # Same trusted state written by record_previous_images and used for rollback.
        # shellcheck disable=SC1090
        source "${STATE_FILE}"
    else
        if ! previous_state="$(docker run --rm --pull never -v "${DEPLOY_STATE_VOLUME}:/state:ro" \
            alpine:3.20 cat /state/previous.env)"; then
            echo "警告：无法读取回滚镜像记录，已跳过镜像清理。" >&2
            return 1
        fi
        source /dev/stdin <<< "${previous_state}"
    fi
    if [[ "${BACKEND_IMAGE}" == __missing__ || "${FRONTEND_IMAGE}" == __missing__ || "${AI_IMAGE}" == __missing__ ]]; then
        echo "警告：回滚镜像记录不完整，已跳过镜像清理。" >&2
        return 1
    fi
    protected_images+=("${BACKEND_IMAGE}" "${FRONTEND_IMAGE}" "${AI_IMAGE}")
    for container_name in aiphoto-backend aiphoto-frontend aiphoto-ai aiphoto-postgres; do
        current_image="$(container_image "${container_name}")"
        if [[ -z "${current_image}" ]]; then
            echo "警告：无法确认 ${container_name} 使用的镜像，已跳过镜像清理。" >&2
            return 1
        fi
        protected_images+=("${current_image}")
    done
    in_use="$(docker container ls --all --format '{{.Image}}')" || return 1
    while IFS= read -r image_ref; do
        [[ -z "${image_ref}" ]] || protected_images+=("${image_ref}")
    done <<< "${in_use}"

    for repository in "${repositories[@]}"; do
        if ! candidates="$(docker image ls --filter "reference=${repository}:*" --format '{{.Repository}}:{{.Tag}}' | sort -u)"; then
            failed=1
            continue
        fi
        while IFS= read -r image_ref; do
            [[ "${image_ref}" == "${repository}:"* && "${image_ref}" != *':<none>' ]] || continue
            if printf '%s\n' "${protected_images[@]}" | grep -Fx -- "${image_ref}" >/dev/null; then
                continue
            fi
            echo "清理旧镜像：${image_ref}"
            if ! docker image rm --no-prune "${image_ref}"; then
                echo "警告：旧镜像清理失败：${image_ref}" >&2
                failed=1
            fi
        done <<< "${candidates}"
    done

    echo "清理 aiphoto 悬空镜像。"
    for image_ref in "${BACKEND_IMAGE}" "${FRONTEND_IMAGE}" "${AI_IMAGE}"; do
        # A rollback reference stored as a bare image ID or digest can itself be untagged.
        if [[ "${image_ref}" == sha256:* || "${image_ref}" == *@* || "${image_ref}" =~ ^[0-9a-f]{12,64}$ ]]; then
            echo "警告：回滚依赖无标签镜像，已跳过悬空镜像清理。" >&2
            return 1
        fi
    done
    for label in com.aiphoto.managed com.memoryvault.managed; do
        if ! docker image prune --force --filter "label=${label}=true"; then
            failed=1
        fi
    done
    return "${failed}"
}

retry_image_build() {
    local attempt=1 max_attempts=3 status log_file
    log_file="$(mktemp)"
    while true; do
        if "$@" 2>&1 | tee "${log_file}"; then
            rm -f "${log_file}"
            return 0
        else
            status=$?
        fi
        # Retry transient registry/transport errors, never application compilation
        # or test failures. Completed layers remain available to the next attempt.
        if ((attempt >= max_attempts)) || ! grep -Eqi \
            'unexpected status from (HEAD|GET) request.*(429|500|502|503|504)|TLS handshake timeout|i/o timeout|connection reset by peer|temporary failure in name resolution' \
            "${log_file}"; then
            rm -f "${log_file}"
            return "${status}"
        fi
        echo "镜像源临时故障，5 秒后重试构建（$((attempt + 1))/${max_attempts}），复用已完成的缓存层。" >&2
        sleep 5
        attempt=$((attempt + 1))
    done
}

deploy_release() {
    local backend_image ai_image app_version
    backend_image="$(effective_value BACKEND_IMAGE)"
    ai_image="$(effective_value AI_IMAGE)"
    app_version="$(effective_value APP_VERSION)"
    validate_migrated_storage "${backend_image:-aiphoto-backend:${app_version}}" \
        "${ai_image:-aiphoto-ai:${app_version}}" || return 1
    record_previous_images
    backup_database
    stop_legacy_containers
    if gpu_enabled; then
        echo "AI 服务将使用 NVIDIA GPU 模式。"
    else
        echo "AI 服务将使用 CPU 模式。"
    fi
    echo "正在更新 aiphoto 服务。"
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
    remove_legacy_containers
    echo "aiphoto 新版本部署成功。"
}

validate_configuration

case "${ACTION}" in
    validate)
        compose config --quiet
        echo "Docker Compose 配置校验通过。"
        ;;
    test)
        backend_maven_base_image="$(effective_value BACKEND_MAVEN_BASE_IMAGE)"
        backend_maven_base_image="${backend_maven_base_image:-docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21}"
        retry_image_build docker build --file "${PROJECT_DIR}/backend/Dockerfile" --target test \
            --build-arg "BACKEND_MAVEN_BASE_IMAGE=${backend_maven_base_image}" \
            --tag "aiphoto-backend-test:${RELEASE_TAG:-local}" "${PROJECT_DIR}/backend"
        ;;
    build)
        retry_image_build compose build postgres backend frontend ai-service
        ;;
    deploy)
        deploy_release
        ;;
    health)
        wait_for_containers
        ;;
    cleanup)
        cleanup_release_images
        ;;
    save-storage-mappings)
        persist_storage_mounts
        ;;
    rollback)
        rollback_from_state
        ;;
    logs)
        compose logs --no-color --tail=200
        ;;
    *)
        echo "用法：$0 {validate|test|build|deploy|health|cleanup|save-storage-mappings|rollback|logs} [env-file] [state-file]" >&2
        exit 2
        ;;
esac
