package com.final_pj.voice.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.model.Contact

// 기깔나게 데이터 담아주는 어뎁터
class ContactAdapter(
    private val items: List<Contact>,
    private val onCallClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<TextView>(R.id.tv_name)
        val phone = view.findViewById<TextView>(R.id.tv_number)
        val callBtn = view.findViewById<ImageButton>(R.id.btn_call)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = items[position]
        holder.name.text = contact.name
        holder.phone.text = contact.phone

        holder.callBtn.setOnClickListener {
            onCallClick(contact)
        }
    }

    override fun getItemCount() = items.size
}
