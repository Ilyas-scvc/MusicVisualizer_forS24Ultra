package com.musicedge.visualizer.media

import android.content.Context
import android.content.pm.PackageManager
import android.util.ArrayMap

/**
 * Resolves human-readable labels for the package that owns the active media
 * session. Package visibility is granted by the scoped `<queries>` block in the
 * manifest, so unknown packages are simply reported by their package name.
 */
class MusicAppDetector(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
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
}
