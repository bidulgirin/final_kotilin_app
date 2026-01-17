package com.final_pj.voice.feature.call.fragment

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.final_pj.voice.feature.call.activity.CallingActivity
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.model.ContactItem
import android.text.TextWatcher
import android.database.Cursor

class DialerFragment : Fragment(R.layout.fragment_dialer) {

    private lateinit var etPhoneNumber: EditText

    private var pendingSave: Triple<String, String, String>? = null
    private var pendingSearchOpen = false

    private val requestWriteContacts =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingSave?.let { (name, phone, memo) ->
                    saveContact(name, phone, memo)
                }
                pendingSave = null
            } else {
                Toast.makeText(requireContext(), "연락처 저장 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestReadContacts =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (pendingSearchOpen) {
                    pendingSearchOpen = false
                    showSearchDialog()
                }
            } else {
                Toast.makeText(requireContext(), "연락처 읽기 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnAddContact = view.findViewById<ImageButton>(R.id.btn_add_contact_dial)
        val btnSearch = view.findViewById<ImageButton>(R.id.btn_search_contact)
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

        btnAddContact.setOnClickListener {
            val defaultPhone = etPhoneNumber.text?.toString().orEmpty().trim()
            showSaveDialog(defaultPhone)
        }

        btnSearch.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingSearchOpen = true
                requestReadContacts.launch(Manifest.permission.READ_CONTACTS)
                return@setOnClickListener
            }
            showSearchDialog()
        }

        val btnDelete = view.findViewById<Button>(R.id.btn_delete)
        btnDelete?.setOnClickListener {
            val phone = etPhoneNumber.text.toString()
            if (phone.isNotEmpty()) {
                etPhoneNumber.setText(phone.dropLast(1))
            }
        }
    }

    private fun appendNumber(number: String) {
        etPhoneNumber.append(number)
    }

    private fun callPhone(number: String) {
        if (number.isNotEmpty()) {
            val intent = Intent(requireContext(), CallingActivity::class.java).apply {
                putExtra("phone_number", number)
                putExtra("is_outgoing", true)
            }
            startActivity(intent)
        }
    }

    private fun showSaveDialog(defaultPhone: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_contact, null)

        val etName = dialogView.findViewById<EditText>(R.id.etDialogName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etDialogPhone)
        val etMemo = dialogView.findViewById<EditText>(R.id.etDialogMemo)

        etPhone.setText(defaultPhone)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("연락처 저장")
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .setPositiveButton("확인", null)
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setOnClickListener {
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val memo = etMemo.text.toString().trim()

                if (name.isEmpty()) {
                    etName.error = "이름을 입력하세요"
                    return@setOnClickListener
                }
                if (phone.isEmpty()) {
                    etPhone.error = "연락처를 입력하세요"
                    return@setOnClickListener
                }

                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_CONTACTS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingSave = Triple(name, phone, memo)
                    requestWriteContacts.launch(Manifest.permission.WRITE_CONTACTS)
                    return@setOnClickListener
                }

                saveContact(name, phone, memo)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun saveContact(name: String, phone: String, memo: String) {
        val ops = ArrayList<ContentProviderOperation>()

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )

        if (memo.isNotEmpty()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, memo)
                    .build()
            )
        }

        try {
            requireContext().contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(requireContext(), "연락처가 저장되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSearchDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_contact, null)

        val etQuery = dialogView.findViewById<EditText>(R.id.etQuery)
        val listResults = dialogView.findViewById<ListView>(R.id.listResults)

        val items = mutableListOf<ContactItem>()
        val data = mutableListOf<Map<String, String>>()
        val simpleAdapter = SimpleAdapter(
            requireContext(),
            data,
            android.R.layout.simple_list_item_2,
            arrayOf("title", "sub"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        listResults.adapter = simpleAdapter

        fun updateList(query: String) {
            val result = searchContacts(query)
            data.clear()
            data.addAll(
                result.map {
                    mapOf(
                        "title" to (it.name.ifBlank { "(이름 없음)" }),
                        "sub" to it.phone
                    )
                }
            )
            simpleAdapter.notifyDataSetChanged()
            items.clear()
            items.addAll(result)
        }

        updateList("")

        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateList(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("연락처 검색")
            .setView(dialogView)
            .setNegativeButton("닫기", null)
            .create()

        listResults.setOnItemClickListener { _, _, position, _ ->
            val picked = items[position]
            etPhoneNumber.setText(picked.phone)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun searchContacts(query: String): List<ContactItem> {
        val resolver = requireContext().contentResolver
        val result = mutableListOf<ContactItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = if (query.isBlank()) null
        else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val args = if (query.isBlank()) null
        else arrayOf("%$query%", "%$query%")
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        val cursor: Cursor? = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            sortOrder
        )

        cursor?.use {
            val idxId = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val idxName = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val idxNum = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            var count = 0
            while (it.moveToNext() && count < 50) {
                val id = if (idxId >= 0) it.getLong(idxId) else null
                val name = if (idxName >= 0) it.getString(idxName) ?: "" else ""
                val phone = if (idxNum >= 0) it.getString(idxNum) ?: "" else ""
                if (phone.isNotBlank()) {
                    result.add(ContactItem(name = name, phone = phone, contactId = id))
                    count++
                }
            }
        }
        return result
    }
}
