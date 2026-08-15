package com.asdk.tools.wpstatussaver.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.asdk.tools.wpstatussaver.R
import com.asdk.tools.wpstatussaver.model.AppType
import com.asdk.tools.wpstatussaver.model.StatusMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WhatsAppLauncher {

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getInstalledWhatsAppApps(context: Context): List<AppType> {
        val installed = mutableListOf<AppType>()
        if (isAppInstalled(context, AppType.WHATSAPP.packageName)) {
            installed.add(AppType.WHATSAPP)
        }
        if (isAppInstalled(context, AppType.WHATSAPP_BUSINESS.packageName)) {
            installed.add(AppType.WHATSAPP_BUSINESS)
        }
        return installed
    }

    fun openApp(context: Context, appType: AppType) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(appType.packageName)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            openPlayStore(context, appType.packageName)
        }
    }

    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            context.startActivity(intent)
        }
    }

    fun shareStatus(context: Context, scope: CoroutineScope, status: StatusMedia) {
        scope.launch {
            val shareUri = StorageHelper.getShareableUri(context, status)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (status.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
            }
        }
    }

    fun repostStatus(context: Context, scope: CoroutineScope, status: StatusMedia) {
        scope.launch {
            val shareUri = StorageHelper.getShareableUri(context, status)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (status.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    setPackage(status.appType.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.app_not_installed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun shareMultipleStatuses(context: Context, scope: CoroutineScope, items: List<StatusMedia>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            shareStatus(context, scope, items.first())
            return
        }

        scope.launch {
            val uris = ArrayList<Uri>()
            var hasVideo = false
            var hasImage = false

            for (item in items) {
                uris.add(StorageHelper.getShareableUri(context, item))
                if (item.isVideo) hasVideo = true else hasImage = true
            }

            withContext(Dispatchers.Main) {
                val mimeType = when {
                    hasVideo && !hasImage -> "video/*"
                    hasImage && !hasVideo -> "image/*"
                    else -> "*/*"
                }

                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
            }
        }
    }
}
