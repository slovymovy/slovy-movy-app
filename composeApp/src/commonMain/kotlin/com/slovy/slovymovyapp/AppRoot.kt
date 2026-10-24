package com.slovy.slovymovyapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.slovy.slovymovyapp.data.export.AppDataImportManager
import com.slovy.slovymovyapp.data.export.AppDataImportOutcome
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.provider.GoogleStorageBucketDataProvider
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process entry point shared by every platform shell. Opens the app database and builds [App],
 * but first applies a staged app-data import if one is waiting: that swaps `app.db` on disk, so
 * it has to finish before anything holds a connection.
 */
@Composable
fun AppRoot(
    platform: PlatformDbSupport,
    appBuildConfig: AppBuildConfig,
    androidContext: Any?,
) {
    val startupImport = remember(platform) { StartupAppDataImport.start(platform) }
    var importFinished by remember(startupImport) { mutableStateOf(startupImport.isCompleted) }

    if (!importFinished) {
        LaunchedEffect(startupImport) {
            startupImport.join()
            importFinished = true
        }
        AppTheme {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SpinningProgressIndicator()
            }
        }
        return
    }

    val appDatabase = remember(platform) { DataDbManager.openAppDatabase(platform) }
    val settingsRepository = remember(appDatabase) { SettingsRepository(appDatabase) }
    val dataManager = remember(platform, settingsRepository) {
        DataDbManager(platform, settingsRepository, GoogleStorageBucketDataProvider())
    }
    App(
        settingsRepository = settingsRepository,
        dataManager = dataManager,
        platform = platform,
        appBuildConfig = appBuildConfig,
        androidContext = androidContext,
        startupImportOutcome = StartupAppDataImport.outcome,
    )
}

/**
 * Runs the staged-import gate exactly once per process, on a scope that outlives any composition.
 * A composition that is cancelled half-way (an Activity recreated during the spinner) must neither
 * abort the swap nor start a second one, and an archive staged later in the same process waits
 * for the next launch, when no database connection exists yet.
 */
private object StartupAppDataImport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** The outcome of this process's gate; `null` until it finished or when nothing was staged. */
    var outcome: AppDataImportOutcome? = null
        private set

    /** Starts the gate on the first call of the process and returns the same job afterwards. Main thread only. */
    fun start(platform: PlatformDbSupport): Job {
        job?.let { return it }
        if (!AppDataImportManager.hasPendingStartupWork(platform)) {
            return Job().apply { complete() }.also { job = it }
        }
        return scope.launch { outcome = run(platform) }.also { job = it }
    }

    private suspend fun run(platform: PlatformDbSupport): AppDataImportOutcome? = try {
        val result = AppDataImportManager(platform).applyStagedImportIfPresent()
        if (result != null) {
            AppLogger.info(
                "AppDataImport",
                "Applied staged import: installed ${result.installedFiles.joinToString()}; " +
                    "removed ${result.removedFiles.joinToString().ifEmpty { "nothing" }}",
                null,
            )
            AppDataImportOutcome.Applied(result)
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLogger.error("AppDataImport", "Staged import was not applied: ${e.message}", e)
        AppDataImportOutcome.Failed(e.message ?: e::class.simpleName ?: "unknown error")
    }
}
