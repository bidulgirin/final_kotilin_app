package com.final_pj.voice.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.final_pj.voice.CallingControlActivity
import com.final_pj.voice.R

class DialerFragment : Fragment(R.layout.fragment_dialer) {

    private lateinit var etPhoneNumber: EditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)

        val buttons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnStar, R.id.btnHash
        )

        buttons.forEach { id ->
            view.findViewById<Button>(id)?.setOnClickListener {
                val number = (it as Button).text.toString()
                appendNumber(number)
            }
        }

        view.findViewById<Button>(R.id.btnCall)?.setOnClickListener {
            val phone = etPhoneNumber.text.toString()
            if (phone.isNotEmpty()) callPhone(phone)
        }

        view.findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            // TODO: 연락처 저장
        }

        view.findViewById<Button>(R.id.btnSearch)?.setOnClickListener {
            // TODO: 연락처 검색
        }

        val btnDelete = view.findViewById<Button>(R.id.btn_delete)
        btnDelete?.setOnClickListener {
            val phone = etPhoneNumber.text.toString()
            if (phone.isNotEmpty()) {
                etPhoneNumber.setText(phone.dropLast(1)) // 마지막 문자 삭제
            }
        }
    }

    private fun appendNumber(number: String) {
        etPhoneNumber.append(number)
    }

    // 전화 거는 화면으로 이동 (발신)
    private fun callPhone(number: String) {
        if (number.isNotEmpty()) {
            val intent = Intent(requireContext(), CallingControlActivity::class.java).apply {
                putExtra("phone_number", number)
                putExtra("is_outgoing", true)   // ⭐ 발신 표시
            }
            startActivity(intent)
        }
    }
}
