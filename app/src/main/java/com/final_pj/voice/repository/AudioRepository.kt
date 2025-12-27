// 오디오 데이터 로딩
package com.final_pj.voice.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.final_pj.voice.model.AudioItem
class AudioRepository(private val context: Context) {

    fun loadAudioFiles(): List<AudioItem> {
        val audioList = mutableListOf<AudioItem>()

        // 내부 저장소 녹음 파일만 조회
        val files = context.filesDir.listFiles()?.filter { it.extension in listOf("mp4", "mp3", "3gp") }
        files?.forEach { file ->
            audioList.add(
                AudioItem(
                    id = -1L,
                    title = file.nameWithoutExtension,
                    displayName = file.name,
                    duration = 0L, // 필요시 MediaPlayer로 구할 수 있음
                    path = file.absolutePath,
                    uri = Uri.fromFile(file)
                )
            )
        }

        return audioList
    }
}

