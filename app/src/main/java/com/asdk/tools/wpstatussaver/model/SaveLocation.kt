package com.asdk.tools.wpstatussaver.model

import android.os.Environment

enum class SaveLocation(val displayName: String, val dirName: String) {
    PICTURES("Photos", Environment.DIRECTORY_PICTURES),
    DCIM("DCIM", Environment.DIRECTORY_DCIM),
    DOWNLOADS("Downloads", Environment.DIRECTORY_DOWNLOADS);

    fun getRelativePath(isVideo: Boolean): String {
        val baseDir = when (this) {
            PICTURES -> if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            DCIM -> Environment.DIRECTORY_DCIM
            DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
        }
        return "$baseDir/WPStatusSaver"
    }

    fun getPublicDirectory(isVideo: Boolean): String {
        return when (this) {
            PICTURES -> if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            DCIM -> Environment.DIRECTORY_DCIM
            DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
        }
    }
}
