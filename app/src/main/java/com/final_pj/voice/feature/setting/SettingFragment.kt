package com.final_pj.voice.feature.setting

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.fragment.app.Fragment
import com.final_pj.voice.R
/*
* todo
* 내가 할 일 : 최신 코드랑 setting 설정해 둔거 잘...머지해서 안올라간 이유 알기
* 최소한의 코드만 건드릴것임 conflict 나면 정신이 없기 때문에...
*
* 브랜치 정리 - 설정에 들어가야할 기능 정리
*
* 1. 녹음 on/off 기능
* :::::녹음을 해야 요약등이 나오기때문에 off 하면 녹음된 파일을 자동삭제하는 방향으로 가야함
* 2. 요약 기능 on/off
* 3. 다크 모드 on/off
* 4. 차단 목록은 만들어 둠
* 5. 알림 기능 on/off => 실시간으로 감시하다가 임계값이 0.5 넘어가면 딥보이스 사용중인것같다 알려주는 알림 제어
* */
class SettingFragment : Fragment() {

    private lateinit var switchNotifications: Switch // 알림 기능 on/off
    private lateinit var switchDarkMode: Switch // 다크 모드
    private lateinit var swichSummaryMode: Switch // 요약 on/off
    private lateinit var SwitchRecord: Switch // 녹음 on/off

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        switchNotifications = view.findViewById(R.id.switch_notifications)
        switchDarkMode = view.findViewById(R.id.switch_dark_mode)
        SwitchRecord = view.findViewById(R.id.switch_record_mode)
        swichSummaryMode = view.findViewById(R.id.switch_summury_mode)


        // 기존 설정 불러오기
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        switchNotifications.isChecked = prefs.getBoolean("notifications", false)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)

        SwitchRecord.isChecked = prefs.getBoolean("record", true) // 기본값 true
        swichSummaryMode.isChecked = prefs.getBoolean("summary", true) // 기본값 false

        // 토글 상태 변경시 저장
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
        }
        SwitchRecord.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("record", isChecked).apply()
        }

        swichSummaryMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("summary", isChecked).apply()
        }
    }
}