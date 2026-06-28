package com.miniv.chat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniv.chat.R
import com.miniv.chat.chat_data.ChatData

/**
 * RecyclerView Adapter for Chat List
 */
class ChatAdapter(
    private val chatList: MutableList<ChatData>,
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_recycler, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val chatData = chatList[position]
        holder.tvMessage.text = chatData.message
        holder.tvTimestamp.text = chatData.timestamp.toString()
    }

    override fun getItemCount(): Int = chatList.size

    /**
     * Add New chat data
     */
    fun addMessage(data: ChatData) {
        chatList.add(data)
        notifyItemInserted(chatList.lastIndex)
    }

    /**
     * Append token to last LLM chat data
     */
    fun appendTokenToLast(token: String) {
        val lastIdx = chatList.lastIndex
        if (lastIdx < 0) return

        // Append only last is LLM chat data
        val chatData = chatList[lastIdx]
        if (chatData is ChatData.LLMChatData) {
            chatData.appendMessage(token)
            notifyItemChanged(lastIdx)
        }
    }

    /**
     * Replace message of last LLM chat data
     */
    fun replaceLastMessage(newMessage: String) {
        val lastIdx = chatList.lastIndex
        if (lastIdx < 0) return

        // Replace only last is LLM chat data
        val item = chatList[lastIdx]
        if (item is ChatData.LLMChatData) {
            item.updateMessage(newMessage)
            notifyItemChanged(lastIdx)
        }
    }

    /**
     * ViewHolder for Chat RecyclerViewAdapter
     */
    class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
    }
}