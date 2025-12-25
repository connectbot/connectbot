[![Build Status](https://github.com/connectbot/connectbot/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/connectbot/connectbot/actions/workflows/ci.yml)

# ConnectBot

ConnectBot is a [Secure Shell](https://en.wikipedia.org/wiki/Secure_Shell)
client for Android that lets you connect to remote servers over a
cryptographically secure link.


## How to Install

### Google Play

[![Get it on Google Play][2]][1]

  [1]: https://play.google.com/store/apps/details?id=org.connectbot
  [2]: https://developer.android.com/images/brand/en_generic_rgb_wo_60.png

The easiest way to get ConnectBot is to [install from Google Play Store][1].
If you have installed from a downloaded APK, Google Play Store can upgrade
your installed version to the latest version. However, once it has upgraded
*you can't install a version from the releases on GitHub anymore.*


### Download a release

ConnectBot can be downloaded from [releases](
https://github.com/connectbot/connectbot/releases) on GitHub. There are
two versions:

-  "`google`" &mdash; for a version that uses Google Play Services
to handle upgrading the cryptography provider
-  "`oss`" &mdash; includes the cryptography provider in the APK which
   increases its size by a few megabytes.
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

### E2E Testing

ConnectBot includes end-to-end tests that verify SSH connection functionality
against a real SSH server. These tests run on an Android emulator and connect
to an SSH server running in Docker.

#### Requirements

- **Docker**: Required to run the SSH test server container
- **KVM**: Required for Android emulator hardware acceleration (Linux only)

On Linux, ensure your user has access to KVM:

```sh
# Add your user to the kvm group
sudo gpasswd -a $USER kvm

# Log out and log back in, then verify
ls -la /dev/kvm
```

#### Running E2E Tests

```sh
# Run full E2E test suite (manages emulator and Docker automatically)
./gradlew e2eTest

# Run with existing emulator/device (only manages Docker)
./gradlew e2eTestNoEmulator

# Clean up E2E artifacts (emulator, containers, AVD)
./gradlew e2eClean
```

The `e2eTest` task will:
1. Create an Android Virtual Device (AVD) if needed
2. Start the Android emulator with KVM acceleration
3. Build and start the Docker SSH server container
4. Build and install the app and test APKs
5. Run the instrumented E2E tests
6. Stop the emulator and Docker containers

### Continuous Integration

ConnectBot uses [GitHub Actions](https://github.com/connectbot/connectbot/actions)
for continuous integration. The workflow is defined in
`.github/workflows/ci.yml`.

#### Running Workflows Locally with act

In general, simply running `./gradlew build` should cover all the
checks run in the GitHub Actions continuous integration workflow, but you can
run GitHub Actions workflows locally using [`nektos/act`](https://github.com/nektos/act).
This requires Docker to be installed and running.

To run the main CI workflow (`ci.yml`):

```sh
act -W .github/workflows/ci.yml
```


## Translations

If you'd like to correct or contribute new translations to ConnectBot,
then head on over to [ConnectBot's translations project](
https://translations.launchpad.net/connectbot/trunk/+pots/fortune)
