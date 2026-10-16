// One initial job. Additional projects can be added in the UI without rebuilding Jenkins.
def jobName = System.getenv('SEED_JOB_NAME')
def repoUrl = System.getenv('SEED_REPO_URL')
def repoBranch = System.getenv('SEED_REPO_BRANCH')
def gitCredentials = System.getenv('SEED_CREDENTIALS_ID')
def pipelinePath = System.getenv('SEED_SCRIPT_PATH')

pipelineJob(jobName) {
    description('Managed by infra/jenkins/jobs.groovy. Pipeline is loaded from Git.')
    parameters {
        booleanParam('DEPLOY_STAGING', false, 'Deploy to the separate local staging stack after CI passes')
    }
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url(repoUrl)
                        if (gitCredentials) { credentials(gitCredentials) }
                    }
                    branch(repoBranch)
                }
            }
            scriptPath(pipelinePath)
            lightweight(true)
        }
    }
}
