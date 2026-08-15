package com.asdk.tools.wpstatussaver.model

import android.net.Uri
import java.io.File
import java.io.Serializable

data class StatusMedia(
    val uriString: String,
    val title: String,
    val path: String,
    val isVideo: Boolean,
    val dateModified: Long,
    val size: Long,
    var isSaved: Boolean = false,
    val appType: AppType = AppType.WHATSAPP
) : Serializable {

    val uri: Uri
        get() = Uri.parse(uriString)

    val file: File?
        get() = if (path.isNotBlank() && !path.startsWith("content:")) File(path) else null

    constructor(
        uri: Uri,
        title: String,
        path: String,
        isVideo: Boolean,
        dateModified: Long,
        size: Long,
        isSaved: Boolean = false,
        file: File? = null,
        appType: AppType = AppType.WHATSAPP
    ) : this(
        uriString = uri.toString(),
        title = title,
        path = path,
        isVideo = isVideo,
        dateModified = dateModified,
        size = size,
        isSaved = isSaved,
        appType = appType
    )
}
