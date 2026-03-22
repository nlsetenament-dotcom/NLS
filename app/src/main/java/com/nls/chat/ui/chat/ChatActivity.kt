package com.nls.chat.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.nls.chat.R
import com.nls.chat.databinding.ActivityChatBinding
import com.nls.chat.ui.settings.SettingsActivity
import com.nls.chat.utils.hide
import com.nls.chat.utils.show
import java.io.File

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeViewModel()

        viewModel.showWelcomeIfNeeded()
    }

    private fun setupToolbar() {
        val config = viewModel.config
        binding.tvToolbarName.text = config.name

        val path = config.avatarPath
        if (!path.isNullOrBlank() && File(path).exists()) {
            Glide.with(this).load(File(path)).circleCrop()
                .placeholder(R.drawable.ic_avatar_default)
                .into(binding.ivToolbarAvatar)
        }

        // Boton atras
        binding.btnBack.setOnClickListener { finish() }

        // Gear → abrir Settings
        binding.btnGearSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Basura → borrar chat con confirmacion
        binding.btnClearChat.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Borrar conversacion")
                .setMessage("Seguro que quieres borrar todo el historial?")
                .setPositiveButton("Borrar") { _, _ ->
                    viewModel.clearChat()
                    Snackbar.make(binding.root, "Chat borrado", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.rvMessages.layoutManager =
            LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.adapter = adapter
        binding.rvMessages.itemAnimator = null
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnSend.isEnabled = false
        binding.btnSend.alpha = 0.4f

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val has = !s.isNullOrBlank()
                binding.btnSend.isEnabled = has
                binding.btnSend.alpha = if (has) 1f else 0.4f
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { msgs ->
            adapter.submitList(msgs) {
                if (msgs.isNotEmpty())
                    binding.rvMessages.smoothScrollToPosition(msgs.size - 1)
            }
        }

        viewModel.isTyping.observe(this) { typing ->
            if (typing) {
                binding.layoutTyping.show()
                val anim = AnimationUtils.loadAnimation(this, R.anim.typing_dots)
                binding.tvTypingDot1.startAnimation(anim)
                binding.tvTypingDot2.startAnimation(anim)
                binding.tvTypingDot3.startAnimation(anim)
                binding.rvMessages.post {
                    binding.rvMessages.smoothScrollToPosition(adapter.itemCount)
                }
            } else {
                binding.layoutTyping.hide()
            }
        }

        viewModel.errorEvent.observe(this) { err ->
            err?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isBlank()) return
        binding.etMessage.text?.clear()
        viewModel.sendMessage(text)
    }
}
