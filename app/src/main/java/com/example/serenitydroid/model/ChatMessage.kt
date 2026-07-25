package com.example.serenitydroid.model

data class ChatMessage(
    val id: Long,
    val senderType: SenderType,
    var text: String
)

enum class SenderType {
    USER, BOT, SYSTEM
}


