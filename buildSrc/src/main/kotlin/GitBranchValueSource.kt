import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class GitBranchValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val branchNameFromEnv = System.getenv("BRANCH_NAME")
        if (branchNameFromEnv != null) {
            return branchNameFromEnv
        }

        val branchOutput = ByteArrayOutputStream()
        execOperations.exec {
            commandLine = listOf("git", "rev-parse", "--abbrev-ref", "HEAD")
            standardOutput = branchOutput
        }
        val branchName = branchOutput.toString().trim()

        // Check if branch exists on remote
        val remoteCheckResult = execOperations.exec {
            commandLine = listOf("git", "rev-parse", "--verify", "refs/remotes/origin/$branchName")
            isIgnoreExitValue = true
        }

        return if (remoteCheckResult.exitValue == 0) branchName else "main"
    }
}
