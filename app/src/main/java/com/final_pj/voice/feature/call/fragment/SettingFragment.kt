package com.final_pj.voice.feature.call.fragment

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.R
class SettingFragment : Fragment() {

    private lateinit var switchNotifications: Switch
    private lateinit var switchDarkMode: Switch

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        switchNotifications = view.findViewById(R.id.switch_notifications)
        switchDarkMode = view.findViewById(R.id.switch_dark_mode)

        // 기존 설정 불러오기
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        switchNotifications.isChecked = prefs.getBoolean("notifications", true)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)

        // 토글 상태 변경시 저장
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            // 필요하면 테마 적용 로직 추가
        }

        // 차단목록 페이지 이동
        val btnBlockList = view.findViewById<View>(R.id.btn_block_list)
        btnBlockList.setOnClickListener {
            findNavController().navigate(R.id.action_setting_to_blockList)
        }
    }
}
