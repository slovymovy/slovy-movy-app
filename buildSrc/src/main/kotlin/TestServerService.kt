import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val logger = Logging.getLogger(TestServerService::class.java)

abstract class TestServerService : BuildService<TestServerService.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        val classpath: ListProperty<String>
        val workingDir: Property<String>
        val port: Property<Int>
        val dbDir: Property<String>
        val listsDir: Property<String>
    }

    private var process: Process? = null
    private val logFile = File(parameters.workingDir.get(), "build/test-server.log").also {
        it.parentFile.mkdirs()
    }

    init {
        val javaExec = findJavaExecutable()
        logger.lifecycle("Using Java executable: $javaExec")

        val classpath = parameters.classpath.get().joinToString(File.pathSeparator)
        val port = parameters.port.get()

        logger.lifecycle("Checking for existing process on port $port...")
        killProcessOnPort(port)

        logger.lifecycle("Starting test server on port $port...")
        logger.lifecycle("Classpath is: $classpath")
        val command = listOf(javaExec, "-cp", classpath, "com.slovy.slovymovyapp.ApplicationKt")
        val builder = ProcessBuilder(command)
            .directory(File(parameters.workingDir.get()))
            .redirectErrorStream(true)
            .redirectOutput(logFile)

        val env = builder.environment()
        env["IS_TEST"] = "true"
        env["SERVER_PORT"] = port.toString()
        env["TEST_DB_DIR"] = parameters.dbDir.get()
        env["TEST_LISTS_DIR"] = parameters.listsDir.get()

        process = builder.start()
        logger.lifecycle("Server process started (PID: ${process?.pid()}), waiting for health check...")
        waitForServer(port)
    }

    private fun waitForServer(port: Int) {
        val start = System.currentTimeMillis()
        val timeoutMs = 1.minutes.inWholeMilliseconds
        val healthUrl = URI.create("http://127.0.0.1:$port/health").toURL()
        var lastLogTime = 0L

        while (System.currentTimeMillis() - start < timeoutMs) {
            val elapsed = System.currentTimeMillis() - start
            if (elapsed - lastLogTime >= 5000) {
                logger.lifecycle("Still waiting for server... (${elapsed / 1000}s elapsed)")
                lastLogTime = elapsed
            }

            val proc = process
            if (proc != null && !proc.isAlive) {
                throw RuntimeException(
                    "Test server exited early with code ${proc.exitValue()}. " +
                            "Log: ${logFile.absolutePath}\n${tailLog(logFile)}"
                )
            }
            try {
                val conn = healthUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 500
                conn.readTimeout = 500
                conn.requestMethod = "GET"
                conn.connect()
                if (conn.responseCode == 200) {
                    conn.disconnect()
                    val totalTime = System.currentTimeMillis() - start
                    logger.lifecycle("Test server is ready! (took ${totalTime}ms)")
                    return
                }
                conn.disconnect()
            } catch (_: Exception) {
                // retry
            }
            Thread.sleep(200.milliseconds.inWholeMilliseconds)
        }
        throw RuntimeException(
            "Test server did not become ready within ${timeoutMs}ms. " +
                    "Log: ${logFile.absolutePath}\n${tailLog(logFile)}"
        )
    }

    override fun close() {
        val proc = process ?: return
        logger.lifecycle("Stopping test server (PID: ${proc.pid()})...")
        proc.destroy()
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            logger.lifecycle("Server did not stop gracefully, force killing...")
            proc.destroyForcibly()
            proc.waitFor(5, TimeUnit.SECONDS)
        }
        logger.lifecycle("Test server stopped.")
    }

    private fun tailLog(file: File, lines: Int = 50): String {
        if (!file.exists()) return "(no test server log found)"
        val content = file.readLines()
        return content.takeLast(lines).joinToString(System.lineSeparator())
    }

    companion object {


        private fun findJavaExecutable(): String {
            val javaExecName = if (OperatingSystem.current().isWindows) "java.exe" else "java"

            // Try JAVA_HOME environment variable first
            System.getenv("JAVA_HOME")?.let { javaHome ->
                val candidate = File(javaHome, "bin/$javaExecName")
                if (candidate.exists()) return candidate.absolutePath
            }

            // Try java.home system property
            System.getProperty("java.home")?.let { javaHome ->
                val candidate = File(javaHome, "bin/$javaExecName")
                if (candidate.exists()) return candidate.absolutePath
            }

            // Fall back to finding java on PATH
            val whichCommand =
                if (OperatingSystem.current().isWindows) listOf("where", "java") else listOf("which", "java")
            try {
                val process = ProcessBuilder(whichCommand)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readLine()?.trim()
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0 && !output.isNullOrBlank()) {
                    return output
                }
            } catch (_: Exception) {
                // ignore and fall through
            }

            // Last resort: assume java is on PATH
            return javaExecName
        }

        private fun killProcessOnPort(port: Int) {
            try {
                if (OperatingSystem.current().isWindows) {
                    // Windows: use netstat to find PID, then taskkill
                    val netstatProcess = ProcessBuilder("cmd", "/c", "netstat -ano | findstr :$port")
                        .redirectErrorStream(true)
                        .start()
                    val output = netstatProcess.inputStream.bufferedReader().readText()
                    netstatProcess.waitFor(5, TimeUnit.SECONDS)

                    // Parse PIDs from netstat output (last column is PID)
                    val pids = output.lines()
                        .filter { it.contains(":$port") && it.contains("LISTENING") }
                        .mapNotNull { line ->
                            line.trim().split("\\s+".toRegex()).lastOrNull()?.toIntOrNull()
                        }
                        .distinct()

                    if (pids.isNotEmpty()) {
                        logger.lifecycle("Found existing process(es) on port $port: PIDs $pids, killing...")
                        for (pid in pids) {
                            ProcessBuilder("taskkill", "/PID", pid.toString(), "/F")
                                .redirectErrorStream(true)
                                .start()
                                .waitFor(5, TimeUnit.SECONDS)
                        }
                    } else {
                        logger.lifecycle("No existing process found on port $port")
                    }
                } else {
                    // Linux/macOS: use fuser with -k option
                    val fuserProcess = ProcessBuilder("fuser", "-k", "$port/tcp")
                        .redirectErrorStream(true)
                        .start()
                    val killed = fuserProcess.waitFor(5, TimeUnit.SECONDS) && fuserProcess.exitValue() == 0
                    if (killed) {
                        logger.lifecycle("Killed existing process on port $port")
                    } else {
                        logger.lifecycle("No existing process found on port $port")
                    }
                }
                // Give the OS a moment to release the port
                Thread.sleep(500.milliseconds.inWholeMilliseconds)
            } catch (e: Exception) {
                logger.lifecycle("Could not check/kill process on port $port: ${e.message}")
            }
        }
    }
}

abstract class StartTestServerTask : DefaultTask() {
    @get:ServiceReference("testServerService")
    abstract val testServerService: Property<TestServerService>

    @TaskAction
    fun start() {
        testServerService.get()
    }
}
