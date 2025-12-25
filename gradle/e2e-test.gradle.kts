import java.net.Socket
import javax.inject.Inject
import org.gradle.api.file.ProjectLayout
import org.gradle.process.ExecOperations

/**
 * E2E Test Gradle Tasks
 *
 * This script provides tasks for running E2E tests against a Docker SSH server
 * with automatic emulator management.
 *
 * Usage:
 *   ./gradlew e2eTest              - Run full E2E test suite (starts emulator automatically)
 *   ./gradlew e2eTestNoEmulator    - Run E2E tests on already running emulator/device
 *   ./gradlew e2eEmulatorStart     - Start emulator only
 *   ./gradlew e2eEmulatorStop      - Stop emulator
 *   ./gradlew e2eDockerStart       - Start SSH server container
 *   ./gradlew e2eDockerStop        - Stop SSH server container
 */

// Configuration - resolved at configuration time
val androidHome: String = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: ""
val adbPath = if (androidHome.isNotEmpty()) "$androidHome/platform-tools/adb" else "adb"
val emulatorPath = if (androidHome.isNotEmpty()) "$androidHome/emulator/emulator" else "emulator"
val avdManagerPath = if (androidHome.isNotEmpty()) "$androidHome/cmdline-tools/latest/bin/avdmanager" else "avdmanager"
val sdkManagerPath = if (androidHome.isNotEmpty()) "$androidHome/cmdline-tools/latest/bin/sdkmanager" else "sdkmanager"

val dockerComposeFile = layout.projectDirectory.file("../docker/e2e/docker-compose.yml").asFile.absolutePath
val buildDir = layout.buildDirectory.get().asFile.absolutePath

// E2E Test configuration
val e2eSshPort = 2222
val e2eSshUser = "testuser"
val e2eSshPassword = "testpass123"
val e2eAvdName = "connectbot_e2e_test"
val e2eApiLevel = 29
val e2eSshHost = "10.0.2.2"

// ============================================================================
// Custom Task Classes (Configuration Cache Compatible)
// ============================================================================

abstract class E2eCheckKvmTask : DefaultTask() {
    @TaskAction
    fun checkKvm() {
        val kvmDevice = File("/dev/kvm")
        if (!kvmDevice.exists()) {
            throw GradleException("""
                |KVM device /dev/kvm not found.
                |Hardware acceleration is required for x86_64 Android emulator.
                |
                |On Linux, ensure KVM is installed:
                |  sudo apt-get install qemu-kvm
                |
                |Then check if your CPU supports virtualization.
            """.trimMargin())
        }

        if (!kvmDevice.canRead() || !kvmDevice.canWrite()) {
            throw GradleException("""
                |KVM permission denied. Your user needs access to /dev/kvm.
                |
                |Run this command and then log out/in (or reboot):
                |  sudo gpasswd -a ${'$'}USER kvm
                |
                |After logging back in, verify with:
                |  ls -la /dev/kvm
            """.trimMargin())
        }

        println("KVM is available and accessible")
    }
}

abstract class E2eCreateAvdTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val avdName: Property<String>

    @get:Input
    abstract val apiLevel: Property<Int>

    @get:Input
    abstract val avdManagerPath: Property<String>

    @get:Input
    abstract val sdkManagerPath: Property<String>

    @TaskAction
    fun createAvd() {
        // Check if AVD already exists
        val listOutput = java.io.ByteArrayOutputStream()
        execOps.exec {
            commandLine(avdManagerPath.get(), "list", "avd", "-c")
            standardOutput = listOutput
            isIgnoreExitValue = true
        }

        if (listOutput.toString().contains(avdName.get())) {
            println("AVD '${avdName.get()}' already exists")
            return
        }

        println("Creating AVD '${avdName.get()}'...")

        // Install system image if needed
        execOps.exec {
            commandLine(sdkManagerPath.get(), "--install", "system-images;android-${apiLevel.get()};default;x86_64")
            isIgnoreExitValue = true
        }

        // Create AVD
        execOps.exec {
            commandLine(
                avdManagerPath.get(), "create", "avd",
                "--name", avdName.get(),
                "--package", "system-images;android-${apiLevel.get()};default;x86_64",
                "--device", "pixel_4",
                "--force"
            )
            standardInput = java.io.ByteArrayInputStream("no\n".toByteArray())
        }

        println("AVD '${avdName.get()}' created successfully")
    }
}

abstract class E2eEmulatorStartTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val avdName: Property<String>

    @get:Input
    abstract val adbPath: Property<String>

    @get:Input
    abstract val emulatorPath: Property<String>

    @get:Input
    abstract val buildDir: Property<String>

    @TaskAction
    fun startEmulator() {
        // Check if emulator is already running
        val output = java.io.ByteArrayOutputStream()
        execOps.exec {
            commandLine(adbPath.get(), "devices")
            standardOutput = output
            isIgnoreExitValue = true
        }

        if (output.toString().contains("emulator")) {
            println("Emulator already running")
            return
        }

        println("Starting emulator '${avdName.get()}'...")

        // Start emulator in background with output redirected to file for debugging
        val logFile = File("${buildDir.get()}/e2e-emulator.log")
        logFile.parentFile.mkdirs()

        val emulatorProcess = ProcessBuilder(
            emulatorPath.get(),
            "-avd", avdName.get(),
            "-no-window",
            "-no-audio",
            "-no-boot-anim",
            "-gpu", "swiftshader_indirect",
            "-no-snapshot-save"
        ).apply {
            redirectErrorStream(true)
            redirectOutput(logFile)
        }.start()

        // Store PID for later cleanup
        val pidFile = File("${buildDir.get()}/e2e-emulator.pid")
        pidFile.writeText(emulatorProcess.pid().toString())

        println("Emulator starting with PID: ${emulatorProcess.pid()}")
        println("Emulator log: ${logFile.absolutePath}")

        // Give emulator a moment to start
        Thread.sleep(3000)

        // Check if process is still alive
        if (!emulatorProcess.isAlive) {
            val logContent = if (logFile.exists()) logFile.readText().take(1000) else "No log available"
            throw GradleException("Emulator failed to start. Log:\n$logContent")
        }
    }
}

abstract class E2eWaitForEmulatorTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val adbPath: Property<String>

    @TaskAction
    fun waitForEmulator() {
        println("Waiting for emulator to boot...")

        val maxRetries = 120  // 2 minutes
        var retries = 0
        var booted = false

        while (!booted && retries < maxRetries) {
            val output = java.io.ByteArrayOutputStream()
            val result = execOps.exec {
                commandLine(adbPath.get(), "shell", "getprop", "sys.boot_completed")
                standardOutput = output
                errorOutput = java.io.ByteArrayOutputStream()
                isIgnoreExitValue = true
            }

            if (result.exitValue == 0 && output.toString().trim() == "1") {
                booted = true
            } else {
                Thread.sleep(1000)
                retries++
                if (retries % 10 == 0) {
                    println("Still waiting for emulator boot... (${retries}s)")
                }
            }
        }

        if (!booted) {
            throw GradleException("Emulator failed to boot within timeout")
        }

        // Additional wait for package manager to be ready
        println("Emulator booted, waiting for package manager...")
        Thread.sleep(5000)

        // Disable animations for more reliable tests
        listOf(
            "window_animation_scale",
            "transition_animation_scale",
            "animator_duration_scale"
        ).forEach { setting ->
            execOps.exec {
                commandLine(adbPath.get(), "shell", "settings", "put", "global", setting, "0")
                isIgnoreExitValue = true
            }
        }

        println("Emulator is ready!")
    }
}

abstract class E2eEmulatorStopTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val adbPath: Property<String>

    @get:Input
    abstract val buildDir: Property<String>

    @TaskAction
    fun stopEmulator() {
        println("Stopping emulator...")

        // Try graceful shutdown first
        execOps.exec {
            commandLine(adbPath.get(), "emu", "kill")
            isIgnoreExitValue = true
        }

        // Also kill by PID if we have it
        val pidFile = File("${buildDir.get()}/e2e-emulator.pid")
        if (pidFile.exists()) {
            val pid = pidFile.readText().trim()
            execOps.exec {
                commandLine("kill", pid)
                isIgnoreExitValue = true
            }
            pidFile.delete()
        }

        println("Emulator stopped")
    }
}

abstract class E2eWaitForSshTask : DefaultTask() {

    @get:Input
    abstract val sshPort: Property<Int>

    @TaskAction
    fun waitForSsh() {
        val maxRetries = 30
        var retries = 0
        var ready = false

        println("Waiting for SSH server on port ${sshPort.get()}...")

        while (!ready && retries < maxRetries) {
            try {
                Socket("localhost", sshPort.get()).use {
                    ready = true
                    println("SSH server is ready!")
                }
            } catch (e: Exception) {
                Thread.sleep(1000)
                retries++
                if (retries % 5 == 0) {
                    println("Still waiting for SSH server... (${retries}/${maxRetries})")
                }
            }
        }

        if (!ready) {
            throw GradleException("SSH server failed to start within timeout")
        }
    }
}

abstract class E2eCleanTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val adbPath: Property<String>

    @get:Input
    abstract val avdManagerPath: Property<String>

    @get:Input
    abstract val avdName: Property<String>

    @get:Input
    abstract val dockerComposeFile: Property<String>

    @get:Input
    abstract val buildDir: Property<String>

    @TaskAction
    fun clean() {
        execOps.exec {
            commandLine(adbPath.get(), "emu", "kill")
            isIgnoreExitValue = true
        }
        execOps.exec {
            commandLine("docker", "compose", "-f", dockerComposeFile.get(), "down", "-v", "--rmi", "local")
            isIgnoreExitValue = true
        }
        execOps.exec {
            commandLine(avdManagerPath.get(), "delete", "avd", "-n", avdName.get())
            isIgnoreExitValue = true
        }
        val pidFile = File("${buildDir.get()}/e2e-emulator.pid")
        if (pidFile.exists()) {
            pidFile.delete()
        }
        println("E2E cleanup completed")
    }
}

// ============================================================================
// Task Registration
// ============================================================================

// Check KVM availability
tasks.register<E2eCheckKvmTask>("e2eCheckKvm") {
    group = "e2e"
    description = "Check if KVM is available for hardware acceleration"
}

// Create AVD
val avdManagerPathVal = avdManagerPath
val sdkManagerPathVal = sdkManagerPath
val adbPathVal = adbPath
val emulatorPathVal = emulatorPath
val buildDirVal = buildDir
val dockerComposeFileVal = dockerComposeFile

tasks.register<E2eCreateAvdTask>("e2eCreateAvd") {
    group = "e2e"
    description = "Create AVD for E2E testing if it doesn't exist"
    avdName.set(e2eAvdName)
    apiLevel.set(e2eApiLevel)
    this.avdManagerPath.set(avdManagerPathVal)
    this.sdkManagerPath.set(sdkManagerPathVal)
}

// Start emulator
tasks.register<E2eEmulatorStartTask>("e2eEmulatorStart") {
    group = "e2e"
    description = "Start the E2E test emulator"
    dependsOn("e2eCreateAvd", "e2eCheckKvm")
    avdName.set(e2eAvdName)
    this.adbPath.set(adbPathVal)
    this.emulatorPath.set(emulatorPathVal)
    this.buildDir.set(buildDirVal)
}

// Wait for emulator
tasks.register<E2eWaitForEmulatorTask>("e2eWaitForEmulator") {
    group = "e2e"
    description = "Wait for emulator to fully boot"
    dependsOn("e2eEmulatorStart")
    this.adbPath.set(adbPathVal)
}

// Stop emulator
tasks.register<E2eEmulatorStopTask>("e2eEmulatorStop") {
    group = "e2e"
    description = "Stop the E2E test emulator"
    this.adbPath.set(adbPathVal)
    this.buildDir.set(buildDirVal)
}

// Docker tasks
tasks.register<Exec>("e2eDockerStart") {
    group = "e2e"
    description = "Start Docker containers for E2E testing"
    commandLine("docker", "compose", "-f", dockerComposeFile, "up", "-d", "--build")
    doLast {
        println("Docker containers starting...")
    }
}

tasks.register<Exec>("e2eDockerStop") {
    group = "e2e"
    description = "Stop and remove Docker containers"
    commandLine("docker", "compose", "-f", dockerComposeFile, "down", "-v")
    isIgnoreExitValue = true
}

// Wait for SSH
tasks.register<E2eWaitForSshTask>("e2eWaitForSsh") {
    group = "e2e"
    description = "Wait for SSH server to accept connections"
    dependsOn("e2eDockerStart")
    sshPort.set(e2eSshPort)
}

// Task to install APKs
abstract class E2eInstallApksTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val adbPath: Property<String>

    @get:Input
    abstract val buildDir: Property<String>

    @get:Input
    abstract val sshHost: Property<String>

    @get:Input
    abstract val sshPort: Property<Int>

    @TaskAction
    fun installApks() {
        // Install with -g flag to grant all runtime permissions including POST_NOTIFICATIONS
        execOps.exec {
            commandLine(adbPath.get(), "install", "-r", "-t", "-g",
                "${buildDir.get()}/outputs/apk/oss/debug/app-oss-debug.apk")
        }
        execOps.exec {
            commandLine(adbPath.get(), "install", "-r", "-t", "-g",
                "${buildDir.get()}/outputs/apk/androidTest/oss/debug/app-oss-debug-androidTest.apk")
        }
        println("APKs installed with permissions. Running E2E tests against SSH server at ${sshHost.get()}:${sshPort.get()}")
    }
}

// Install APKs task (with emulator)
tasks.register<E2eInstallApksTask>("e2eInstallApks") {
    group = "e2e"
    description = "Install APKs on connected device/emulator"
    dependsOn("e2eWaitForSsh", "e2eWaitForEmulator", "assembleOssDebug", "assembleOssDebugAndroidTest")
    adbPath.set(adbPathVal)
    buildDir.set(buildDirVal)
    sshHost.set(e2eSshHost)
    sshPort.set(e2eSshPort)
}

// Install APKs task (without emulator)
tasks.register<E2eInstallApksTask>("e2eInstallApksNoEmulator") {
    group = "e2e"
    description = "Install APKs on already running device/emulator"
    dependsOn("e2eWaitForSsh", "assembleOssDebug", "assembleOssDebugAndroidTest")
    adbPath.set(adbPathVal)
    buildDir.set(buildDirVal)
    sshHost.set(e2eSshHost)
    sshPort.set(e2eSshPort)
}

// Run E2E tests (with emulator management)
tasks.register<Exec>("e2eRunTests") {
    group = "e2e"
    description = "Run E2E tests on connected device/emulator"
    dependsOn("e2eInstallApks")

    commandLine(
        adbPathVal, "shell", "am", "instrument",
        "-w",
        "-e", "class", "org.connectbot.e2e.SshConnectionE2ETest,org.connectbot.e2e.PasswordAuthE2ETest,org.connectbot.e2e.PubkeyAuthE2ETest",
        "-e", "sshHost", e2eSshHost,
        "-e", "sshPort", e2eSshPort.toString(),
        "-e", "sshUser", e2eSshUser,
        "-e", "sshPassword", e2eSshPassword,
        "org.connectbot.tests/org.connectbot.HiltTestRunner"
    )
}

// Run E2E tests (without emulator management)
tasks.register<Exec>("e2eRunTestsNoEmulator") {
    group = "e2e"
    description = "Run E2E tests on already running device/emulator"
    dependsOn("e2eInstallApksNoEmulator")

    commandLine(
        adbPathVal, "shell", "am", "instrument",
        "-w",
        "-e", "class", "org.connectbot.e2e.SshConnectionE2ETest,org.connectbot.e2e.PasswordAuthE2ETest,org.connectbot.e2e.PubkeyAuthE2ETest",
        "-e", "sshHost", e2eSshHost,
        "-e", "sshPort", e2eSshPort.toString(),
        "-e", "sshUser", e2eSshUser,
        "-e", "sshPassword", e2eSshPassword,
        "org.connectbot.tests/org.connectbot.HiltTestRunner"
    )
}

// Main E2E test task (starts emulator automatically)
tasks.register("e2eTest") {
    group = "e2e"
    description = "Run full E2E test suite (starts emulator and SSH server automatically)"
    dependsOn("e2eRunTests")
    finalizedBy("e2eEmulatorStop", "e2eDockerStop")
    doLast {
        println("E2E tests completed!")
    }
}

// E2E test task without managing emulator
tasks.register("e2eTestNoEmulator") {
    group = "e2e"
    description = "Run E2E tests using existing device/emulator (only manages SSH server)"
    dependsOn("e2eRunTestsNoEmulator")
    finalizedBy("e2eDockerStop")
    doLast {
        println("E2E tests completed!")
    }
}

// Cleanup task
tasks.register<E2eCleanTask>("e2eClean") {
    group = "e2e"
    description = "Clean up E2E test artifacts, emulator, and containers"
    adbPath.set(adbPathVal)
    avdManagerPath.set(avdManagerPathVal)
    avdName.set(e2eAvdName)
    dockerComposeFile.set(dockerComposeFileVal)
    buildDir.set(buildDirVal)
}
