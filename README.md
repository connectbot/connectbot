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
*you can't install a version from the releases on GitHub anymore* (due to
key rotation that upgrades the package signature to a more secure algorithm).


### Download a release

ConnectBot can be downloaded from [releases](
https://github.com/connectbot/connectbot/releases) on GitHub. There are
two versions:

-  `google` &mdash; for a version that uses Google Play Services
   to handle upgrading the cryptography provider
-  `oss` &mdash; includes the cryptography provider in the APK which
   increases its size by a few megabytes.

## Architecture

### Major dependencies

The ConnectBot app that uses two other libraries to provide its functionality:
* [ConnectBot Terminal](https://github.com/connectbot/termlib) &mdash; the
  terminal emulator used by the app is also created and maintained by the
  ConnectBot author, Kenny Root.
* [ConnectBot fork of Trilead SSH-2](https://github.com/connectbot/sshlib)
  &mdash; a heavily modified fork of the original Trilead SSH-2 Java library
  written by Christian Plattner.

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

## Parallel release lines

`main` develops 1.12 and publishes to Play alpha. `branch/1.11` receives
maintenance fixes; exact `v1.11.x` tags publish to Play beta for testing before
manual promotion to production in Play Console. Branch pushes on maintenance
are not published. Cherry-pick applicable fixes forward without replacing the
version tag filter on either branch.

Stable Android codes remain `major * 10000000 + minor * 100000 + patch * 1000`
plus commits since the version tag. Patch numbers are limited to 0–49 and the
commit distance to 0–999. The fixed, annotated `v1.12.0-alpha` anchor starts at
11150000 and allows 0–49999 subsequent commits. `v1.12.0` starts at 11200000.
The Gradle build fails if a range is exhausted or an unsupported anchor is used.
Do not move the alpha anchor or create alpha.2/beta/RC anchors: those would reset
the commit counter. Published histories must not be rewritten.

For example, `v1.11.2` is 11102000, alpha plus 125 commits is 11150125,
and `v1.12.1` is 11201000. The app's existing Git-derived version names and
flavor suffixes are retained. Alpha installations cannot downgrade to 1.11;
test maintenance on an installation that has not upgraded to 1.12.

Run `./gradlew :app:checkAppVersioning` for allocation boundary checks. Generate
version files under `app/build/outputs/app_versioning/` for both flavors with:

```sh
./gradlew :app:generateAppVersionInfoForGoogleRelease :app:generateAppVersionInfoForOssRelease
```

Before pushing the cutover, deploy the shared publisher's beta routing, configure
`play-beta` for main-only deployments, run the signer's OIDC setup with
`--enable-beta --apply`, and enable `PLAY_BETA_ENABLED`. Inspect current Play
track codes first: maintenance must remain below 11150000 and existing alpha
codes must be below the first new alpha build. Both tracks retain testing-only
permissions; production promotion stays manual. Push main and the annotated
alpha anchor together after the publisher is ready. When both main and a tag
build the same commit, the exact version tag owns publication.
