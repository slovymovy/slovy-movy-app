import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

data class TestServerWiring(
    val service: Provider<TestServerService>,
    val startTask: TaskProvider<StartTestServerTask>,
)

fun Project.registerTestServer(
    port: Int,
    startTaskName: String = "startTestServer",
): TestServerWiring {
    val service = gradle.sharedServices.registerIfAbsent(
        "testServerService",
        TestServerService::class.java
    ) {
        val serverProject = project(":server")
        parameters.classpath.set(
            serverProject.provider {
                val config = serverProject.configurations.getByName("runtimeClasspath")
                val classesDir = serverProject.layout.buildDirectory.dir("classes/kotlin/main").get().asFile.absolutePath
                val resourcesDir = serverProject.layout.buildDirectory.dir("resources/main").get().asFile.absolutePath
                (listOf(classesDir, resourcesDir) + config.files.map { it.absolutePath }).distinct()
            }
        )
        parameters.workingDir.set(rootProject.projectDir.absolutePath)
        parameters.port.set(port)
        parameters.dbDir.set(File(rootProject.projectDir, ".test-db-files").absolutePath)
        maxParallelUsages.set(1)
    }

    val startTask = tasks.register(startTaskName, StartTestServerTask::class.java) {
        dependsOn(":server:classes")
    }

    return TestServerWiring(service, startTask)
}

fun Task.usesTestServer(wiring: TestServerWiring) {
    dependsOn(":server:classes")
    dependsOn(wiring.startTask)
    usesService(wiring.service)
}

fun Project.configureTasksToUseTestServer(
    wiring: TestServerWiring,
    vararg taskNames: String,
) {
    val names = taskNames.toSet()
    tasks.matching { it.name in names }.configureEach {
        usesTestServer(wiring)
    }
}
