package com.final_pj.voice.feature.call.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.lifecycle.lifecycleScope
import com.final_pj.voice.R
import com.final_pj.voice.core.App
import com.final_pj.voice.feature.call.service.MyInCallService
import com.final_pj.voice.feature.report.repository.VoicePhishingRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class IncomingCallActivity : AppCompatActivity() {
    private val REQ_READ_CONTACTS = 1001
    private lateinit var motionLayout: MotionLayout
    private lateinit var tvNumber: TextView
    private lateinit var name: TextView

    private lateinit var phishingBox: View
    private lateinit var tvPhishingDesc: TextView
    private lateinit var tvReportCount: TextView
    private lateinit var btnBlockNow: View

    private lateinit var phishingRepo: VoicePhishingRepository

    private val mainHandler = Handler(Looper.getMainLooper())

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                mainHandler.post { if (!isFinishing && !isDestroyed) finish() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)
        // 필드에 할당 (val 금지!)
        phishingRepo = (application as App).phishingNumber

        motionLayout = findViewById(R.id.callSlideLayout)
        tvNumber = findViewById(R.id.tvNumber)
        name = findViewById(R.id.name)

        phishingBox = findViewById<View>(R.id.phishingBox)
        btnBlockNow = findViewById<View>(R.id.btnBlockNow)
        tvPhishingDesc = findViewById<TextView>(R.id.tvPhishingDesc)
        tvReportCount = findViewById<TextView>(R.id.tvReportCount)

        // 기본 숨김(원하면 XML에 visibility="gone" 줘도 됨)
        phishingBox.visibility = View.GONE

        val number = intent.getStringExtra("phone_number").orEmpty()
        tvNumber.text = number
        loadContactName(number)

        checkPhishing(number)

        btnBlockNow.setOnClickListener {
            blockNow(number)
        }

        motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionCompleted(layout: MotionLayout, currentId: Int) {
                when (currentId) {
                    R.id.accept -> {
                        if (number.isBlank()) {
                            resetSlider()
                            return
                        }
                        MyInCallService.currentCall?.answer(0)

                        startActivity(Intent(this@IncomingCallActivity, CallingActivity::class.java).apply {
                            putExtra("phone_number", number)
                            putExtra("is_outgoing", false)
                        })
                        finish()
                    }

                    R.id.reject -> {
                        MyInCallService.currentCall?.reject(false, null)
                        finish()
                    }
                }
            }

            override fun onTransitionStarted(layout: MotionLayout, startId: Int, endId: Int) {}
            override fun onTransitionChange(layout: MotionLayout, startId: Int, endId: Int, progress: Float) {}
            override fun onTransitionTrigger(layout: MotionLayout, triggerId: Int, positive: Boolean, progress: Float) {}
        })
    }

    override fun onStart() {
        super.onStart()
        MyInCallService.currentCall?.registerCallback(callCallback)
    }

    override fun onStop() {
        MyInCallService.currentCall?.unregisterCallback(callCallback)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_READ_CONTACTS) {
            val number = intent.getStringExtra("phone_number").orEmpty()
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                queryAndShowContactName(number)
            } else {
                name.text = "" // 권한 거부면 이름 표시 불가
            }
        }
    }

    private fun loadContactName(rawNumber: String) {
        if (rawNumber.isBlank()) {
            name.text = ""
            return
        }

        // 권한 있으면 바로 조회
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            queryAndShowContactName(rawNumber)
            return
        }

        // 권한 없으면 요청 (전화 기본앱이라도 런타임은 필요)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CONTACTS),
            REQ_READ_CONTACTS
        )
    }

    private fun queryAndShowContactName(rawNumber: String) {
        lifecycleScope.launch {
            val displayName = withContext(Dispatchers.IO) {
                queryContactNameByPhone(rawNumber)
            }

            name.text = displayName ?: "" // 없으면 빈칸
        }
    }

    private fun queryContactNameByPhone(rawNumber: String): String? {
        // ContactsContract는 다양한 포맷을 처리해주지만,
        // 하이픈/공백 제거해도 좋음
        val number = rawNumber.replace("-", "").replace(" ", "")

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(android.net.Uri.encode(number))
            .build()

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME
        )

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }


    private fun resetSlider() {
        motionLayout.progress = 0f
        try {
            motionLayout.setTransition(R.id.start, R.id.accept)
            motionLayout.transitionToStart()
        } catch (_: Exception) {}
    }

    private fun checkPhishing(rawNumber: String) {
        Log.d("rawNumber", "${rawNumber}")
        if (rawNumber.isBlank()) {
            //phishingBox.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            val hit = try {
                withTimeout(2500L) { phishingRepo.lookupLocal(rawNumber) }
            } catch (_: Exception) {
                null
            }

            if (hit != null) {
                phishingBox.visibility = View.VISIBLE
                tvPhishingDesc.text = hit.description?.takeIf { it.isNotBlank() } ?: "설명 정보 없음"
                tvReportCount.text = "신고 ${hit.reportCount}건"
            } else {
                //phishingBox.visibility = View.GONE
            }
        }
    }

    private fun blockNow(rawNumber: String) {
        MyInCallService.currentCall?.reject(false, null)
        finish()
    }
}
