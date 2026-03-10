package com.example.matsyamitra

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FeedingScheduleActivity : AppCompatActivity() {

    private lateinit var fishTypeEditText: EditText
    private lateinit var feedAmountEditText: EditText
    private lateinit var feedingTimeEditText: EditText
    private lateinit var saveScheduleButton: Button
    private lateinit var savedScheduleTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feeding_schedule)

        fishTypeEditText = findViewById(R.id.fishTypeEditText)
        feedAmountEditText = findViewById(R.id.feedAmountEditText)
        feedingTimeEditText = findViewById(R.id.feedingTimeEditText)
        saveScheduleButton = findViewById(R.id.saveScheduleButton)
        savedScheduleTextView = findViewById(R.id.savedScheduleTextView)

        // Load saved schedule when activity starts
        loadSchedule()

        saveScheduleButton.setOnClickListener {
            saveSchedule()
        }
    }

    private fun saveSchedule() {
        val fishType = fishTypeEditText.text.toString()
        val feedAmount = feedAmountEditText.text.toString()
        val feedingTime = feedingTimeEditText.text.toString()

        if (fishType.isEmpty() || feedAmount.isEmpty() || feedingTime.isEmpty()) {
            Toast.makeText(this, "దయచేసి అన్ని వివరాలను నమోదు చేయండి.", Toast.LENGTH_SHORT).show() // Please enter all details.
            return
        }

        // Get SharedPreferences (like a small notepad for your app)
        val sharedPref = getSharedPreferences("FeedingSchedule", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        // Save the data
        editor.putString("fish_type", fishType)
        editor.putString("feed_amount", feedAmount)
        editor.putString("feeding_time", feedingTime)
        editor.apply() // Apply saves the changes

        Toast.makeText(this, getString(R.string.schedule_saved_success), Toast.LENGTH_SHORT).show()
        loadSchedule() // Refresh the displayed schedule
    }

    private fun loadSchedule() {
        val sharedPref = getSharedPreferences("FeedingSchedule", Context.MODE_PRIVATE)
        val fishType = sharedPref.getString("fish_type", "")
        val feedAmount = sharedPref.getString("feed_amount", "")
        val feedingTime = sharedPref.getString("feeding_time", "")

        if (fishType.isNullOrEmpty() || feedAmount.isNullOrEmpty() || feedingTime.isNullOrEmpty()) {
            savedScheduleTextView.text = getString(R.string.saved_schedule_display)
        } else {
            savedScheduleTextView.text = "సేవ్ చేయబడిన షెడ్యూల్:\n" + // Saved Schedule:
                    "చేప జాతి: $fishType\n" +
                    "దాణా మొత్తం: $feedAmount\n" +
                    "దాణా సమయం: $feedingTime"
        }
    }
}