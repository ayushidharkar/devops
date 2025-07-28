pipeline {
    agent {
        label 'Ayushi-Node' 
    }

    tools {
        jdk 'Ayushi-java'
        maven 'Ayushi-maven'
    }

    environment {
        SONARQUBE_ENV = 'Ayushi-Sonarqube'
        JIRA_SITE = 'Ayushi-Jira'
        SLACK_CHANNEL = '#all-jenkins'
    }

    triggers {
        githubPush()
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'master', url: 'https://github.com/ayushidharkar/Maven-Java-Project.git', credentialsId: 'ayushidharkar-PAT'

            }
        }

        stage('Build') {
            steps {
                bat 'mvn clean install -DskipTests'
            }
        }
        
        stage('Archive Artifacts') {
            steps {
                echo '📦 Archiving the built artifacts...'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Test') {
            steps {
                bat 'mvn test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Code Coverage') {
            steps {
                bat 'mvn jacoco:report'
            }
            post {
                always {
                    publishHTML(target: [
                        reportName: 'JaCoCo Code Coverage',
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true
                    ])
                }
            }
        }

       
        stage('SonarQube Analysis') {

            steps {
                withSonarQubeEnv('Ayushi-Sonarqube'){
                withCredentials([string(credentialsId: 'ayushisonar', variable: 'ayushi_Sonar')]) {
                bat 'mvn sonar:sonar -Dsonar.projectKey=Jenkins -Dsonar.projectName="Jenkins" -Dsonar.login=%ayushi_Sonar%'
              }
            }
            }
        }
 

        // stage('Quality Gate') {
//     steps {
//         timeout(time: 10, unit: 'MINUTES') {
//             waitForQualityGate abortPipeline: true
//         }
//     }
// }

    stage('Update Jira') {
            steps {
                script {
                    def issueKey = 'CPG-7'
                    echo "📌 Posting build result to Jira issue: ${issueKey}"
                    jiraAddComment idOrKey: issueKey, comment: "✅ Jenkins build [#${env.BUILD_NUMBER}] succeeded: ${env.BUILD_URL}"
                }
            }
        }
}

   post {
        success {
            echo '✅ Pipeline completed successfully!'
            slackSend(
                channel: "${env.SLACK_CHANNEL}",
                message: "✅ *Build SUCCESSFUL*\n*Job:* ${env.JOB_NAME}\n*Build:* #${env.BUILD_NUMBER}\n🔗 ${env.BUILD_URL}",
                tokenCredentialId: 'ayushi-slack'
            )
        }
        failure {
            echo '❌ Pipeline failed. Please check build/test/quality logs.'
            slackSend(
                channel: "${env.SLACK_CHANNEL}",
                message: "❌ *Build FAILED*\n*Job:* ${env.JOB_NAME}\n*Build:* #${env.BUILD_NUMBER}\n🔗 ${env.BUILD_URL}",
                tokenCredentialId: 'ayushi-slack'
            )
        }
    }
}