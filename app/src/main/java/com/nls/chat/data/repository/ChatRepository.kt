package com.nls.chat.data.repository

import com.nls.chat.ai.GeminiService
import com.nls.chat.data.local.MessageDao
import com.nls.chat.model.CompanionConfig
import com.nls.chat.model.Message
import com.nls.chat.model.MessageSender
import com.nls.chat.model.MessageStatus
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val messageDao: MessageDao,
    private val geminiService: GeminiService
) {
    val messagesFlow: Flow<List<Message>> = messageDao.getAllMessagesFlow()

    suspend fun loadHistoryToService(config: CompanionConfig) {
        val messages = messageDao.getAllMessages()
        geminiService.loadHistory(config, messages)
    }

    suspend fun saveMessage(message: Message): Message {
        val id = messageDao.insertMessage(message)
        return message.copy(id = id)
    }

    suspend fun saveFallbackMessage(
        text: String,
        sender: MessageSender,
        status: MessageStatus = MessageStatus.SENT
    ): Message = saveMessage(Message(text = text, sender = sender, status = status))

    // runCatching garantiza que NUNCA se propaga una excepcion al ViewModel
    suspend fun sendMessageAndGetResponse(
        userText: String,
        config: CompanionConfig
    ): Result<Message> = runCatching {

        // 1. Guardar mensaje del usuario en Room
        saveMessage(
            Message(
                text = userText.trim(),
                sender = MessageSender.USER,
                status = MessageStatus.SENT
            )
        )

        // 2. Llamar a Gemini API
        val geminiResult = geminiService.sendMessage(userText.trim(), config)

        // 3. Si Gemini falla, lanzar para que runCatching lo capture
        val responseText = geminiResult.getOrElse { ex ->
            saveMessage(
                Message(
                    text = "No pude conectarme ahora. Intentalo de nuevo.",
                    sender = MessageSender.AI,
                    status = MessageStatus.ERROR
                )
            )
            throw ex
        }

        // 4. Guardar respuesta de la IA
        saveMessage(
            Message(
                text = responseText,
                sender = MessageSender.AI,
                status = MessageStatus.SENT
            )
        )
    }

    suspend fun getWelcomeMessage(config: CompanionConfig): Result<Message> = runCatching {
        val result = geminiService.getWelcomeMessage(config)
        val text = result.getOrElse { "Hola! Como estas hoy?" }
        saveMessage(
            Message(text = text, sender = MessageSender.AI, status = MessageStatus.SENT)
        )
    }

    suspend fun clearAllMessages(config: CompanionConfig) {
        messageDao.deleteAllMessages()
        geminiService.clearHistory(config)
    }

    suspend fun getMessageCount() = messageDao.getMessageCount()
}
