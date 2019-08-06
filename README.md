[![Build Status](https://travis-ci.com/connectbot/connectbot.svg?branch=master)](
https://travis-ci.com/connectbot/connectbot)

# ConnectBot

ConnectBot is a [Secure Shell](https://en.wikipedia.org/wiki/Secure_Shell)
client for Android that lets you connect to remote servers over a
cryptographically secure link.


## Google Play

[![Get it on Google Play][2]][1]

  [1]: https://play.google.com/store/apps/details?id=org.connectbot
  [2]: https://developer.android.com/images/brand/en_generic_rgb_wo_60.png


## Compiling

### Android Studio

ConnectBot is most easily developed in [Android Studio](
https://developer.android.com/studio/). You can import this project
directly from its project creation screen by importing from the GitHub URL.

### Command line

To compile ConnectBot using `gradlew`, you must first specify where your
Android SDK is via the `ANDROID_SDK_HOME` environment variable. Then
you can invoke the Gradle wrapper to build:

```sh
./gradlew build
```

### Reproducing Continuous Integration (CI) builds locally

To run the Jenkins CI pipeline locally, you can use
`jenkinsfile-runner` via a Docker installation which can be invoked like
this:

```sh
docker run -it -v $(pwd):/workspace \
    -v jenkinsfile-runner-cache:/var/jenkinsfile-runner-cache \
    -v jenkinsfile-runner:/var/jenkinsfile-runner \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v $(which docker):$(which docker) \
    -e ANDROID_ADB_SERVER_ADDRESS=host.docker.internal \
    jenkins/jenkinsfile-runner
```


## Translations

If you'd like to correct or contribute new translations to ConnectBot,
then head on over to [ConnectBot's translations project](
https://translations.launchpad.net/connectbot/trunk/+pots/fortune)
