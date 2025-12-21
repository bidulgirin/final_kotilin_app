// 오디오 데이터 로딩
package com.final_pj.voice.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import com.final_pj.voice.model.AudioItem

class AudioRepository(
    private val contentResolver: ContentResolver
) {
    fun loadAudioFiles(): List<AudioItem> {
        Log.d("test", "저장소에 갈래")
        val audioList = mutableListOf<AudioItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_RECORDING} = 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)

                audioList.add(
                    AudioItem(
                        id = id,
                        title = cursor.getString(titleCol) ?: "",
                        displayName = cursor.getString(nameCol) ?: "",
                        duration = cursor.getLong(durationCol),
                        uri = uri
                    )
                )
            }
        }

        return audioList
    }
}