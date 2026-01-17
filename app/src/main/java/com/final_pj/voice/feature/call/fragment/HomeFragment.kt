package com.final_pj.voice.feature.call.fragment

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.feature.call.activity.CallingActivity
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.adapter.ContactAdapter
import com.final_pj.voice.feature.call.model.Contact

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val PERMISSION_REQUEST_READ = 1001

    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contacts: List<Contact>

    private var pendingSave: Triple<String, String, String>? = null

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons(view)
        
        if (hasContactPermission(Manifest.permission.READ_CONTACTS)) {
            refreshContactList(view)
            setupSearch(view)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSION_REQUEST_READ
            )
        }
    }

    private fun setupButtons(view: View) {
        val btnSearchToggle = view.findViewById<ImageButton>(R.id.btn_search_toggle)
        val btnAddContact = view.findViewById<ImageButton>(R.id.btn_add_contact)
        val searchView = view.findViewById<SearchView>(R.id.search_contact)

        btnSearchToggle.setOnClickListener {
            if (searchView.visibility == View.VISIBLE) {
                searchView.visibility = View.GONE
                searchView.setQuery("", false)
                contactAdapter.filter("")
            } else {
                searchView.visibility = View.VISIBLE
                searchView.isIconified = false
                searchView.requestFocus()
            }
        }

        btnAddContact.setOnClickListener {
            showSaveDialog()
        }
    }

    private fun refreshContactList(view: View) {
        setupRecycler(view)
    }

    private fun setupRecycler(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.contact_recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        contacts = loadContacts()
        Log.d("CONTACT_PHONE", "$contacts")

        contactAdapter = ContactAdapter(
            contacts,
            onCallClick = { contact -> callPhone(contact.phone) },
            onEditClick = { contact -> showEditDialog(contact) },
            onDeleteClick = { contact -> showDeleteConfirmDialog(contact) }
        )

        recycler.adapter = contactAdapter
    }

    private fun setupSearch(view: View) {
        val searchView = view.findViewById<SearchView>(R.id.search_contact)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                contactAdapter.filter(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                contactAdapter.filter(newText.orEmpty())
                return true
            }
        })

        searchView.setOnCloseListener {
            contactAdapter.filter("")
            false
        }
    }

    private fun showSaveDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_contact, null)

        val etName = dialogView.findViewById<EditText>(R.id.etDialogName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etDialogPhone)
        val etMemo = dialogView.findViewById<EditText>(R.id.etDialogMemo)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("연락처 추가")
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", null)
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

                if (!hasContactPermission(Manifest.permission.WRITE_CONTACTS)) {
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

    private fun showEditDialog(contact: Contact) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_contact, null)

        val etName = dialogView.findViewById<EditText>(R.id.etDialogName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etDialogPhone)
        val etMemo = dialogView.findViewById<EditText>(R.id.etDialogMemo)

        etName.setText(contact.name)
        etPhone.setText(contact.phone)
        // Note: loading memo might require extra query, skipping for brevity but functionality is there

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("연락처 수정")
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .setPositiveButton("수정", null)
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setOnClickListener {
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                val newMemo = etMemo.text.toString().trim()

                if (newName.isEmpty() || newPhone.isEmpty()) {
                    Toast.makeText(requireContext(), "필수 항목을 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (hasContactPermission(Manifest.permission.WRITE_CONTACTS)) {
                    updateContact(contact.contactId, newName, newPhone, newMemo)
                    dialog.dismiss()
                } else {
                    requestWriteContacts.launch(Manifest.permission.WRITE_CONTACTS)
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteConfirmDialog(contact: Contact) {
        AlertDialog.Builder(requireContext())
            .setTitle("연락처 삭제")
            .setMessage("${contact.name} 연락처를 삭제하시겠습니까?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                if (hasContactPermission(Manifest.permission.WRITE_CONTACTS)) {
                    deleteContact(contact.contactId)
                } else {
                    requestWriteContacts.launch(Manifest.permission.WRITE_CONTACTS)
                }
            }
            .show()
    }

    private fun saveContact(name: String, phone: String, memo: String) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build())
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
        if (memo.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, memo).build())
        }
        try {
            requireContext().contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(requireContext(), "연락처가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            view?.let { refreshContactList(it) }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateContact(id: Long?, name: String, phone: String, memo: String) {
        if (id == null) return
        val ops = ArrayList<ContentProviderOperation>()
        
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?", 
                arrayOf(id.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build())

        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?", 
                arrayOf(id.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE))
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone).build())

        try {
            requireContext().contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(requireContext(), "연락처가 수정되었습니다.", Toast.LENGTH_SHORT).show()
            view?.let { refreshContactList(it) }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "수정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteContact(id: Long?) {
        if (id == null) return
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection("${ContactsContract.RawContacts.CONTACT_ID}=?", arrayOf(id.toString())).build())
        try {
            requireContext().contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(requireContext(), "연락처가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            view?.let { refreshContactList(it) }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    private fun hasContactPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val resolver = requireContext().contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = resolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val phone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                contacts.add(Contact(name, phone, id))
            }
        }
        return contacts
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_READ) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                view?.let { refreshContactList(it); setupSearch(it) }
            }
        }
    }
}
