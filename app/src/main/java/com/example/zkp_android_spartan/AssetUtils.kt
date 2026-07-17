package com.example.zkp_android_spartan

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Recursively copies an Android asset directory (or file) to the local filesystem.
 *
 * @param context Android context, used to access the AssetManager.
 * @param assetPath Path inside assets to copy, e.g. "spartan-backend/circuits/c0000_trivial".
 * @param destDir Destination directory (or file) on the filesystem.
 * @throws IOException if the asset cannot be read or the destination cannot be written.
 */
fun copyAssetDirToFiles(context: Context, assetPath: String, destDir: File) {
    val assets = context.assets
    val children = assets.list(assetPath) ?: emptyArray()

    if (children.isEmpty()) {
        // `assetPath` points to a file.
        destDir.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            destDir.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } else {
        // `assetPath` points to a directory.
        destDir.deleteRecursively()
        destDir.mkdirs()
        for (child in children) {
            copyAssetDirToFiles(context, "$assetPath/$child", File(destDir, child))
        }
    }
}
