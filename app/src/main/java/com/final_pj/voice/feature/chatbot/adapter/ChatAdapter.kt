package com.final_pj.voice.feature.chatbot.adapter

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.feature.chatbot.model.ChatMessage

class ChatAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_BOT = 1
        private const val TYPE_LOADING = 2
    }

    override fun getItemViewType(position: Int): Int {
        val m = items[position]
        return when {
            m.isLoading -> TYPE_LOADING
            m.isUser -> TYPE_USER
            else -> TYPE_BOT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_USER -> {
                // ✅ 너가 쓰는 유저 아이템 레이아웃으로 변경
                val v = inflater.inflate(R.layout.item_chat_user, parent, false)
                UserVH(v)
            }
            TYPE_BOT -> {
                // ✅ 너가 쓰는 봇 아이템 레이아웃으로 변경
                val v = inflater.inflate(R.layout.item_chat_bot, parent, false)
                BotVH(v)
            }
            else -> {
                val v = inflater.inflate(R.layout.item_chat_loading, parent, false)
                LoadingVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = items[position]
        when (holder) {
            is UserVH -> holder.bind(m)
            is BotVH -> holder.bind(m)
            is LoadingVH -> holder.bind()
        }
    }

    override fun getItemCount(): Int = items.size

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

    fun setItems(newItems: List<ChatMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** ✅ 로딩 말풍선 추가 후 id 반환 */
    fun addLoading(): Long {
        val msg = ChatMessage(isUser = false, isLoading = true)
        add(msg)
        return msg.id
    }

    /** ✅ 특정 id 메시지를 교체 (로딩 -> 봇답변) */
    fun replaceById(targetId: Long, newMsg: ChatMessage) {
        val idx = items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            items[idx] = newMsg
            notifyItemChanged(idx)
        } else {
            add(newMsg)
        }
    }

    // ---------------- ViewHolders ----------------

    class UserVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv: TextView = itemView.findViewById(R.id.tvUser)

        fun bind(m: ChatMessage) {
            tv.text = m.text
        }
    }

    class BotVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv: TextView = itemView.findViewById(R.id.tvBot)

        fun bind(m: ChatMessage) {
            tv.text = m.text
        }
    }

    class LoadingVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv = v.findViewById<TextView>(R.id.tvLoading)
        private val handler = Handler(Looper.getMainLooper())
        private var tick = 0
        private val runnable = object : Runnable {
            override fun run() {
                val dots = ".".repeat((tick % 3) + 1)
                tv.text = "생각중$dots"
                tick++
                handler.postDelayed(this, 350)
            }
        }

        fun bind() {
            handler.removeCallbacks(runnable)
            handler.post(runnable)
        }

        fun stop() {
            handler.removeCallbacks(runnable)
        }
    }
}
