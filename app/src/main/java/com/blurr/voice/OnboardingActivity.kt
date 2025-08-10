package com.blurr.voice

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.data.MemoryManager
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var disclaimerText: TextView
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val profileManager = UserProfileManager(this)

        nameInput = findViewById(R.id.inputName)
        emailInput = findViewById(R.id.inputEmail)
        disclaimerText = findViewById(R.id.textDisclaimer)
        continueButton = findViewById(R.id.buttonContinue)

        disclaimerText.text = "This email will not be verified or stored by us. Please enter an accurate email so we don’t have to spend money to onboard you. We treat different emails as different users."

        continueButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Basic validation is handled in manager for email pattern when checking completeness later if needed
            profileManager.saveProfile(name, email)
            // Fire-and-forget: add the name to long-term memory
            // Use fire-and-forget so saving is not cancelled if Activity finishes
            MemoryManager.getInstance(this@OnboardingActivity).addMemoryFireAndForget("User name is $name")
            Toast.makeText(this, "Thanks, $name!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}


