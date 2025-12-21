package com.final_pj.voice

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.adapter.AudioAdapter
import com.final_pj.voice.model.AudioItem
import com.final_pj.voice.repository.AudioRepository
import com.final_pj.voice.service.CallDetectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    // ----------------------------
    // 포그라운드서비스 관련
    // ----------------------------
    private fun startForegroundService() {
        val intent = Intent(this, CallDetectService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
    private fun showAudioList(){
        // 화면 구성
        // 저장소에서 가져와
        audioRepository = AudioRepository(contentResolver)

        audioAdapter = AudioAdapter(emptyList()) {
            playAudio(it)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = audioAdapter

        loadAudio()
    }
    // ----------------------------
    // 녹음 예제 (테스트용)
    // ----------------------------
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var outputFile: String

    private fun startRecording() {
        outputFile = "${externalCacheDir?.absolutePath}/voice_${System.currentTimeMillis()}"
        //val outputFile = File(getExternalFilesDir("Recordings/Call"), "recording_${System.currentTimeMillis()}")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile)
            prepare()
            start()
        }
        isRecording = true
        findViewById<Button>(R.id.btnRecord).text = "녹음 끝"
        Toast.makeText(this, "녹음 시작", Toast.LENGTH_SHORT).show()
    }
    private fun saveToMediaStore(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_RECORDING, 0) // 0: 녹음 파일
        }

        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
            val inputStream = FileInputStream(file)
            inputStream.copyTo(outputStream!!)
            inputStream.close()
            outputStream.close()
        }
    }
    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null

        isRecording = false
        findViewById<Button>(R.id.btnRecord).text = "녹음 시작"
        Toast.makeText(this, "녹음 완료: $outputFile", Toast.LENGTH_SHORT).show()

        // MediaStore에 등록
        val file = File(outputFile)
        saveToMediaStore(file)

    }

    private val REQUEST_PERMISSION_CODE = 1000

    // ----------------------------
    // 오디오 관련
    // ----------------------------
    private lateinit var audioAdapter: AudioAdapter
    private lateinit var audioRepository: AudioRepository // 저장소에서 오디오 가져오기
    private fun loadAudio() {
        Log.d("test", "오디오 로드 시도")
        lifecycleScope.launch(Dispatchers.IO) {
            val list = audioRepository.loadAudioFiles()
            withContext(Dispatchers.Main) {
                audioAdapter.submitList(list)
            }
        }
    }

    private fun playAudio(item: AudioItem) {
        MediaPlayer().apply {
            setDataSource(this@MainActivity, item.uri)
            prepare()
            start()
        }
    }

    // ----------------------------
    // 권한 관련
    // ----------------------------
    private fun checkAndRequestPermissions() {
        // 모든 권한이 있다면
        if (hasRequiredPermissions()) {
            // 포그라운드 서비스 시작
            startForegroundService()
            // 오디오 파일 가져오기
            showAudioList()
            // 녹음 토클 버튼
            val btnRecord: Button = findViewById(R.id.btnRecord)
            btnRecord.setOnClickListener {
                if (isRecording) stopRecording() else startRecording()
            }
        } else {
            // 권한 요청을 해라
            requestRequiredPermissions()
        }
    }


    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_AUDIO,

        )

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_PHONE_STATE, // 통화상태감지
                Manifest.permission.RECORD_AUDIO, // 오디오 녹음 권한
                Manifest.permission.POST_NOTIFICATIONS, // 알림권한
                Manifest.permission.READ_MEDIA_AUDIO, // 외부저장소  READ_EXTERNAL_STORAGE 1ㅡ3이하
            ),
            REQUEST_PERMISSION_CODE
        )
    }

    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSION_CODE) return

        if (grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            // 포그라운드 서비스 시작
            startForegroundService()
            showAudioList()
        } else {
            Toast.makeText(
                this,
                "통화 감지 기능을 사용하려면 권한이 필요합니다.\\n권한을 허용하지 않으면 앱이 종료됩니다.",
                Toast.LENGTH_LONG
            ).show()

            finishAffinity() // 앱의 모든 Activity 종료
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("test", "mainActivity 발동")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 권한 요청
        checkAndRequestPermissions()


        
    }
    companion object {
        private const val REQUEST_PERMISSION_CODE = 1001
    }
}


