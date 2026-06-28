package com.miniv.chat.chat_data

import java.util.UUID

/**
 * Chat Data
 * - Represents each chat data
 *
 * @property id Unique ID of chat
 * @property message Content of chat
 * @property timestamp Millis timestamp of chat
 *
 * @see LLMChatData - Chat Data from LLM Engine Output
 * @see UserChatData - Chat Data from User Input
 */
sealed interface ChatData {
    val id: UUID
    val message: String
    val timestamp: Long

    data class LLMChatData(
        override val id: UUID,
        override var message: String,
        override val timestamp: Long,
    ): ChatData {
        /**
         * Append [data] to [message] of current instance
         *
         * @return updated [message]
         */
        fun appendMessage(data: String): String {
            message += data
            return message
        }

        /**
         * Update [message] to [newMessage]
         *
         * @return updated [message]
         */
        fun updateMessage(newMessage: String): String {
            message = newMessage
            return message
        }
    }

    data class UserChatData(
        override val id: UUID,
        override val message: String,
        override val timestamp: Long,
    ): ChatData
}
