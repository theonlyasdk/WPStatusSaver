package com.asdk.tools.wpstatussaver.model

enum class AppType(
    val title: String,
    val packageName: String,
    val legacyRelativePath: String,
    val scopedMediaDirName: String
) {
    WHATSAPP(
        title = "WhatsApp",
        packageName = "com.whatsapp",
        legacyRelativePath = "WhatsApp/Media/.Statuses",
        scopedMediaDirName = "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    ),
    WHATSAPP_BUSINESS(
        title = "WA Business",
        packageName = "com.whatsapp.w4b",
        legacyRelativePath = "WhatsApp Business/Media/.Statuses",
        scopedMediaDirName = "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    )
}
