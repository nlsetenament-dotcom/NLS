package com.nls.chat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.nls.chat.ai.GeminiService
import com.nls.chat.data.local.AppDatabase
import com.nls.chat.data.local.PreferencesManager
import com.nls.chat.data.repository.ChatRepository
import com.nls.chat.model.CompanionConfig
import com.nls.chat.model.MessageSender
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesManager(app)
    private val db    = AppDatabase.getInstance(app)
    private val repo  = ChatRepository(db.messageDao(), GeminiService())

    val config: CompanionConfig
        get() = prefs.companionConfig ?: CompanionConfig()

    val messages = repo.messagesFlow.asLiveData()

    private val _isTyping = MutableLiveData(false)
    val isTyping: LiveData<Boolean> = _isTyping

    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> = _errorEvent

    private var welcomeShown = false

    init {
        viewModelScope.launch {
            try { repo.loadHistoryToService(config) } catch (_: Exception) {}
        }
    }

    fun showWelcomeIfNeeded() {
        if (welcomeShown) return
        welcomeShown = true
        viewModelScope.launch {
            try {
                val count = repo.getMessageCount()
                if (count == 0) {
                    // postValue es thread-safe desde coroutines
                    _isTyping.postValue(true)
                    repo.getWelcomeMessage(config)
                    _isTyping.postValue(false)
                }
            } catch (e: Exception) {
                _isTyping.postValue(false)
                try {
                    repo.saveFallbackMessage(
                        "Hola! Me alegra que estes aqui. Como estas hoy?",
                        MessageSender.AI
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        _isTyping.postValue(true)
        viewModelScope.launch {
            try {
                val result = repo.sendMessageAndGetResponse(text.trim(), config)
                result
                    .onSuccess { _isTyping.postValue(false) }
                    .onFailure {
                        _isTyping.postValue(false)
                        _errorEvent.postValue("Sin respuesta. Verifica tu conexion.")
                    }
            } catch (e: Exception) {
                // Garantia absoluta: _isTyping SIEMPRE se apaga
                _isTyping.postValue(false)
                _errorEvent.postValue("Error inesperado: ${e.message}")
                try {
                    repo.saveFallbackMessage(
                        "Ups, algo salio mal. Intentalo de nuevo.",
                        MessageSender.AI
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            try {
                repo.clearAllMessages(config)
                welcomeShown = false
                showWelcomeIfNeeded()
            } catch (e: Exception) {
                _errorEvent.postValue("No se pudo borrar el chat.")
            }
        }
    }

    fun clearError() { _errorEvent.value = null }
}
