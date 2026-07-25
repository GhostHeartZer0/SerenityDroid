package com.example.serenitydroid.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.serenitydroid.R
import com.example.serenitydroid.databinding.ItemChatMessageBinding
import com.example.serenitydroid.model.ChatMessage
import com.example.serenitydroid.model.SenderType

class ChatAdapter(private val context: Context) :
    ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    // Default to a standard color
    private var userBubbleColor: Int = ContextCompat.getColor(context, R.color.electric_blue)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding =
            ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // This function allows MainActivity to update the bubble color
    fun setUserBubbleColor(colorRes: Int) {
        userBubbleColor = ContextCompat.getColor(context, colorRes)
        notifyDataSetChanged() // Redraw visible items with the new color
    }

    inner class MessageViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chatMessage: ChatMessage) {
            if (chatMessage.senderType == SenderType.USER) {
                // User's message
                binding.userMessageLayout.visibility = View.VISIBLE
                binding.botMessageLayout.visibility = View.GONE
                binding.systemMessageLayout.visibility = View.GONE

                binding.userMessageTextView.text = chatMessage.text
                binding.userMessageCard.setCardBackgroundColor(userBubbleColor)

            } else if (chatMessage.senderType == SenderType.BOT) {
                // Bot's message
                binding.userMessageLayout.visibility = View.GONE
                binding.botMessageLayout.visibility = View.VISIBLE
                binding.systemMessageLayout.visibility = View.GONE

                binding.botMessageTextView.text = chatMessage.text

            } else {
                // System message
                binding.userMessageLayout.visibility = View.GONE
                binding.botMessageLayout.visibility = View.GONE
                binding.systemMessageLayout.visibility = View.VISIBLE

                binding.systemMessageTextView.text = chatMessage.text
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
