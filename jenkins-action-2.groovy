pipeline {
  agent any
  environment {
    CONTAINER_IMAGE = "registry.ocp4.pacosta.com:8443/${params.ENVIRONMENT}/actionpoint:1.101"
    REGISTRY_CREDENTIALS = '23b86796-edc9-44c2-a231-a0247349b0bc'
  }
  parameters {
    choice choices: ['test', 'dev', 'prod', 'rti', 'admin-dashboard'], description: 'CHOOSE YOUR ENVIRONMENT', name: 'ENVIRONMENT'
    choice choices: ['test.yaml', 'dev.yaml', 'prod.yaml'], description: 'CHOOSE YOUR DEPLOY', name: 'DEPLOY'  
  }
  
  stages {
    stage('Checkout') {
      steps {
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[url: 'http://costacloud:redhat12345@gitlab1-default.apps.ocp4.pacosta.com/costacloud/action-point-2.git']]])
      }
    }
    /*
    stage('SonarQube Analysis') {
      environment {
        SONARQUBE_PATH = "/var/jenkins_home/sonar-scanner-4.8.0.2856-linux:${env.PATH}"
        SONARQUBE_HOST_URL = "http://sonarqube-default.apps.ocp4.pacosta.com"
        SONARQUBE_TOKEN = credentials('SonarQubeToken')
        scannerHome = tool name: 'SonarScanner'
        SONARQUBE_PROJECT_KEY = "sqp_77b8130e53f61bd9aeb234a6c0346224af2a87a8"
      }
      steps {
        script {
          withSonarQubeEnv('sonar') {
            sh "${scannerHome}/bin/sonar-scanner -Dsonar.java.binaries=. -Dsonar.host.url=${SONARQUBE_HOST_URL} -Dsonar.login=${SONARQUBE_TOKEN} -Dsonar.projectKey=${SONARQUBE_PROJECT_KEY} -Dsonar.sources=. -Dsonar.projectName=${JOB_NAME}"
          }
        }
      }
      */
     stage('Compile') {
       steps {
         sh 'mvn -Dmaven.repo.local=/var/jenkins_home/.m2/repository -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true compile'
       }
     }
     stage('Build Jar') {
       steps {
         sh 'mvn package -DskipTests'
       }
     }
     stage('Build Image') {
       steps {
         script {
           sh "sudo podman build -t ${CONTAINER_IMAGE} ."
         }
       }
     }
     stage('Push Image') {
       steps {
         script {
           withCredentials([usernamePassword(credentialsId: "${REGISTRY_CREDENTIALS}", usernameVariable: 'openshift', passwordVariable: 'redhat123')]) {
             sh "sudo podman login -u openshift -p redhat123 --tls-verify=false https://registry.ocp4.pacosta.com:8443"
             sh "sudo podman push --tls-verify=false $CONTAINER_IMAGE"
           }
         }
       }
     }
     stage('Deploy') {
       environment {
         OCP_SERVER = 'https://api.ocp4.pacosta.com:6443'
         OCP_PROJECT = "${params.ENVIRONMENT}"
         OCP_APP_NAME = 'action-point-2'
         OCP_USERNAME = 'admin'
         OCP_PASSWORD = 'password'
         PATH_EXTRA = "/bin"
         PATH = "${PATH}:${PATH_EXTRA}"
       }
       steps {
         script {
           def ocp_login = sh(script: "oc login --insecure-skip-tls-verify -u ${env.OCP_USERNAME} -p ${env.OCP_PASSWORD} ${env.OCP_SERVER}", returnStdout: true)
           echo ocp_login

           def image_pull = sh(script: "sudo podman pull --tls-verify=false ${CONTAINER_IMAGE}", returnStdout: true)
           echo image_pull

           def ocp_deploy = sh(script: "oc apply -f ${params.DEPLOY} -n ${env.OCP_PROJECT}", returnStdout: true)
           echo ocp_deploy
         }
       }
     }
  }
}
