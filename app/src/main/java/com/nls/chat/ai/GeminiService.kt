package com.nls.chat.ai

import com.nls.chat.BuildConfig
import com.nls.chat.model.CompanionConfig
import com.nls.chat.model.Message
import com.nls.chat.model.MessageSender

class GeminiService {

    private val api = RetrofitClient.geminiApi

    // Si BuildConfig no tiene la key (build local sin local.properties),
    // usar el fallback directo para que siempre funcione
    private val apiKey: String
        get() {
            val fromBuild = BuildConfig.GEMINI_API_KEY
            return if (fromBuild.isNotBlank()) fromBuild
            else "AIzaSyA8q5SOQKGxP_zsf8Uct475LbGnukG2uk0"
        }

    private val conversationHistory = mutableListOf<GeminiContent>()

    // ─── Historial ────────────────────────────────────────────────────────────

    fun loadHistory(config: CompanionConfig, messages: List<Message>) {
        conversationHistory.clear()
        injectSystemPrompt(config)

        var lastRole = "model"
        messages.forEach { msg ->
            val role = if (msg.sender == MessageSender.USER) "user" else "model"
            if (role == lastRole) {
                val filler = if (role == "user") "model" else "user"
                conversationHistory.add(GeminiContent(filler, listOf(GeminiPart("..."))))
            }
            conversationHistory.add(GeminiContent(role, listOf(GeminiPart(msg.text))))
            lastRole = role
        }
    }

    fun clearHistory(config: CompanionConfig) {
        conversationHistory.clear()
        injectSystemPrompt(config)
    }

    // ─── Envio de mensajes ───────────────────────────────────────────────────

    suspend fun sendMessage(userText: String, config: CompanionConfig): Result<String> {
        if (conversationHistory.isEmpty()) injectSystemPrompt(config)
        ensureLastRoleIsModel()

        return try {
            conversationHistory.add(GeminiContent("user", listOf(GeminiPart(userText))))

            val response = api.generateContent(
                apiKey  = apiKey,
                request = GeminiRequest(
                    contents         = conversationHistory.toList(),
                    generationConfig = GenerationConfig(temperature = 0.9f, maxOutputTokens = 300)
                )
            )

            if (response.isSuccessful) {
                val text = response.body()?.extractText()
                if (!text.isNullOrBlank()) {
                    conversationHistory.add(GeminiContent("model", listOf(GeminiPart(text))))
                    trimHistoryIfNeeded()
                    Result.success(text)
                } else {
                    conversationHistory.removeLastOrNull()
                    val reason = response.body()?.promptFeedback?.blockReason
                    Result.failure(Exception("Bloqueado: $reason"))
                }
            } else {
                conversationHistory.removeLastOrNull()
                val code = response.code()
                val body = response.errorBody()?.string() ?: ""
                Result.failure(Exception("HTTP $code: $body"))
            }
        } catch (e: Exception) {
            conversationHistory.removeLastOrNull()
            Result.failure(e)
        }
    }

    // ─── Saludo inicial ───────────────────────────────────────────────────────

    suspend fun getWelcomeMessage(config: CompanionConfig): Result<String> {
        return try {
            val prompt = config.buildSystemPrompt()
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent("user",  listOf(GeminiPart(prompt))),
                    GeminiContent("model", listOf(GeminiPart("Entendido, soy ${config.name}."))),
                    GeminiContent("user",  listOf(GeminiPart(
                        "Saluda al usuario por primera vez de forma carinosa y breve. Maximo 2 oraciones."
                    )))
                ),
                generationConfig = GenerationConfig(temperature = 0.85f, maxOutputTokens = 150)
            )

            val response = api.generateContent(apiKey = apiKey, request = request)
            val text = if (response.isSuccessful) {
                response.body()?.extractText() ?: "Hola! Como estas hoy?"
            } else {
                "Hola! Como estas hoy?"
            }

            // Agregar como par user/model al historial
            conversationHistory.add(GeminiContent("user",  listOf(GeminiPart("Hola"))))
            conversationHistory.add(GeminiContent("model", listOf(GeminiPart(text))))

            Result.success(text)
        } catch (e: Exception) {
            Result.success("Hola! Como estas hoy?")
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private fun injectSystemPrompt(config: CompanionConfig) {
        conversationHistory.add(GeminiContent("user",  listOf(GeminiPart(config.buildSystemPrompt()))))
        conversationHistory.add(GeminiContent("model", listOf(GeminiPart("Entendido, soy ${config.name}."))))
    }

    private fun ensureLastRoleIsModel() {
        if (conversationHistory.lastOrNull()?.role == "user") {
            conversationHistory.add(GeminiContent("model", listOf(GeminiPart("..."))))
        }
    }

    private fun trimHistoryIfNeeded() {
        if (conversationHistory.size > 34) {
            val sys    = conversationHistory.take(2)
            val recent = conversationHistory.takeLast(30)
            conversationHistory.clear()
            conversationHistory.addAll(sys)
            conversationHistory.addAll(recent)
        }
    }
}
