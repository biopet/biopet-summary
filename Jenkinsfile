node('local') {
    stage('Build') {
        sh "${tool name: 'sbt 0.13.15', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/sbt compile test"
    }
    stage('Results') {
        junit '**/target/test-reports/*.xml'
    }
}