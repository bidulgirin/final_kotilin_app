package com.final_pj.voice.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.CallingControlActivity
import com.final_pj.voice.R
import com.final_pj.voice.adapter.ContactAdapter
import com.final_pj.voice.model.Contact

// 전화통화 기록 + 전화통화 기록 요약 페이지

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val PERMISSION_REQUEST = 1001

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (hasContactPermission()) {
            setupRecycler(view)
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

        val contacts = loadContacts()
        Log.d("CONTACT_PHONE", "${contacts}")


        recycler.adapter = ContactAdapter(contacts) { contact ->
            val bundle = Bundle().apply {
                putString("name", contact.name)
                putString("phone", contact.phone)
            }
            //findNavController().navigate(R.id.action_home_to_call, bundle)
            callPhone(contact.phone)
        }

    }
    // 전화 거는 화면으로 이동 (발신) : DialerFragement 랑 중복됨...
    private fun callPhone(number: String) {
        if (number.isNotEmpty()) {
            val intent = Intent(requireContext(), CallingControlActivity::class.java).apply {
                putExtra("phone_number", number)
                putExtra("is_outgoing", true)   // ⭐ 발신 표시
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

    // ------------------
    // 연락처 불러오는 함수
    // ------------------
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
                val name =
                    it.getString(it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ))
                val phone =
                    it.getString(it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ))

                contacts.add(Contact(name, phone))
            }
        }
        return contacts
    }
}