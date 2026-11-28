def configureReleaseParameters(String storageMappings) {
    properties([parameters([
        string(name: 'NAS_HOST', defaultValue: '192.168.31.155', description: '飞牛 NAS 局域网 IP 或域名'),
        string(name: 'PHOTO_LIBRARY_PATH', defaultValue: '/vol2/1000/aiphotoslib', description: 'NAS 宿主机照片库绝对路径（必须已存在）'),
        string(name: 'AI_MODELS_PATH', defaultValue: '/vol1/1000/aiphotos/models', description: 'NAS 宿主机 AI 模型根目录绝对路径（必须已存在）'),
        string(name: 'STORAGE_DATA_PATH', defaultValue: '/vol1/1000/aiphotos/storage', description: 'NAS 宿主机上传照片和缩略图存储目录（必须已存在）'),
        text(name: 'STORAGE_POOL_MAPPINGS', defaultValue: storageMappings, description: '每行填写 别名=/NAS/绝对路径。发版成功后，下次自动填入。'),
        string(name: 'FRONTEND_PORT', defaultValue: '8391', description: '前端对外端口'),
        string(name: 'BACKEND_PORT', defaultValue: '8392', description: '后端对外端口'),
        booleanParam(name: 'ENABLE_GPU', defaultValue: false, description: '已安装 NVIDIA Container Toolkit 时启用 GPU 加速'),
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '紧急部署时跳过后端测试'),
    ])])
}

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        quietPeriod(0)
        timestamps()
        skipDefaultCheckout()
        timeout(time: 120, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    // Keep these defaults in sync with configureReleaseParameters.
    parameters {
        string(name: 'NAS_HOST', defaultValue: '192.168.31.155', description: '飞牛 NAS 局域网 IP 或域名')
        string(name: 'PHOTO_LIBRARY_PATH', defaultValue: '/vol2/1000/aiphotoslib', description: 'NAS 宿主机照片库绝对路径（必须已存在）')
        string(name: 'AI_MODELS_PATH', defaultValue: '/vol1/1000/aiphotos/models', description: 'NAS 宿主机 AI 模型根目录绝对路径（必须已存在）')
        string(name: 'STORAGE_DATA_PATH', defaultValue: '/vol1/1000/aiphotos/storage', description: 'NAS 宿主机上传照片和缩略图存储目录（必须已存在）')
        string(name: 'FRONTEND_PORT', defaultValue: '8391', description: '前端对外端口')
        string(name: 'BACKEND_PORT', defaultValue: '8392', description: '后端对外端口')
        booleanParam(name: 'ENABLE_GPU', defaultValue: false, description: '已安装 NVIDIA Container Toolkit 时启用 GPU 加速')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '紧急部署时跳过后端测试')
    }

    environment {
        APP_NAME = 'aiphoto'
        COMPOSE_PROJECT_NAME = 'aiphoto'
        // Jenkins 外部凭据沿用原 ID，避免品牌升级要求人工迁移密钥。
        PRODUCTION_ENV_CREDENTIAL_ID = 'memoryvault-production-env'
        PREVIOUS_IMAGES_FILE = '.aiphoto-previous-images'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '执行节点已就绪，正在初始化发版参数。'
                script {
                    // Register the storage field before checkout. It stays outside the declarative
                    // parameters block so successful releases can persist its default.
                    if (!params.containsKey('STORAGE_POOL_MAPPINGS')) {
                        configureReleaseParameters('')
                    }
                }
                echo '正在从 GitHub 拉取 main 分支。'
                git branch: 'main', url: 'git@github.com:beishan/aiphoto.git'
                script {
                    // A new job's first run can be queued before parameter defaults exist.
                    if (env.NAS_HOST == null) env.NAS_HOST = params.NAS_HOST
                    if (env.PHOTO_LIBRARY_PATH == null) env.PHOTO_LIBRARY_PATH = params.PHOTO_LIBRARY_PATH
                    if (env.AI_MODELS_PATH == null) env.AI_MODELS_PATH = params.AI_MODELS_PATH
                    if (env.STORAGE_DATA_PATH == null) env.STORAGE_DATA_PATH = params.STORAGE_DATA_PATH
                    if (env.FRONTEND_PORT == null) env.FRONTEND_PORT = params.FRONTEND_PORT
                    if (env.BACKEND_PORT == null) env.BACKEND_PORT = params.BACKEND_PORT
                    if (env.ENABLE_GPU == null) env.ENABLE_GPU = params.ENABLE_GPU ? 'true' : 'false'
                    def appVersion = readFile('VERSION').trim()
                    if (!(appVersion ==~ /\d+\.\d+\.\d+/)) {
                        error("VERSION 必须使用语义化版本号（例如 0.2.2），当前值：${appVersion}")
                    }
                    def shortCommit = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
                    def subject = sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim().replaceAll(/\s+/, ' ')
                    def title = subject.length() > 48 ? "${subject.take(48)}…" : subject
                    env.APP_VERSION = appVersion
                    env.RELEASE_TAG = "${appVersion}-${env.BUILD_NUMBER}-${shortCommit}"
                    env.BACKEND_IMAGE = "aiphoto-backend:${env.RELEASE_TAG}"
                    env.FRONTEND_IMAGE = "aiphoto-frontend:${env.RELEASE_TAG}"
                    env.AI_IMAGE = "aiphoto-ai:${env.RELEASE_TAG}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} v${appVersion} ${title ?: shortCommit}"
                    currentBuild.description = "版本 ${appVersion} · 提交 ${shortCommit}"
                }
            }
        }

        stage('Validate') {
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'AIPHOTO_ENV_FILE')]) {
                    sh './scripts/deploy.sh validate "$AIPHOTO_ENV_FILE"'
                }
            }
        }

        stage('Backend Test') {
            when { expression { !params.SKIP_TESTS } }
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'AIPHOTO_ENV_FILE')]) {
                    sh './scripts/deploy.sh test "$AIPHOTO_ENV_FILE"'
                }
            }
            post {
                always {
                    sh 'docker image rm "aiphoto-backend-test:${RELEASE_TAG}" >/dev/null 2>&1 || true'
                }
            }
        }

        stage('Build Images') {
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'AIPHOTO_ENV_FILE')]) {
                    sh './scripts/deploy.sh build "$AIPHOTO_ENV_FILE"'
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'AIPHOTO_ENV_FILE')]) {
                    sh './scripts/deploy.sh deploy "$AIPHOTO_ENV_FILE" "$PREVIOUS_IMAGES_FILE"'
                }
            }
        }

        stage('LAN Health Check') {
            steps {
                sh '''
                    set -eu
                    check_url() {
                        name="$1"
                        url="$2"
                        attempt=1
                        while [ "$attempt" -le 24 ]; do
                            status="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 "$url" || true)"
                            if [ "$status" = "200" ]; then
                                echo "${name} health check passed: ${url}"
                                return 0
                            fi
                            echo "${name} returned ${status:-curl-error}; retry ${attempt}/24"
                            sleep 5
                            attempt=$((attempt + 1))
                        done
                        return 1
                    }
                    check_url backend "http://${NAS_HOST}:${BACKEND_PORT}/actuator/health"
                    check_url frontend "http://${NAS_HOST}:${FRONTEND_PORT}/health"
                '''
            }
        }
    }

    post {
        success {
            withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'AIPHOTO_ENV_FILE')]) {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE', catchInterruptions: false,
                            message: '部署已成功，但存储映射或 Jenkins 参数默认值保存失败。') {
                        def mappings = sh(script: './scripts/deploy.sh save-storage-mappings "$AIPHOTO_ENV_FILE"', returnStdout: true)
                        configureReleaseParameters(mappings)
                    }
                    if (sh(script: './scripts/deploy.sh cleanup "$AIPHOTO_ENV_FILE" "$PREVIOUS_IMAGES_FILE"', returnStatus: true) != 0) {
                        unstable('部署已成功，但历史镜像清理未完成，请查看 Jenkins 清理日志。')
                    }
                }
            }
            echo "aiphoto ${env.RELEASE_TAG} 构建和部署成功。"
        }
        failure {
            script {
                if (fileExists(env.PREVIOUS_IMAGES_FILE)) {
                    withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'AIPHOTO_ENV_FILE')]) {
                        sh './scripts/rollback.sh "$AIPHOTO_ENV_FILE" "$PREVIOUS_IMAGES_FILE" || true'
                    }
                }
            }
            echo 'aiphoto 构建或部署失败，已尝试自动回滚。'
        }
        // Declarative Pipeline 的 always 会先于 success/failure 执行；
        // cleanup 固定最后执行，避免提前删除部署清理和回滚所需的脚本与状态文件。
        cleanup { cleanWs() }
    }
}
