# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [main][main]
### Changed
- Updated to API 34
- Set foreground service type to "remote messaging"

## [1.9.11][1.9.11]
### Fixed
- Fix Persistent notification no longer working (#1352) (by rcheze)

### Added
- Allow connecting to IPv6 Link-local addresses (by Antonis Kanouras)

### Changed
- Update to a newer sshlib with fixes for EdDSA providers in Google Play Services
- Configure Ed25519 key generation to use 255 bits
- Add reference to monochrome icon (by Niklas Höher)
- Improve Proguard rules by ignoring `@Nullable` annotations
- Upload native debug symbols during build
- Pin GitHub Actions to use macOS 13 to avoid emulator issues
- Pin all used GitHub Actions for improved security and reproducibility
- Improve and update CI/CD workflows

### Translations
- Add Turkmen translations
- Automated translation imports from Launchpad
- Fix translations export tool

### Dependencies
- Update dependencies

## [1.9.10][1.9.10]
### Fixed
- Allow underscores in the hostmask (by Brian J. Murrell)
- Fix click failure for emulated keys on Android > 12 when scrolling to max position (by Andreas Richter)
- Fix accidental size bloat in Play Store release build
- Fix issues with the remote signing workflow in CI/CD

### Changed
- Update sshlib to 2.2.22 for mitigation against the Terrapin attack
- Update sshlib to 2.2.23 to fix transitive dependencies from Tink
- Update the Android SDK to the newest version (targetSdkVersion 33)
- Update Android Gradle Plugin to 7.2.2
- Improve build configuration
- Improve CI/CD workflows
- Update translations installation workflow

### Added
- Monochrome app icon support (by JFronny)

### Translations
- Automated translation imports from Launchpad

### Dependencies
- Update dependencies

## [1.9.9][1.9.9]
### Fixed
- Fix text selection for certain font sizes (by Pavel Roskin)
- Fix lint warnings (by Hannes Achleitner)

### Changed
- Simplified code (by Hannes Achleitner)
- Update to Gradle 7.2
- Set compileSdkVersion to 31
- Simplify NDK (by Hannes Achleitner)
- Move to upstream android-emulator-runner
- Exclude accessibility testing (related to Espresso dependency update)

### Translations
- Updated some Finnish translations (by Perry Thompson)
- Automated translation imports from Launchpad
- Translation infrastructure updates (export with new CLDR, use new plurals from CLDR)

### Dependencies
- Update dependencies

## [1.9.8][1.9.8]
### Fixed
- Issue on some MediaTek phones where arithmetic errors show up
  when connecting using Ed25519.

## [1.9.7][1.9.7]
### Fixed
- Added support for rsa-sha2-512 by updating sshlib

## [1.9.6][1.9.6]
### Fixed
- Fixed crash when using tmux

## [1.9.5][1.9.5]
### Fixed
- This fixes an incompatibility with OpenSSH 7.8

## [1.9.0-alpha1][1.9.0-alpha1]
### Changed
- Major Material Design overhaul of UI.
- Improved cut-and-paste interface.

### Added
- Tabs are now used on large screens.
- Keyboard shortcuts and EULA in help menu.
- Terminal mouse support including mouse wheel.
- Full IPv6 host support.

### Removed
- No more intro wizard on first start.
- No more outdated physical or virtual keyboard instructions.

## [1.8.6][1.8.6] - 2015-08-24
### Fixed
- Crash due to no grantpt symbol in newer NDK was fixed.

## [1.8.5][1.8.5] - 2015-08-11
### Added
- Mouse support for right click and selection and mouse wheel
  (third button) to paste.
- Hot keys for keyboard including Ctrl - and Ctrl + for decreasing
  and increasing font resolution and Ctrl Shift V for pasting.
- Running notification now has a "disconnect all" button to
  quickly close all connections.
- Support for all ABIs including x86, MIPS, aarch64.

### Changed
- Default RSA key size is now 2048 bits.
- New soft keypad including directional arrows.
- Moved from ViewFlipper to ViewPager for better swipe handling in
  the console.

### Fixed
- Pubkeys now have the correct strength listed in the pubkey list.
- EC key operations would fail on some devices.
- Connecting to a host from the host list no longer asks which
  app you want to use.
- The text in the entropy gathering dialog is now scaled correctly.
- Touch slop was not correctly scaled when determining dragging.

## [1.8.4][1.8.4] - 2015-04-19
### Fixed
- Key exchange and host key algorithm preference order was not being
  respected.
- ECDH would sometimes fail because the shared secret would be encoded
  as a negative integer.
- DSA host key support was broken from the beginning of the v1.8 series.
- Connections would sometimes close when leaving ConnectBot.
- Telnet port range too high will no longer cause crashes.

### Added
- More context is given for failures to connect via SSH which should
  reveal why a host might be incompatible with ConnectBot.
- SSH key exchange algorithm will now be printed upon connection.
- All addresses for a particular host will be tried when connecting
  (including IPv6).

## [1.8.3][1.8.3] - 2015-04-02
### Fixed
- Only enable EC support when the device supports it.
- Default font size scales with the device display density.
- Color picker scales correctly depending on device density.
- Color picker color numbers are now localized


[main]: http://github.com/connectbot/connectbot/compare/v1.9.11...main
[1.9.11]: https://github.com/connectbot/connectbot/compare/v1.9.10...v1.9.11
[1.9.10]: https://github.com/connectbot/connectbot/compare/v1.9.9...v1.9.10
[1.9.9]: https://github.com/connectbot/connectbot/compare/v1.9.8...v1.9.9
[1.9.8]: https://github.com/connectbot/connectbot/compare/v1.9.7...v1.9.8
[1.9.7]: https://github.com/connectbot/connectbot/compare/v1.9.6...v1.9.7
[1.9.6]: https://github.com/connectbot/connectbot/compare/v1.9.5...v1.9.6
[1.9.5]: https://github.com/connectbot/connectbot/compare/v1.9.4...v1.9.5
[1.9.0-alpha1]: https://github.com/connectbot/connectbot/compare/v1.8.6...v1.9.0-alpha1
[1.8.6]: https://github.com/connectbot/connectbot/compare/v1.8.5...v1.8.6
[1.8.5]: https://github.com/connectbot/connectbot/compare/v1.8.4...v1.8.5
[1.8.4]: https://github.com/connectbot/connectbot/compare/v1.8.3...v1.8.4
[1.8.3]: https://github.com/connectbot/connectbot/compare/v1.8.2...v1.8.3
