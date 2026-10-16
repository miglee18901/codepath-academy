pipeline {
    agent any
    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timestamps()
    }
    parameters {
        booleanParam(name: 'DEPLOY_STAGING', defaultValue: false,
            description: 'Deploy verified images to the separate local staging stack')
        booleanParam(name: 'ENABLE_OBSERVABILITY', defaultValue: false,
            description: 'Connect staging to the already running shared observability stack')
    }
    triggers {
        githubPush()
        pollSCM('H/5 * * * *')
    }
    environment {
        COMPOSE_PROJECT_NAME = 'codepath-academy-staging'
        COMPOSE_DISABLE_ENV_FILE = '1'
    }
    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim() + "-${env.BUILD_NUMBER}"
                    env.TEST_CONTAINER = "codepath-tests-${env.IMAGE_TAG}"
                }
                sh 'docker version && docker compose version'
            }
        }
        stage('Backend tests') {
            steps {
                sh 'docker build --target test -t codepath-academy-tests:$IMAGE_TAG .'
                sh '''
                    docker create --name "$TEST_CONTAINER" "codepath-academy-tests:$IMAGE_TAG"
                    mkdir -p reports
                    docker cp "$TEST_CONTAINER:/app/build/test-results/test" reports/backend
                    docker cp "$TEST_CONTAINER:/app/build/reports/tests/test" reports/backend-html
                '''
            }
            post {
                always {
                    junit testResults: 'reports/backend/*.xml', allowEmptyResults: true
                    sh 'docker rm -f "$TEST_CONTAINER" >/dev/null 2>&1 || true'
                }
            }
        }
        stage('Frontend lint and build') {
            steps {
                sh 'docker build --target verify -t codepath-academy-frontend:$IMAGE_TAG frontend'
            }
        }
        stage('Package backend') {
            steps {
                sh 'docker build --target runtime --build-arg APP_VERSION=$IMAGE_TAG -t codepath-academy-backend:$IMAGE_TAG .'
                writeFile file: 'images.env', text: "IMAGE_TAG=${env.IMAGE_TAG}\n"
                archiveArtifacts artifacts: 'images.env,reports/backend-html/**', fingerprint: true
            }
        }
        stage('Deploy staging') {
            when { expression { params.DEPLOY_STAGING } }
            steps {
                script {
                    env.OBSERVABILITY_COMPOSE = params.ENABLE_OBSERVABILITY ? '-f compose.observability.yml' : ''
                }
                sh '''
                    export DEPLOYMENT_ENVIRONMENT=staging APP_VERSION="$IMAGE_TAG" OTEL_SAMPLE_RATIO=0.2
                    docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml $OBSERVABILITY_COMPOSE config --quiet
                    docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml $OBSERVABILITY_COMPOSE up -d --no-build --wait --wait-timeout 240
                    docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml $OBSERVABILITY_COMPOSE exec -T backend wget -q -O /dev/null http://127.0.0.1:9091/actuator/health
                '''
            }
        }
    }
    post {
        unsuccessful {
            script {
                if (params.DEPLOY_STAGING) {
                    sh 'docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml ps || true'
                }
            }
        }
        cleanup {
            sh 'docker image rm "codepath-academy-tests:$IMAGE_TAG" >/dev/null 2>&1 || true'
        }
    }
}
