package com.slovy.slovymovyapp.data.remote

import kotlinx.io.files.Path

// Actual implementations live in iosArm64Main and iosSimulatorArm64Main where
// NSObject / NSURLSession ObjC interop is available.
internal expect suspend fun nsUrlSessionDownload(
    url: String,
    headers: Map<String, String>,
    destPath: Path,
    tempPath: Path,
    onProgress: (DownloadProgress) -> Unit,
    cancelToken: CancelToken,
    moveFile: (from: Path, to: Path) -> Boolean,
    deleteFile: (path: Path) -> Unit,
)
