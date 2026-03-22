package com.nls.chat.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.nls.chat.R
import com.nls.chat.data.local.PreferencesManager
import com.nls.chat.databinding.ActivityHomeBinding
import com.nls.chat.ui.chat.ChatActivity
import com.nls.chat.ui.onboarding.OnboardingActivity
import com.nls.chat.ui.settings.SettingsActivity
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager(this)

        binding.btnStartChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnResetOnboarding.setOnClickListener {
            prefs.isOnboardingComplete = false
            prefs.companionConfig = null
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }

        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun animateEntrance() {
        val fadeIn  = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        // Avatar aparece con fade
        binding.ivAvatar.startAnimation(fadeIn)

        // Nombre y detalles suben desde abajo con delay
        slideUp.startOffset = 120
        binding.tvCompanionName.startAnimation(slideUp)

        val slideUp2 = AnimationUtils.loadAnimation(this, R.anim.slide_up).apply { startOffset = 180 }
        binding.tvPersonality.startAnimation(slideUp2)

        val slideUp3 = AnimationUtils.loadAnimation(this, R.anim.slide_up).apply { startOffset = 240 }
        binding.tvInterests.startAnimation(slideUp3)

        val slideUp4 = AnimationUtils.loadAnimation(this, R.anim.slide_up).apply { startOffset = 300 }
        binding.btnStartChat.startAnimation(slideUp4)

        val slideUp5 = AnimationUtils.loadAnimation(this, R.anim.slide_up).apply { startOffset = 360 }
        binding.btnResetOnboarding.startAnimation(slideUp5)
    }

    private fun loadProfile() {
        val config = prefs.companionConfig ?: return
        binding.tvCompanionName.text = config.name
        binding.tvPersonality.text = "${config.personality.displayName} · ${config.speechStyle.displayName}"
        binding.tvInterests.text = if (config.interests.isNotEmpty())
            config.interests.joinToString(" · ")
        else "Sin intereses especificados"

        val path = config.avatarPath
        if (!path.isNullOrBlank() && File(path).exists()) {
            Glide.with(this).load(File(path)).circleCrop()
                .placeholder(R.drawable.ic_avatar_default)
                .into(binding.ivAvatar)
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_avatar_default)
        }
    }
}
