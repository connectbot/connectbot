pipeline {
  agent {
    dockerfile {
      dir 'ci/builder'
      args "${env.JAVA_OPTS ? "-e JAVA_OPTS=\"$env.JAVA_OPTS\"" : ''} " +
           "${env.GRADLE_BUILD_CACHE ? " -e GRADLE_BUILD_CACHE=\"$env.GRADLE_BUILD_CACHE\"" : ''} " +
           "${env.MAVEN_REPO_CACHE ? " -e MAVEN_REPO_CACHE=\"$env.MAVEN_REPO_CACHE\"" : ''} " +
           "${env.DOCKER_NETWORK ? " --network \"$env.DOCKER_NETWORK\"" : ''}"
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
        gradlew 'check jacocoTestReport'

        junit 'app/build/test-results/**/*.xml'
      }
    }

    stage('Device test') {
      when { expression { env.ANDROID_ADB_SERVER_ADDRESS != null } }
      steps {
        script {
          sh "ANDROID_ADB_SERVER_ADDRESS=${env.ANDROID_ADB_SERVER_ADDRESS ?: "host.docker.internal"} " +
             "ANDROID_ADB_SERVER_PORT=${env.ANDROID_ADB_SERVER_PORT ?: "5037"} " +
             "./gradlew connectedCheck --stacktrace --no-daemon"
        }
      }
    }
  }

  post {
    always {
      jacoco(
        execPattern: 'app/build/jacoco/*.exec, app/build/outputs/code-coverage/connected/**/*.ec',
        sourcePattern: 'app/src/*/java',
        classPattern: 'app/build/intermediates/javac/**/classes',
        inclusionPattern: 'org/connectbot/**/*.class',
        exclusionPattern: '**/R$*.class, **/*$ViewInjector*.*, **/BuildConfig.*, **/Manifest*.*'
      )

      publishCoverage adapters: [
          jacocoAdapter('app/build/reports/jacoco/jacocoTestGoogleDebugUnitTestReport/jacocoTestGoogleDebugUnitTestReport.xml'),
          jacocoAdapter('app/build/reports/jacoco/jacocoTestGoogleReleaseUnitTestReport/jacocoTestGoogleReleaseUnitTestReport.xml'),
          jacocoAdapter('app/build/reports/jacoco/jacocoTestOssDebugUnitTestReport/jacocoTestOssDebugUnitTestReport.xml'),
          jacocoAdapter('app/build/reports/jacoco/jacocoTestOssReleaseUnitTestReport/jacocoTestOssReleaseUnitTestReport.xml')
      ]

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
