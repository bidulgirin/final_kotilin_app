package com.final_pj.voice.feature.setting.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.R
import com.final_pj.voice.feature.login.LoginActivity
import com.final_pj.voice.feature.login.TokenStore

class SettingFragment : Fragment() {

    private lateinit var switchNotifications: Switch // 알림 기능 on/off
    private lateinit var switchDarkMode: Switch // 다크 모드
    private lateinit var switchSummaryMode: Switch // 요약 on/off
    private lateinit var switchRecord: Switch // 녹음 on/off

    private lateinit var tokenStore: TokenStore

    // 설정 Key 상수화 (Fragment 안에 두려면 companion object )
    companion object SettingKeys {
        const val PREF_NAME = "settings"

        const val NOTIFICATIONS = "notifications"
        const val DARK_MODE = "dark_mode"
        const val RECORD_ENABLED = "record_enabled"
        const val SUMMARY_ENABLED = "summary_enabled"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        switchNotifications = view.findViewById(R.id.switch_notifications)
        switchDarkMode = view.findViewById(R.id.switch_dark_mode)
        switchRecord = view.findViewById(R.id.switch_record_mode)
        switchSummaryMode = view.findViewById(R.id.switch_summury_mode)
        tokenStore = TokenStore(requireContext())
        // 기존 설정 불러오기 (상수 사용)
        val prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        switchNotifications.isChecked = prefs.getBoolean(NOTIFICATIONS, false)
        switchDarkMode.isChecked = prefs.getBoolean(DARK_MODE, false)

        // "플래그" 기본값들 (원하는 기본 정책대로 바꾸면 됨)
        switchRecord.isChecked = prefs.getBoolean(RECORD_ENABLED, true)
        switchSummaryMode.isChecked = prefs.getBoolean(SUMMARY_ENABLED, true)

        // 토글 변경 시: 저장만(플래그/설정값 기록만) + 다크모드는 즉시 반영
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(NOTIFICATIONS, isChecked).apply()
        }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(DARK_MODE, isChecked).apply()

            // 즉시 테마 반영
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // 녹음: 여기서는 "플래그만" 저장 (삭제/중지 로직은 다른 곳에서)
        switchRecord.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(RECORD_ENABLED, isChecked).apply()
        }

        // 요약: 여기서는 "플래그만" 저장
        switchSummaryMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(SUMMARY_ENABLED, isChecked).apply()
        }

        // 차단목록 페이지 이동
        view.findViewById<View>(R.id.btn_block_list).setOnClickListener {
            findNavController().navigate(R.id.action_setting_to_blockList)
        }


        // 로그아웃 버튼
        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            // 1) 토큰 삭제 (예: SharedPreferences에 저장한 경우)
            clearToken()

            // 2) 로그인 화면으로 이동 (백스택 제거)
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)

            // 3) 현재 Activity 종료
            requireActivity().finish()
        }

    }

    private fun clearToken() {
        tokenStore.clear()
    }
}