// 오디오 데이터 로딩
package com.final_pj.voice.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.final_pj.voice.model.AudioItem
import android.media.MediaMetadataRetriever
import java.io.File

class AudioRepository(private val context: Context) {

    fun loadAudioFiles(): List<AudioItem> {
        val audioList = mutableListOf<AudioItem>()

        // ✅ 통화 녹음이 저장된 경로
        val dir: File = context.getExternalFilesDir(null) ?: return emptyList()
        Log.d("여기있니????", "${dir}")

        val files = dir.listFiles()?.filter {
            it.extension.lowercase() in listOf("m4a", "mp4", "mp3", "3gp")
        } ?: emptyList()

        files.forEach { file ->
            audioList.add(
                AudioItem(
                    id = file.lastModified(), // 임시 ID
                    title = file.nameWithoutExtension,
                    displayName = file.name,
                    duration = getDuration(file.absolutePath),
                    path = file.absolutePath,
                    uri = Uri.fromFile(file)
                )
            )
        }

        // 최신 녹음이 위로
        return audioList.sortedByDescending { it.id }
    }

    private fun getDuration(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}


