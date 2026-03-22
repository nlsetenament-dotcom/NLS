package com.nls.chat.ai

import com.nls.chat.BuildConfig
import com.nls.chat.model.CompanionConfig
import com.nls.chat.model.Message
import com.nls.chat.model.MessageSender

class GeminiService {

    private val api    = RetrofitClient.geminiApi
    private val apiKey = BuildConfig.GEMINI_API_KEY

    // Historial en memoria — siempre empieza con el system prompt (user/model)
    private val conversationHistory = mutableListOf<GeminiContent>()

    // ─── Historial ────────────────────────────────────────────────────────────

    fun loadHistory(config: CompanionConfig, messages: List<Message>) {
        conversationHistory.clear()
        injectSystemPrompt(config)

        // Garantizar que los turnos alternen user/model correctamente
        // Gemini rechaza dos mensajes seguidos del mismo rol
        var lastRole = "model" // El systemPrompt termina en model

        messages.forEach { msg ->
            val role = if (msg.sender == MessageSender.USER) "user" else "model"

            // Si el rol es igual al último, insertar mensaje vacío del rol opuesto
            if (role == lastRole) {
                val fillerRole = if (role == "user") "model" else "user"
                conversationHistory.add(
                    GeminiContent(fillerRole, listOf(GeminiPart("...")))
                )
            }
            conversationHistory.add(GeminiContent(role, listOf(GeminiPart(msg.text))))
            lastRole = role
        }
    }

    fun clearHistory(config: CompanionConfig) {
        conversationHistory.clear()
        injectSystemPrompt(config)
    }

    // ─── Envío de mensajes ───────────────────────────────────────────────────

    suspend fun sendMessage(userText: String, config: CompanionConfig): Result<String> {
        if (conversationHistory.isEmpty()) injectSystemPrompt(config)

        // El último turno debe ser "model" para que podamos añadir "user"
        ensureLastRoleIsModel()

        return try {
            conversationHistory.add(GeminiContent("user", listOf(GeminiPart(userText))))

            val response = api.generateContent(
                apiKey  = apiKey,
                request = GeminiRequest(
                    contents          = conversationHistory.toList(),
                    generationConfig  = GenerationConfig(temperature = 0.9f, maxOutputTokens = 300)
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
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            conversationHistory.removeLastOrNull()
            Result.failure(e)
        }
    }

    // ─── Saludo inicial ───────────────────────────────────────────────────────

    suspend fun getWelcomeMessage(config: CompanionConfig): Result<String> {
        // Request independiente (no usa conversationHistory)
        return try {
            val prompt = config.buildSystemPrompt()
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent("user",  listOf(GeminiPart(prompt))),
                    GeminiContent("model", listOf(GeminiPart("Entendido, sere ${config.name}."))),
                    // Turno user para pedir el saludo
                    GeminiContent("user",  listOf(GeminiPart(
                        "Saluda al usuario por primera vez de forma carinosa y breve. " +
                        "Maximo 2 oraciones."
                    )))
                ),
                generationConfig = GenerationConfig(temperature = 0.85f, maxOutputTokens = 150)
            )

            val response = api.generateContent(apiKey = apiKey, request = request)
            val text = if (response.isSuccessful) {
                response.body()?.extractText()
                    ?: "Hola! Me alegra que estes aqui. Como estas hoy?"
            } else {
                "Hola! Como estas hoy?"
            }

            // Agregar al historial como user trigger + model response (turnos alternados)
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

    /** Si el último turno es "user" (no deberia pasar), agrega un model vacío */
    private fun ensureLastRoleIsModel() {
        val last = conversationHistory.lastOrNull()?.role
        if (last == "user") {
            conversationHistory.add(GeminiContent("model", listOf(GeminiPart("..."))))
        }
    }

    private fun trimHistoryIfNeeded() {
        if (conversationHistory.size > 34) {
            val systemPair = conversationHistory.take(2)
            val recent     = conversationHistory.takeLast(30)
            conversationHistory.clear()
            conversationHistory.addAll(systemPair)
            conversationHistory.addAll(recent)
        }
    }
}
