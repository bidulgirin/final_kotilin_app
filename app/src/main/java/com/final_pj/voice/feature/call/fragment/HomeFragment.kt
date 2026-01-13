package com.final_pj.voice.feature.call.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.feature.call.activity.CallingActivity
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.adapter.ContactAdapter
import com.final_pj.voice.feature.call.model.Contact

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val PERMISSION_REQUEST = 1001

    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contacts: List<Contact>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (hasContactPermission()) {
            setupRecycler(view)
            setupSearch(view)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSION_REQUEST
            )
        }
    }

    private fun setupRecycler(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.contact_recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        contacts = loadContacts()
        Log.d("CONTACT_PHONE", "$contacts")

        contactAdapter = ContactAdapter(contacts) { contact ->
            callPhone(contact.phone)
        }

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

        // X(클리어) 버튼 눌렀을 때 전체 목록으로 복구되는 경우가 많아서 처리
        searchView.setOnCloseListener {
            contactAdapter.filter("")
            false
        }
    }

    // 전화 거는 화면으로 이동 (발신)
    private fun callPhone(number: String) {
        if (number.isNotEmpty()) {
            val intent = Intent(requireContext(), CallingActivity::class.java).apply {
                putExtra("phone_number", number)
                putExtra("is_outgoing", true)
            }
            startActivity(intent)
        }
    }

    private fun callContact(phone: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phone")
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(intent)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CALL_PHONE),
                PERMISSION_REQUEST
            )
        }
    }

    private fun hasContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()

        val resolver = requireContext().contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        val cursor = resolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                )
                val phone = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                )

                contacts.add(Contact(name, phone))
            }
        }
        return contacts
    }
}
