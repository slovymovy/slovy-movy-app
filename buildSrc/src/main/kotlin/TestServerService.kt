import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

abstract class TestServerService : BuildService<TestServerService.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        val classpath: ListProperty<String>
        val workingDir: Property<String>
        val port: Property<Int>
        val dbDir: Property<String>
    }

    private var process: Process? = null
    private val logFile = File(parameters.workingDir.get(), "build/test-server.log")

    init {
        val javaHome = System.getProperty("java.home")
        val javaExec = File(javaHome, "bin/java").absolutePath
        val classpath = parameters.classpath.get().joinToString(File.pathSeparator)
        val port = parameters.port.get()

        val command = listOf(javaExec, "-cp", classpath, "com.slovy.slovymovyapp.ApplicationKt")
        val builder = ProcessBuilder(command)
            .directory(File(parameters.workingDir.get()))
            .redirectErrorStream(true)
            .redirectOutput(logFile)

        val env = builder.environment()
        env["IS_TEST"] = "true"
        env["SERVER_PORT"] = port.toString()
        env["TEST_DB_DIR"] = parameters.dbDir.get()

        process = builder.start()
        waitForServer(port)
    }

    private fun waitForServer(port: Int) {
        val start = System.currentTimeMillis()
        val timeoutMs = 20_000L
        val healthUrl = URI.create("http://127.0.0.1:$port/health").toURL()

        while (System.currentTimeMillis() - start < timeoutMs) {
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
                    return
                }
                conn.disconnect()
            } catch (_: Exception) {
                // retry
            }
            Thread.sleep(200)
        }
        throw RuntimeException(
            "Test server did not become ready within ${timeoutMs}ms. " +
                    "Log: ${logFile.absolutePath}\n${tailLog(logFile)}"
        )
    }

    override fun close() {
        val proc = process ?: return
        proc.destroy()
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            proc.waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun tailLog(file: File, lines: Int = 50): String {
        if (!file.exists()) return "(no test server log found)"
        val content = file.readLines()
        return content.takeLast(lines).joinToString(System.lineSeparator())
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
