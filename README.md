[![Build Status](https://travis-ci.org/connectbot/connectbot.svg?branch=master)](
https://travis-ci.org/connectbot/connectbot)

Google Play
----------------

[![Get it on Google Play][2]][1]

  [1]: https://play.google.com/store/apps/details?id=org.connectbot
  [2]: https://developer.android.com/images/brand/en_generic_rgb_wo_60.png

Translations
----------------

If you'd like to see ConnectBot translated into your language and you're
willing to help, then head on over to
https://translations.launchpad.net/connectbot/trunk/+pots/fortune


Compiling
----------------

To compile ConnectBot using gradlew, you must first specify where your
Android SDK is via the ANDROID_SDK_HOME environment variable.

To run the Jenkins CI pipeline locally, you can use
`jenkinsfile-runner` which can be invoked like this:

```sh
docker run -it -v $(pwd):/workspace \
    -v jenkinsfile-runner-cache:/var/jenkinsfile-runner-cache \
    -v jenkinsfile-runner:/var/jenkinsfile-runner \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v $(which docker):$(which docker) \
    jenkins/jenkinsfile-runner
```
