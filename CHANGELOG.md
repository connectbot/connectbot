# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased][unreleased]
### Fixed
- Pubkeys now have the correct strength listed in the pubkey list.
- EC key operations would fail on some devices.
- Connecting to a host from the host list no longer asks which
  app you want to use.
- The text in the entropy gathering dialog is now scaled correctly.

### Changed
- Default RSA key size is now 2048 bits.

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


[unreleased]: https://github.com/connectbot/connectbot/compare/v1.8.4...HEAD
[1.8.4]: https://github.com/connectbot/connectbot/compare/v1.8.3...v1.8.4
[1.8.3]: https://github.com/connectbot/connectbot/compare/v1.8.2...v1.8.3
