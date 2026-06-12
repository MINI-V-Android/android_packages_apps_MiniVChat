package com.miniv.chat

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

/** Main Activity */
class MainActivity : Activity() {
    // View Instances
    private lateinit var btnSend: Button
    private lateinit var etInput: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
    }

    /**
     * Setup view instances
     */
    private fun setupUI() {
        // Initialize instances
        btnSend = findViewById(R.id.btnSend)
        etInput = findViewById(R.id.etInput)
        scrollView = findViewById(R.id.scrollView)
        tvOutput = findViewById(R.id.tvOutput)

        btnSend.setOnClickListener { onBtnSend() }
    }

    /**
     * On click [btnSend]
     */
    private fun onBtnSend() {
        // TODO: Implement logic
    }
}
