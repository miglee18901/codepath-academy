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
                sh 'docker build --target runtime -t codepath-academy-backend:$IMAGE_TAG .'
                writeFile file: 'images.env', text: "IMAGE_TAG=${env.IMAGE_TAG}\n"
                archiveArtifacts artifacts: 'images.env,reports/backend-html/**', fingerprint: true
            }
        }
        stage('Deploy staging') {
            when { expression { params.DEPLOY_STAGING } }
            steps {
                sh '''
                    docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml config --quiet
                    docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml up -d --no-build --wait --wait-timeout 240
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
