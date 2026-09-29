package com.example.zkp_android_spartan

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.GZIPOutputStream

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

/**
 * Copies a circuit directory from assets to the local filesystem.
 *
 * In addition to the recursive copy performed by [copyAssetDirToFiles], this
 * function explicitly ensures that the circuit artifacts under `target/` are
 * present with the names expected by the Spartan backend. The Android build
 * tools can strip the `.gz` extension from compressed assets (storing a witness
 * as `target/<circuit>` instead of `target/<circuit>.gz`), so the recursive
 * copy alone may leave the prover unable to find `target/<circuit>.gz`.
 *
 * The Rust-produced `target/precompute.bin` artifact (written by
 * `precomputeProof`) is not shipped in assets, and the recursive copy deletes
 * the whole circuit directory. If such a file exists at [destDir], it is backed
 * up before the copy and restored afterwards so that the path returned by
 * `precomputeProof` stays valid for `generateProofPrecomputed`.
 *
 * @param context Android context, used to access the AssetManager.
 * @param assetPath Path inside assets to the circuit directory,
 *                  e.g. "spartan-backend/circuits/c0000_trivial".
 * @param destDir Destination directory on the filesystem.
 * @throws IOException if the asset cannot be read or the destination cannot be written.
 */
fun copyCircuitToFiles(context: Context, assetPath: String, destDir: File) {
    val targetDir = File(destDir, "target")
    val precomputeFile = File(targetDir, "precompute.bin")
    var backup: File? = null
    if (precomputeFile.exists()) {
        backup = File.createTempFile("precompute", ".bin", context.cacheDir)
        precomputeFile.copyTo(backup, overwrite = true)
    }

    try {
        copyAssetDirToFiles(context, assetPath, destDir)

        val circuitName = destDir.name
        val targetAssetPath = "$assetPath/target"

        listOf(
            "$circuitName.json" to File(targetDir, "$circuitName.json"),
            "$circuitName.gz" to File(targetDir, "$circuitName.gz")
        ).forEach { (fileName, fileDest) ->
            if (fileDest.exists()) return@forEach

            fileDest.parentFile?.mkdirs()

            // The Android build tools may strip the `.gz` extension from compressed
            // assets, storing the witness file as `target/<circuit>` instead of
            // `target/<circuit>.gz`. Try the canonical name first, then the fallback.
            val assetNames = if (fileName.endsWith(".gz")) {
                listOf(fileName, fileName.removeSuffix(".gz"))
            } else {
                listOf(fileName)
            }

            var copied = false
            for (assetName in assetNames) {
                try {
                    context.assets.open("$targetAssetPath/$assetName").use { input ->
                        fileDest.outputStream().use { output ->
                            // AAPT does not merely strip the `.gz` extension: it also
                            // gunzips the asset, so the extension-less fallback holds
                            // the raw, decompressed witness. When we take that fallback
                            // for a `.gz` target, re-gzip it so the file written as
                            // `<circuit>.gz` is valid gzip that the Spartan prover can
                            // decompress. The canonical `.gz` asset (if AAPT ever
                            // leaves one) is copied through unchanged.
                            if (fileName.endsWith(".gz") && !assetName.endsWith(".gz")) {
                                GZIPOutputStream(output).use { gz -> input.copyTo(gz) }
                            } else {
                                input.copyTo(output)
                            }
                        }
                    }
                    copied = true
                    break
                } catch (_: FileNotFoundException) {
                    // Try the next candidate name.
                }
            }

            if (!copied) {
                throw FileNotFoundException("$targetAssetPath/$fileName")
            }
        }

        backup?.let {
            targetDir.mkdirs()
            it.copyTo(File(targetDir, "precompute.bin"), overwrite = true)
        }
    } finally {
        backup?.delete()
    }
}
