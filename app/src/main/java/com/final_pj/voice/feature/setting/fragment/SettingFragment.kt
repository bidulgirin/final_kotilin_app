package com.final_pj.voice.feature.setting.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.R
import com.final_pj.voice.feature.login.LoginActivity
import com.final_pj.voice.feature.login.TokenStore

class SettingFragment : Fragment() {

    private lateinit var switchNotifications: Switch
    private lateinit var switchDarkMode: Switch
    private lateinit var switchSummaryMode: Switch
    private lateinit var switchRecord: Switch

    // 사용자 정보 표시용
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView

    private lateinit var tokenStore: TokenStore

    companion object SettingKeys {
        const val PREF_NAME = "settings"

        const val NOTIFICATIONS = "notifications"
        const val DARK_MODE = "dark_mode"
        const val RECORD_ENABLED = "record_enabled"
        const val SUMMARY_ENABLED = "summary_enabled"

        // 사용자 정보 키 (로그인 시 저장해두는 값)
        const val USER_NAME = "user_name"
        const val USER_EMAIL = "user_email"
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

        // 사용자 정보 뷰 바인딩
        tvUserName = view.findViewById(R.id.tv_user_name)
        tvUserEmail = view.findViewById(R.id.tv_user_email)

        tokenStore = TokenStore(requireContext())

        val prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 기존 설정 불러오기
        switchNotifications.isChecked = prefs.getBoolean(NOTIFICATIONS, false)
        switchDarkMode.isChecked = prefs.getBoolean(DARK_MODE, false)
        switchRecord.isChecked = prefs.getBoolean(RECORD_ENABLED, true)
        switchSummaryMode.isChecked = prefs.getBoolean(SUMMARY_ENABLED, true)

        // 사용자 정보 불러와 표시 (없으면 "-" 처리)
        val userName = prefs.getString(USER_NAME, null) ?: "-"
        val userEmail = prefs.getString(USER_EMAIL, null) ?: "-"

        tvUserName.text = "이름: $userName"
        tvUserEmail.text = "이메일: $userEmail"

        // 토글 저장 + 다크모드 즉시 반영
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(NOTIFICATIONS, isChecked).apply()
        }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        switchRecord.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(RECORD_ENABLED, isChecked).apply()
        }

        switchSummaryMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(SUMMARY_ENABLED, isChecked).apply()
        }

        // 차단목록 페이지 이동
        view.findViewById<View>(R.id.btn_block_list).setOnClickListener {
            findNavController().navigate(R.id.action_setting_to_blockList)
        }

        // 마이페이지로 이동 버튼
        view.findViewById<Button>(R.id.btn_my_page).setOnClickListener {
            findNavController().navigate(R.id.action_setting_to_myPage)
        }

        // 로그아웃
        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            clearToken()

            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun clearToken() {
        tokenStore.clear()

        // (선택) 로그아웃 시 사용자 정보도 지우고 싶으면:
        // requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        //     .edit().remove(USER_NAME).remove(USER_EMAIL).apply()
    }
}
