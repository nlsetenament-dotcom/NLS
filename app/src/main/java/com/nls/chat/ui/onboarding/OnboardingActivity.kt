package com.nls.chat.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.nls.chat.R
import com.nls.chat.data.local.PreferencesManager
import com.nls.chat.databinding.ActivityOnboardingBinding
import com.nls.chat.model.*
import com.nls.chat.ui.home.HomeActivity
import java.io.File
import java.io.FileOutputStream

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: PreferencesManager

    private var currentStep = 0
    private var companionName = ""
    private var selectedGender = Gender.FEMALE
    private var selectedPersonality = Personality.SWEET
    private var selectedSpeechStyle = SpeechStyle.CASUAL
    private var selectedEmotionalLevel = EmotionalLevel.MEDIUM
    private var selectedInterests = mutableListOf<String>()
    private var avatarUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            avatarUri = it
            Glide.with(this).load(it).circleCrop().into(binding.ivAvatarPreview)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickImageLauncher.launch("image/*")
        else Toast.makeText(this, "Permiso necesario para foto", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager(this)

        if (prefs.isOnboardingComplete) { goToHome(); return }

        binding.btnNext.setOnClickListener { handleNext() }
        binding.btnBack.setOnClickListener { handleBack() }
        binding.btnPickAvatar.setOnClickListener { pickAvatar() }
        binding.ivAvatarPreview.setOnClickListener { pickAvatar() }

        showStep(0)
    }

    private fun showStep(step: Int) {
        currentStep = step
        binding.progressBar.progress = ((step + 1).toFloat() / 5 * 100).toInt()
        binding.tvStepIndicator.text = "Paso ${step + 1} de 5"

        val steps = listOf(
            binding.step0Layout, binding.step1Layout, binding.step2Layout,
            binding.step3Layout, binding.step4Layout
        )
        steps.forEach { it.visibility = View.GONE }

        val target = steps[step]
        target.visibility = View.VISIBLE

        // Animacion de entrada del contenido del paso
        val anim = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        target.startAnimation(anim)

        when (step) {
            0 -> {
                binding.btnBack.visibility = View.GONE
                binding.tvNextLabel.text = "Siguiente"
            }
            4 -> {
                binding.btnBack.visibility = View.VISIBLE
                binding.tvNextLabel.text = "Crear"
            }
            else -> {
                binding.btnBack.visibility = View.VISIBLE
                binding.tvNextLabel.text = "Siguiente"
            }
        }
    }

    private fun handleNext() {
        when (currentStep) {
            0 -> {
                companionName = binding.etCompanionName.text.toString().trim()
                if (companionName.isBlank()) {
                    binding.etCompanionName.error = "Ingresa un nombre"
                    return
                }
                selectedGender = when (binding.rgGender.checkedRadioButtonId) {
                    binding.rbMale.id -> Gender.MALE
                    binding.rbNonBinary.id -> Gender.NON_BINARY
                    else -> Gender.FEMALE
                }
                showStep(1)
            }
            1 -> {
                selectedPersonality = when (binding.rgPersonality.checkedRadioButtonId) {
                    binding.rbFunny.id -> Personality.FUNNY
                    binding.rbSerious.id -> Personality.SERIOUS
                    binding.rbFlirty.id -> Personality.FLIRTY
                    binding.rbAdventurous.id -> Personality.ADVENTUROUS
                    else -> Personality.SWEET
                }
                selectedSpeechStyle = when (binding.rgSpeech.checkedRadioButtonId) {
                    binding.rbFormal.id -> SpeechStyle.FORMAL
                    binding.rbYouth.id -> SpeechStyle.YOUTH
                    else -> SpeechStyle.CASUAL
                }
                showStep(2)
            }
            2 -> {
                selectedEmotionalLevel = when (binding.rgEmotional.checkedRadioButtonId) {
                    binding.rbLow.id -> EmotionalLevel.LOW
                    binding.rbHigh.id -> EmotionalLevel.HIGH
                    else -> EmotionalLevel.MEDIUM
                }
                showStep(3)
            }
            3 -> {
                selectedInterests.clear()
                for (i in 0 until binding.chipGroupInterests.childCount) {
                    val chip = binding.chipGroupInterests.getChildAt(i) as? Chip
                    if (chip?.isChecked == true) selectedInterests.add(chip.text.toString())
                }
                showStep(4)
            }
            4 -> finishOnboarding()
        }
    }

    private fun handleBack() { if (currentStep > 0) showStep(currentStep - 1) }

    private fun pickAvatar() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            pickImageLauncher.launch("image/*")
        else requestPermissionLauncher.launch(perm)
    }

    private fun finishOnboarding() {
        val savedPath = avatarUri?.let { copyAvatarToInternal(it) }
        prefs.companionConfig = CompanionConfig(
            name = companionName,
            gender = selectedGender,
            personality = selectedPersonality,
            speechStyle = selectedSpeechStyle,
            emotionalLevel = selectedEmotionalLevel,
            interests = selectedInterests.toList(),
            avatarPath = savedPath
        )
        prefs.isOnboardingComplete = true
        goToHome()
    }

    private fun copyAvatarToInternal(uri: Uri): String? {
        return try {
            val dir = File(filesDir, "avatars").apply { mkdirs() }
            val file = File(dir, "avatar.jpg")
            contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(file).use { out -> inp.copyTo(out) }
            }
            file.absolutePath
        } catch (e: Exception) { null }
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
