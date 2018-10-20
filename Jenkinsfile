pipeline {
  agent {
    dockerfile {
      dir 'ci/builder'
    }
  }

  stages {
    stage('Build') {
      steps {
        gradlew 'assemble'
      }
    }

    stage('Test') {
      steps {
        gradlew 'check'

        junit 'app/build/test-results/**/*.xml'
      }
    }
  }

  post {
    always {
      jacoco(
        execPattern: 'app/build/jacoco/*.exec',
        classPattern: 'app/build/intermediates/classes/google/release',
        sourcePattern: 'app/src/main/java/org/connectbot'
      )

      dir('app/build') {
        archiveArtifacts artifacts: 'outputs/apk/**/*.apk', fingerprint: true
        archiveArtifacts 'outputs/apk/**/output.json'

        archiveArtifacts 'jacoco/*.exec'

        archiveArtifacts 'test-results/**/*.xml'

        checkstyle pattern: 'reports/checkstyle/*.xml'
        archiveArtifacts 'reports/checkstyle/*.html'
        archiveArtifacts 'reports/checkstyle/*.xml'

        androidLint pattern: 'reports/lint-results*.xml'
        archiveArtifacts 'reports/lint-results*.xml'

        archiveArtifacts 'reports/tests/**/*'
      }
    }
  }
}

def gradlew(command) {
  if (isUnix()) {
    sh "./gradlew ${command} --stacktrace --no-daemon"
  } else {
    bat "./gradlew.bat ${command} --stacktrace --no-daemon"
  }
}
