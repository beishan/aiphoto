pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 120, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        string(name: 'NAS_HOST', defaultValue: '192.168.31.155', description: '飞牛 NAS 局域网 IP 或域名')
        string(name: 'PHOTO_LIBRARY_PATH', defaultValue: '/vol2/1000/aiphotoslib', description: 'NAS 宿主机照片库绝对路径（必须已存在）')
        string(name: 'AI_MODELS_PATH', defaultValue: '/vol1/1000/aiphotos/models', description: 'NAS 宿主机 AI 模型根目录绝对路径（必须已存在）')
        string(name: 'FRONTEND_PORT', defaultValue: '8391', description: '前端对外端口')
        string(name: 'BACKEND_PORT', defaultValue: '8392', description: '后端对外端口')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '紧急部署时跳过后端测试')
    }

    environment {
        APP_NAME = 'memoryvault'
        COMPOSE_PROJECT_NAME = 'memoryvault'
        PRODUCTION_ENV_CREDENTIAL_ID = 'memoryvault-production-env'
        PREVIOUS_IMAGES_FILE = '.memoryvault-previous-images'
        IMAGE_RETENTION_COUNT = '5'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'git@github.com:beishan/aiphoto.git'
                script {
                    def shortCommit = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
                    def subject = sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim().replaceAll(/\s+/, ' ')
                    def title = subject.length() > 48 ? "${subject.take(48)}…" : subject
                    env.RELEASE_TAG = "${env.BUILD_NUMBER}-${shortCommit}"
                    env.BACKEND_IMAGE = "memoryvault-backend:${env.RELEASE_TAG}"
                    env.FRONTEND_IMAGE = "memoryvault-frontend:${env.RELEASE_TAG}"
                    env.AI_IMAGE = "memoryvault-ai:${env.RELEASE_TAG}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${title ?: shortCommit}"
                    currentBuild.description = "提交 ${shortCommit}"
                }
            }
        }

        stage('Validate') {
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'MEMORYVAULT_ENV_FILE')]) {
                    sh './scripts/deploy.sh validate "$MEMORYVAULT_ENV_FILE"'
                }
            }
        }

        stage('Backend Test') {
            when { expression { !params.SKIP_TESTS } }
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'MEMORYVAULT_ENV_FILE')]) {
                    sh './scripts/deploy.sh test "$MEMORYVAULT_ENV_FILE"'
                }
            }
            post {
                always {
                    sh 'docker image rm "memoryvault-backend-test:${RELEASE_TAG}" >/dev/null 2>&1 || true'
                }
            }
        }

        stage('Build Images') {
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'MEMORYVAULT_ENV_FILE')]) {
                    sh './scripts/deploy.sh build "$MEMORYVAULT_ENV_FILE"'
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'MEMORYVAULT_ENV_FILE')]) {
                    sh './scripts/deploy.sh deploy "$MEMORYVAULT_ENV_FILE" "$PREVIOUS_IMAGES_FILE"'
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
            withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'MEMORYVAULT_ENV_FILE')]) {
                sh './scripts/deploy.sh cleanup "$MEMORYVAULT_ENV_FILE" || true'
            }
            echo "MemoryVault ${env.RELEASE_TAG} 构建和部署成功。"
        }
        failure {
            script {
                if (fileExists(env.PREVIOUS_IMAGES_FILE)) {
                    withCredentials([file(credentialsId: env.PRODUCTION_ENV_CREDENTIAL_ID, variable: 'MEMORYVAULT_ENV_FILE')]) {
                        sh './scripts/rollback.sh "$MEMORYVAULT_ENV_FILE" "$PREVIOUS_IMAGES_FILE" || true'
                    }
                }
            }
            echo 'MemoryVault 构建或部署失败，已尝试自动回滚。'
        }
        always { cleanWs() }
    }
}
