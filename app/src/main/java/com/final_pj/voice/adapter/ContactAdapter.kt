package com.final_pj.voice.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.feature.call.model.Contact
import com.google.android.material.button.MaterialButton
import java.util.Locale

class ContactAdapter(
    private val items: List<Contact>,
    private val onCallClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    // 화면에 보여줄 리스트(검색 결과)
    private val displayItems: MutableList<Contact> = items.toMutableList()

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvNumber: TextView = itemView.findViewById(R.id.tv_number)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btn_call)

        fun bind(contact: Contact) {
            tvName.text = contact.name
            tvNumber.text = contact.phone
            btnCall.setOnClickListener { onCallClick(contact) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(displayItems[position])
    }

    override fun getItemCount(): Int = displayItems.size

    /** 이름/번호 검색 */
    fun filter(query: String) {
        val q = query.normalize()

        displayItems.clear()

        if (q.isEmpty()) {
            displayItems.addAll(items)
        } else {
            displayItems.addAll(
                items.filter { c ->
                    val name = (c.name ?: "").normalize()
                    val phone = (c.phone ?: "").normalize()
                    name.contains(q) || phone.contains(q)
                }
            )
        }

        notifyDataSetChanged()
    }

    private fun String.normalize(): String {
        return lowercase(Locale.getDefault())
            .replace(" ", "")
            .replace("-", "")
    }
}
