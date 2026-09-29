package com.example.zkp_android_spartan

import android.content.Context
import java.io.File
import java.util.zip.GZIPOutputStream

/** directory holding circuit artifacts */
private const val TARGET_DIR = "target"

/**
 * Recursively copies an Android asset to the filesystem.
 *
 * @param context Android context
 * @param asset path to copy
 * @param destDir destination on the filesystem
 * @throws IOException if asset cannot be read or written
 */
fun extractAsset(context: Context, asset: String, destDir: File) {
    val children = context.assets.list(asset) ?: emptyArray()

    if (children.isEmpty()) {   // `asset` points to file
        destDir.parentFile?.mkdirs()
        context.assets.open(asset).use { input ->
            destDir.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } else {    // `asset` points to directory
        destDir.mkdirs()
        for (child in children) {
            extractAsset(context, "$asset/$child", File(destDir, child))
        }
    }
}

/**
 * Copy a circuit directory from the bundled assets into the local
 * filesystem.
 *
 * @param context Android context
 * @param asset path to copy
 * @param destDir destination on the filesystem
 * @throws IOException if the asset cannot be read or written
 */
fun copyCircuitFromAssets(context: Context, asset: String, destDir: File) {
    extractAsset(context, asset, destDir)
    // the backend expects the witness at `target/<circuit>.gz`; if AAPT
    // gunzipped it and stripped the extension (stored as `target/<circuit>`),
    // re-gzip it
    val circuitName = destDir.name
    val fileDest = File(File(destDir, TARGET_DIR), "$circuitName.gz")
    if (!fileDest.exists()) {
        context.assets.open("$asset/$TARGET_DIR/$circuitName").use { input ->
            fileDest.outputStream().use { output ->
                GZIPOutputStream(output).use { gz -> input.copyTo(gz) }
            }
        }
    }
}
