package com.hournet.hdrzk.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File

private const val TAG = "AutoUpdate"

@Composable
fun AutoUpdate(client: OkHttpClient) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var actualVersion by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var pendingInstall by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

    val currentVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (e: Exception) {
        Log.e(TAG, "Error getting current version", e)
        "0.0.0"
    }

    val scope = rememberCoroutineScope()

    // Launcher для настроек разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Permission launcher result: ${result.resultCode}")
        scope.launch {
            delay(500)
            if (canInstallApks(context)) {
                Log.d(TAG, "Permission granted, will check on resume")
                // Флаг установлен, загрузка начнется в repeatOnLifecycle
            } else {
                Log.d(TAG, "Permission still not granted")
                pendingInstall = false
            }
        }
    }

    // Launcher для настроек приложений (удаление)
    val appSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        Log.d(TAG, "Returned from app settings")
    }

    // Отслеживаем жизненный цикл для проверки разрешений и установки
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Если есть скачанный файл и получено разрешение - устанавливаем
            if (downloadedApkFile != null && canInstallApks(context)) {
                Log.d(TAG, "App resumed with downloaded APK and permission, installing")
                val apkFile = downloadedApkFile
                downloadedApkFile = null

                if (apkFile != null && apkFile.exists()) {
                    delay(300)
                    installApk(context, apkFile)
                }
            }
            // Если ожидается загрузка и есть разрешение - загружаем
            else if (pendingInstall && canInstallApks(context) && actualVersion.isNotEmpty()) {
                Log.d(TAG, "App resumed with permission, starting download")
                pendingInstall = false
                isDownloading = true
                downloadProgress = 0f
                errorMessage = ""

                scope.launch {
                    val apkFile = getApkFile(context, actualVersion)
                    val success = downloadApk(downloadUrl, apkFile, client) { progress ->
                        downloadProgress = progress
                    }

                    isDownloading = false

                    if (success && apkFile.exists()) {
                        Log.d(TAG, "Download successful, installing APK")
                        delay(500)
                        val installed = installApk(context, apkFile)
                        if (!installed) {
                            Log.e(TAG, "Installation failed")
                            errorMessage = "Ошибка при установке обновления"
                            showSignatureDialog = true
                        }
                    } else {
                        Log.e(TAG, "Download failed")
                        errorMessage = "Ошибка при скачивании обновления"
                        showSignatureDialog = true
                    }
                }
            }
        }
    }

    // Проверяем обновления при инициализации
    LaunchedEffect(Unit) {
        Log.d(TAG, "Checking for updates. Current version: $currentVersion")
        checkForUpdates(currentVersion, client) { version, url ->
            Log.d(TAG, "Update available: $version")
            actualVersion = version
            downloadUrl = url
            showUpdateDialog = true
        }
    }

    // Диалог конфликта подписей
    if (showSignatureDialog) {
        AlertDialog(
            onDismissRequest = { showSignatureDialog = false },
            title = { Text(text = if (errorMessage.isNotEmpty()) "Ошибка обновления" else "Конфликт подписей") },
            text = {
                Text(text = if (errorMessage.isNotEmpty()) {
                    "$errorMessage\n\nВозможные причины:\n• Конфликт подписей приложения\n• Проблемы с загрузкой\n• Недостаточно места на устройстве\n\nДля устранения конфликта подписей необходимо удалить текущую версию приложения и установить новую."
                } else {
                    "Для установки обновления необходимо сначала удалить текущую версию приложения, так как подписи различаются.\n\n" +
                            "1. Откройте настройки приложения\n" +
                            "2. Нажмите 'Удалить'\n" +
                            "3. Вернитесь и повторите обновление\n\n" +
                            "Ваши данные будут сохранены."
                })
            },
            confirmButton = {
                Button(onClick = {
                    openAppSettings(context, appSettingsLauncher)
                    showSignatureDialog = false
                }) {
                    Text(text = "Открыть настройки")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showSignatureDialog = false
                    errorMessage = ""
                }) {
                    Text(text = "Отмена")
                }
            }
        )
    }

    // Диалог для разрешения установки из неизвестных источников
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                pendingInstall = false
            },
            title = { Text(text = "Разрешение требуется") },
            text = { Text(text = "Для установки обновления необходимо разрешить установку из неизвестных источников.\n\nПосле предоставления разрешения вернитесь в приложение, и установка начнется автоматически.") },
            confirmButton = {
                Button(onClick = {
                    pendingInstall = true
                    openInstallPermissionSettings(context, permissionLauncher)
                    showPermissionDialog = false
                }) {
                    Text(text = "Открыть настройки")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    pendingInstall = false
                }) {
                    Text(text = "Отмена")
                }
            }
        )
    }

    // Диалог загрузки
    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(text = "Загрузка обновления") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Загружается версия $actualVersion...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (downloadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Подготовка к загрузке...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Диалог обновления
    // Диалог обновления
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(text = "Доступно обновление") },
            text = {
                Text(
                    text = "Доступна новая версия $actualVersion\n" +
                            "Текущая версия: $currentVersion"
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Основная кнопка — Обновить
                    Button(
                        onClick = {
                            showUpdateDialog = false

                            if (canInstallApks(context)) {
                                // Есть разрешение - скачиваем и устанавливаем
                                isDownloading = true
                                downloadProgress = 0f
                                errorMessage = ""

                                scope.launch {
                                    val apkFile = getApkFile(context, actualVersion)
                                    val success = downloadApk(downloadUrl, apkFile, client) { progress ->
                                        downloadProgress = progress
                                    }

                                    isDownloading = false

                                    if (success && apkFile.exists()) {
                                        Log.d(TAG, "Download successful, installing APK")
                                        delay(500)
                                        val installed = installApk(context, apkFile)
                                        if (!installed) {
                                            Log.e(TAG, "Installation failed, might be signature conflict")
                                            errorMessage = "Не удалось установить обновление"
                                            showSignatureDialog = true
                                        }
                                    } else {
                                        Log.e(TAG, "Download failed")
                                        errorMessage = "Не удалось загрузить обновление"
                                        showSignatureDialog = true
                                    }
                                }
                            } else {
                                // Нет разрешения - запрашиваем
                                showPermissionDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Обновить")
                    }

                    // Второстепенная кнопка — Позже
                    OutlinedButton(
                        onClick = { showUpdateDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Позже")
                    }
                }
            },
            dismissButton = {}
        )
    }
}

private fun canInstallApks(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

private fun openInstallPermissionSettings(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launcher.launch(intent)
    }
}

private fun openAppSettings(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    launcher.launch(intent)
}

private suspend fun downloadApk(
    url: String,
    outputFile: File,
    client: OkHttpClient,
    onProgress: (Float) -> Unit = {}
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download from: $url")

            // Удаляем старый файл если существует
            if (outputFile.exists()) {
                outputFile.delete()
                Log.d(TAG, "Deleted existing APK file")
            }

            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", "HDRZK-AutoUpdater/1.0")
                .addHeader("Accept", "application/vnd.android.package-archive")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: ${response.code} ${response.message}")
                    return@withContext false
                }

                val contentLength = response.body?.contentLength() ?: -1
                val inputStream = response.body?.byteStream()
                    ?: return@withContext false

                inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        Log.d(TAG, "Downloading APK, size: $contentLength bytes")

                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            if (contentLength > 0) {
                                val progress = downloaded.toFloat() / contentLength.toFloat()
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            Log.d(TAG, "Download completed. File size: ${outputFile.length()} bytes")

            if (outputFile.length() < 1024 * 1024) { // Менее 1MB - подозрительно
                Log.e(TAG, "Downloaded file too small: ${outputFile.length()} bytes")
                return@withContext false
            }

            if (!isValidApk(outputFile)) {
                Log.e(TAG, "Downloaded file is not a valid APK")
                return@withContext false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            false
        }
    }
}

private fun isValidApk(file: File): Boolean {
    return try {
        // Проверяем ZIP signature (APK это ZIP файл)
        val bytes = ByteArray(4)
        file.inputStream().use { it.read(bytes) }

        // ZIP файл начинается с "PK"
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte()) &&
                file.length() > 0
    } catch (e: Exception) {
        Log.e(TAG, "Error validating APK", e)
        false
    }
}

private fun installApk(context: Context, apkFile: File): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.applicationContext.packageName}.provider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }

        Log.d(TAG, "Starting installation of: ${apkFile.absolutePath}")
        Log.d(TAG, "File size: ${apkFile.length()} bytes")

        // Проверяем что можем запустить Intent
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else {
            Log.e(TAG, "No activity found to handle APK installation")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error installing APK", e)
        false
    }
}

private fun getApkFile(context: Context, version: String): File {
    // Используем внешний кэш если доступен, иначе внутренний
    val cacheDir = context.externalCacheDir ?: context.cacheDir
    return File(cacheDir, "hdrzk-$version.apk")
}

suspend fun checkForUpdates(
    currentVersion: String,
    client: OkHttpClient,
    onUpdateAvailable: (String, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates from GitHub API")

            val request = okhttp3.Request.Builder()
                .url("https://api.github.com/repos/Hournet/HDRZK/releases/latest")
                .addHeader("User-Agent", "HDRZK-AutoUpdater/1.0")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch latest release: ${response.code}")
                    return@withContext
                }

                val body = response.body?.string()
                Log.d(TAG, "GitHub API response received")

                if (body != null) {
                    val json = JSONObject(body)
                    val tagName = json.getString("tag_name")
                    val actualVersion = tagName.removePrefix("v")

                    Log.d(TAG, "Latest release version: $actualVersion")
                    Log.d(TAG, "Current version: $currentVersion")

                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""

                    // Ищем APK файл в релизе
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        Log.d(TAG, "Found asset: $name")

                        // Ищем APK файл (может быть app-release.apk или hdrzk-release.apk)
                        if (name.endsWith(".apk") && (name.contains("release") || name.contains("hdrzk"))) {
                            downloadUrl = asset.getString("browser_download_url")
                            Log.d(TAG, "Selected APK: $name")
                            break
                        }
                    }

                    Log.d(TAG, "Download URL: $downloadUrl")

                    withContext(Dispatchers.Main) {
                        if (actualVersion.isNotEmpty() &&
                            downloadUrl.isNotEmpty() &&
                            isVersionNewer(currentVersion, actualVersion)) {
                            Log.d(TAG, "Update available: $actualVersion (current: $currentVersion)")
                            onUpdateAvailable(actualVersion, downloadUrl)
                        } else {
                            Log.d(TAG, "No update available or same/older version")
                        }
                    }
                } else {
                    Log.e(TAG, "Empty response body")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
    }
}

private fun isVersionNewer(current: String, new: String): Boolean {
    return try {
        // Парсим версии как семантические (major.minor.patch)
        val currentParts = current.split(".").map {
            it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        }
        val newParts = new.split(".").map {
            it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        }

        val maxLength = maxOf(currentParts.size, newParts.size)

        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrNull(i) ?: 0
            val newPart = newParts.getOrNull(i) ?: 0

            when {
                newPart > currentPart -> {
                    Log.d(TAG, "Version comparison: $new > $current (part $i: $newPart > $currentPart)")
                    return true
                }
                newPart < currentPart -> {
                    Log.d(TAG, "Version comparison: $new < $current (part $i: $newPart < $currentPart)")
                    return false
                }
            }
        }

        Log.d(TAG, "Version comparison: $new == $current")
        false
    } catch (e: Exception) {
        Log.e(TAG, "Error comparing versions: $current vs $new", e)
        // Если не можем сравнить, считаем что версии разные
        current != new
    }
}


@Composable
fun UpdateDialogPreview() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "Доступно обновление") },
        text = {
            Text(
                text = "Доступна новая версия 1\n" +
                        "Текущая версия: 2"
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Основная кнопка
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Обновить")
                }

                // Второстепенная кнопка
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Позже")
                }
            }
        },
        dismissButton = {}
    )
}


@Preview(showBackground = true)
@Composable
fun PreviewUpdateDialog() {
    UpdateDialogPreview()
}