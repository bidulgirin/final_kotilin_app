//  모델 복사해서 쓰기
package com.final_pj.voice.core.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetUtils {
    fun copyAssets(context: Context, assetPath: String) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        val outDir = File(context.filesDir, assetPath)
        if (!outDir.exists()) outDir.mkdirs()

        for (file in files) {
            val inPath = if (assetPath.isEmpty()) file else "$assetPath/$file"
            val outFile = File(outDir, file)

            val children = assetManager.list(inPath)
            if (children != null && children.isNotEmpty()) {
                copyAssets(context, inPath)
            } else {
                if (outFile.exists()) continue

                assetManager.open(inPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}


