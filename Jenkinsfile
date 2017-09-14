node('local') {
    stage('Init') {
        tool 'JDK 8u102'
        tool 'sbt 0.13.15'
        tool 'Apache Maven 3.3.9'
    }

    timeout(45) {
        stage('Checkout') {
            checkout scm
            //sh 'git submodule update --init --recursive'
        }
    }

    stage('Build') {
        sh "${tool name: 'sbt 0.13.15', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/sbt compile test"
    }

    stage('Results') {
        junit '**/target/test-reports/*.xml'
    }
}