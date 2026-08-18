package com.musicedge.visualizer.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.ArrayMap

/** One row of the Music Apps screen. */
data class MediaAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    /** True when the app looks like a music player, or was seen owning a session. */
    val isKnownMediaApp: Boolean,
)

/**
 * Resolves labels and icons, and lists the apps the user can choose from.
 *
 * Package visibility (Android 11+) is granted by the scoped `<queries>` block in the
 * manifest - media browser services, the APP_MUSIC category, apps that can open
 * audio files, and launchable apps. No QUERY_ALL_PACKAGES.
 *
 * Apps that match none of those intent filters but really did own a media session
 * are still listed: their package names are remembered in settings and passed in as
 * [discoveredPackages]. That is how a player that publishes nothing standard still
 * ends up in the list.
 */
class MusicAppDetector(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
    private val ownPackage: String = context.applicationContext.packageName
    private val labelCache = ArrayMap<String, String>()

    fun labelOf(packageName: String): String {
        labelCache[packageName]?.let { return it }
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrElse { packageName }
        labelCache[packageName] = label
        return label
    }

    /**
     * Everything the user may pick from, media players first. Call off the main
     * thread: this queries the package manager and loads icons.
     */
    fun listSelectableApps(
        discoveredPackages: Set<String>,
        allowedPackages: Set<String>,
    ): List<MediaAppInfo> {
        val mediaPackages = LinkedHashSet<String>()
        mediaPackages += queryPackages(Intent(MEDIA_BROWSER_SERVICE_ACTION), services = true)
        mediaPackages += queryPackages(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            services = false,
        )
        mediaPackages += queryPackages(
            Intent(Intent.ACTION_VIEW).setType(AUDIO_MIME_TYPE),
            services = false,
        )
        mediaPackages += discoveredPackages
        mediaPackages += allowedPackages

        val launchable = queryPackages(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            services = false,
        )

        val all = LinkedHashSet<String>().apply {
            addAll(mediaPackages)
            addAll(launchable)
        }

        return all.asSequence()
            .filter { it != ownPackage }
            .mapNotNull { packageName -> appInfoOrNull(packageName, packageName in mediaPackages) }
            .sortedWith(compareByDescending<MediaAppInfo> { it.isKnownMediaApp }.thenBy { it.label.lowercase() })
            .toList()
    }

    private fun appInfoOrNull(packageName: String, isKnownMediaApp: Boolean): MediaAppInfo? {
        val applicationInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return null

        return MediaAppInfo(
            packageName = packageName,
            label = packageManager.getApplicationLabel(applicationInfo).toString(),
            icon = runCatching { packageManager.getApplicationIcon(applicationInfo) }.getOrNull(),
            isKnownMediaApp = isKnownMediaApp,
        )
    }

    private fun queryPackages(intent: Intent, services: Boolean): List<String> {
        val flags = PackageManager.ResolveInfoFlags.of(0L)
        val resolved = runCatching {
            if (services) {
                packageManager.queryIntentServices(intent, flags)
            } else {
                packageManager.queryIntentActivities(intent, flags)
            }
        }.getOrElse { return emptyList() }

        return resolved.mapNotNull { info ->
            info.serviceInfo?.packageName ?: info.activityInfo?.packageName
        }
    }

    private companion object {
        const val MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService"
        const val AUDIO_MIME_TYPE = "audio/*"
    }
}
