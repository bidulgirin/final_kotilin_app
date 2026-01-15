package com.final_pj.voice.feature.mypage

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.R

class MyPageFragment : Fragment() {

    companion object {
        private const val PREF_NAME = "settings"
        private const val USER_NAME = "user_name"
        private const val USER_EMAIL = "user_email"
    }

    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_my_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvUserName = view.findViewById(R.id.tv_user_name)
        tvUserEmail = view.findViewById(R.id.tv_user_email)

        // 일단 SharedPreferences에서 사용자 정보 표시 (SettingFragment와 동일한 저장소 가정)
        val prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(USER_NAME, "-") ?: "-"
        val email = prefs.getString(USER_EMAIL, "-") ?: "-"

        tvUserName.text = "이름: $name"
        tvUserEmail.text = "이메일: $email"

        // 설정으로 돌아가기 (뒤로가기와 동일하게 처리)
        view.findViewById<Button>(R.id.btn_back_setting).setOnClickListener {
            findNavController().popBackStack()
        }

        // 프로필 수정 버튼은 틀만 (enabled=false 상태)
        view.findViewById<Button>(R.id.btn_edit_profile).setOnClickListener {
            // TODO: 추후 프로필 수정 화면으로 이동
        }
    }
}
