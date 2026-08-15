package com.asdk.tools.wpstatussaver.util

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.asdk.tools.wpstatussaver.R
import com.asdk.tools.wpstatussaver.model.AppType
import com.asdk.tools.wpstatussaver.model.SaveLocation
import com.asdk.tools.wpstatussaver.model.StatusMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object StorageHelper {

    private const val PREFS_NAME = "wp_status_saver_prefs"
    private const val KEY_SAF_URI_PREFIX = "saf_tree_uri_"
    private const val SAVED_FOLDER_NAME = "WPStatusSaver"

    fun isSafRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun getLegacyPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun isPermissionGranted(context: Context, appType: AppType): Boolean {
        return if (isSafRequired()) {
            hasPersistedSafPermission(context, appType)
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasPersistedSafPermission(context: Context, appType: AppType): Boolean {
        val uri = getSavedSafTreeUri(context, appType) ?: return false
        val persistedPermissions = context.contentResolver.persistedUriPermissions
        return persistedPermissions.any { it.uri == uri && it.isReadPermission }
    }

    fun getSavedSafTreeUri(context: Context, appType: AppType): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_SAF_URI_PREFIX + appType.name, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSafTreeUri(context: Context, appType: AppType, uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SAF_URI_PREFIX + appType.name, uri.toString()).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearSafTreeUri(context: Context, appType: AppType) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SAF_URI_PREFIX + appType.name).apply()
    }

    fun createSafIntent(appType: AppType): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val encodedPath = Uri.encode(appType.scopedMediaDirName)
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encodedPath")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        }
        return intent
    }

    suspend fun loadStatuses(context: Context, appType: AppType): List<StatusMedia> = withContext(Dispatchers.IO) {
        val statuses = mutableListOf<StatusMedia>()
        try {
            if (isSafRequired()) {
                val treeUri = getSavedSafTreeUri(context, appType) ?: return@withContext emptyList()
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
                
                val targetDoc = if (rootDoc.name == ".Statuses" || rootDoc.name == "Media") {
                    if (rootDoc.name == "Media") {
                        rootDoc.findFile(".Statuses") ?: rootDoc
                    } else {
                        rootDoc
                    }
                } else {
                    rootDoc.findFile(".Statuses") ?: rootDoc
                }

                for (doc in targetDoc.listFiles()) {
                    if (doc.isFile && isValidMedia(doc.name)) {
                        val isVideo = isVideoFile(doc.name)
                        statuses.add(
                            StatusMedia(
                                uri = doc.uri,
                                title = doc.name ?: "Status",
                                path = doc.uri.toString(),
                                isVideo = isVideo,
                                dateModified = doc.lastModified(),
                                size = doc.length(),
                                isSaved = isMediaSaved(context, doc.name ?: ""),
                                appType = appType
                            )
                        )
                    }
                }
            } else {
                val paths = listOf(
                    File(Environment.getExternalStorageDirectory(), appType.legacyRelativePath),
                    File(Environment.getExternalStorageDirectory(), appType.scopedMediaDirName)
                )

                val uniqueFiles = mutableSetOf<String>()
                for (dir in paths) {
                    if (dir.exists() && dir.isDirectory) {
                        val files = dir.listFiles() ?: continue
                        for (file in files) {
                            if (file.isFile && isValidMedia(file.name) && uniqueFiles.add(file.name)) {
                                val isVideo = isVideoFile(file.name)
                                statuses.add(
                                    StatusMedia(
                                        uri = Uri.fromFile(file),
                                        title = file.name,
                                        path = file.absolutePath,
                                        isVideo = isVideo,
                                        dateModified = file.lastModified(),
                                        size = file.length(),
                                        isSaved = isMediaSaved(context, file.name),
                                        file = file,
                                        appType = appType
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        statuses.sortedByDescending { it.dateModified }
    }

    private fun isValidMedia(name: String?): Boolean {
        if (name.isNullOrEmpty() || name.startsWith(".nomedia")) return false
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".gif")
    }

    private fun isVideoFile(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".mkv") || lower.endsWith(".webm")
    }

    private fun isMediaSaved(context: Context, fileName: String): Boolean {
        if (fileName.isEmpty()) return false
        val dirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVED_FOLDER_NAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), SAVED_FOLDER_NAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), SAVED_FOLDER_NAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SAVED_FOLDER_NAME)
        )

        return dirs.any { File(it, fileName).exists() }
    }

    fun requestSaveWithLocationChoice(
        context: Context,
        status: StatusMedia,
        onLocationChosen: (com.asdk.tools.wpstatussaver.model.SaveLocation) -> Unit
    ) {
        if (!SettingsManager.isAskSaveLocation(context)) {
            onLocationChosen(SettingsManager.getDefaultSaveLocation(context))
            return
        }

        val options = arrayOf(
            com.asdk.tools.wpstatussaver.model.SaveLocation.PICTURES.displayName,
            com.asdk.tools.wpstatussaver.model.SaveLocation.DCIM.displayName,
            com.asdk.tools.wpstatussaver.model.SaveLocation.DOWNLOADS.displayName
        )

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Save where?")
            .setItems(options) { _, which ->
                val selected = when (which) {
                    1 -> com.asdk.tools.wpstatussaver.model.SaveLocation.DCIM
                    2 -> com.asdk.tools.wpstatussaver.model.SaveLocation.DOWNLOADS
                    else -> com.asdk.tools.wpstatussaver.model.SaveLocation.PICTURES
                }
                onLocationChosen(selected)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    suspend fun saveMedia(
        context: Context,
        status: StatusMedia,
        location: com.asdk.tools.wpstatussaver.model.SaveLocation = SettingsManager.getDefaultSaveLocation(context)
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = if (status.title.isNotBlank()) status.title else "status_${System.currentTimeMillis()}.${if (status.isVideo) "mp4" else "jpg"}"
            val mimeType = if (status.isVideo) "video/mp4" else "image/jpeg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, location.getRelativePath(status.isVideo))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collectionUri = if (status.isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val itemUri = context.contentResolver.insert(collectionUri, contentValues) ?: return@withContext false

                var success = false
                context.contentResolver.openInputStream(status.uri)?.use { inputStream ->
                    context.contentResolver.openOutputStream(itemUri)?.use { outputStream ->
                        copyStream(inputStream, outputStream)
                        success = true
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(itemUri, contentValues, null, null)
                return@withContext success
            } else {
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(location.getPublicDirectory(status.isVideo)),
                    SAVED_FOLDER_NAME
                )
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val destFile = File(targetDir, fileName)
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null
                val targetFile = status.file
                try {
                    inputStream = if (targetFile != null && targetFile.exists()) {
                        FileInputStream(targetFile)
                    } else {
                        context.contentResolver.openInputStream(status.uri)
                    }
                    outputStream = FileOutputStream(destFile)
                    if (inputStream != null) {
                        copyStream(inputStream, outputStream)
                    }
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadSavedMedia(context: Context): List<StatusMedia> = withContext(Dispatchers.IO) {
        val savedList = mutableListOf<StatusMedia>()
        val seenNames = mutableSetOf<String>()

        try {
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVED_FOLDER_NAME)
            val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), SAVED_FOLDER_NAME)
            val dcimDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), SAVED_FOLDER_NAME)
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SAVED_FOLDER_NAME)

            val directories = listOf(picturesDir, moviesDir, dcimDir, downloadDir)
            for (dir in directories) {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        if (file.isFile && isValidMedia(file.name) && seenNames.add(file.name)) {
                            val isVideo = isVideoFile(file.name)
                            savedList.add(
                                StatusMedia(
                                    uri = getShareableUri(context, file),
                                    title = file.name,
                                    path = file.absolutePath,
                                    isVideo = isVideo,
                                    dateModified = file.lastModified(),
                                    size = file.length(),
                                    isSaved = true,
                                    file = file
                                )
                            )
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                queryMediaStore(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, savedList, seenNames)
                queryMediaStore(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, savedList, seenNames)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        savedList.sortedByDescending { it.dateModified }
    }

    private fun queryMediaStore(
        context: Context,
        collectionUri: Uri,
        isVideo: Boolean,
        list: MutableList<StatusMedia>,
        seenNames: MutableSet<String>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$SAVED_FOLDER_NAME%")

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: "media"
                    if (seenNames.add(name)) {
                        val id = cursor.getLong(idCol)
                        val uri = Uri.withAppendedPath(collectionUri, id.toString())
                        val date = cursor.getLong(dateCol) * 1000L
                        val size = cursor.getLong(sizeCol)

                        list.add(
                            StatusMedia(
                                uri = uri,
                                title = name,
                                path = uri.toString(),
                                isVideo = isVideo,
                                dateModified = date,
                                size = size,
                                isSaved = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteSavedMedia(context: Context, status: StatusMedia): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetFile = status.file
            if (targetFile != null && targetFile.exists()) {
                val deleted = targetFile.delete()
                if (deleted) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        null,
                        null
                    )
                }
                return@withContext deleted
            }

            if (status.uri.scheme == "content") {
                val deletedRows = context.contentResolver.delete(status.uri, null, null)
                return@withContext deletedRows > 0
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getShareableUri(context: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    suspend fun getShareableUri(context: Context, status: StatusMedia): Uri = withContext(Dispatchers.IO) {
        val targetFile = status.file
        if (targetFile != null && targetFile.exists()) {
            return@withContext getShareableUri(context, targetFile)
        }
        if (status.uri.scheme == "file") {
            val file = File(status.uri.path ?: "")
            if (file.exists()) {
                return@withContext getShareableUri(context, file)
            }
        }

        try {
            val cacheFile = File(context.cacheDir, "share_${status.title}")
            context.contentResolver.openInputStream(status.uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    copyStream(input, output)
                }
            }
            return@withContext getShareableUri(context, cacheFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext status.uri
        }
    }

    suspend fun saveMultipleMedia(
        context: Context,
        items: List<StatusMedia>,
        location: SaveLocation = SettingsManager.getDefaultSaveLocation(context)
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (item in items) {
            if (saveMedia(context, item, location)) {
                item.isSaved = true
                count++
            }
        }
        count
    }

    suspend fun deleteMultipleSavedMedia(context: Context, items: List<StatusMedia>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (item in items) {
            if (deleteSavedMedia(context, item)) {
                count++
            }
        }
        count
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }
}
