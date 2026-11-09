# CTAP2 Android library

Build the AAR with `./gradlew :ctap2-android:assembleRelease`. This module depends
on the JVM `ctap2` library and supplies USB/NFC discovery and session adapters.
It has no product flavors or native compilation.

Run operations through `Ctap2DeviceWorker` with a supplied I/O dispatcher. It
serializes access until SDK callbacks finish, including when the waiting caller
is cancelled. A USB session returned by `openUsbSession` belongs to its caller
and can be retained across PIN entry. `withNfcSession` executes operations and
closes the transient session within the SDK connection callback.

Start and stop discovery with the host activity lifecycle. USB and NFC hardware
features are optional. Device access requires Android USB permission or an NFC
capable device; mocked tests do not establish physical authenticator behavior.
