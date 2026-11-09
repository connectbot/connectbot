# CTAP2 JVM library

This module contains blocking CTAP2 operations backed by YubiKit. It produces a
JAR and uses Java 17. Build and test it with `./gradlew :ctap2:build` from the
repository root. Root Gradle configuration still includes the Android projects.

`Ctap2Client` owns a single session. Serialize its calls on a worker thread and
close it when finished. `YubiKitCtap2Client.open` accepts a JVM HID or smart-card
connection and transfers connection ownership to the returned client.

Credential enumeration accepts a relying-party predicate; it runs before fetching
and decoding that relying party's credentials. Assertion requests accept an
already computed 32-byte client-data hash. Callers choose the relying party and
perform application-specific hashing and signature encoding.

The Android transport adapter lives in `ctap2-android`. SSH, cbssh, persistence,
and user interaction remain in the application. Artifact publication and
additional CTAP2 operations are deferred.
