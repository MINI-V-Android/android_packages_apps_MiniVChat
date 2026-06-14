package com.miniv.chat

import android.app.Activity
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.miniv.ai.IMINIVAIService

/** Main Activity */
class MainActivity : Activity() {
    companion object {
        private const val TAG = "MiniVChat"
        private const val SERVICE_NAME = "miniv_ai"
        private const val MAX_TOKENS = 512
    }

    // View Instances
    private lateinit var btnSend: Button
    private lateinit var etInput: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView

    // Service Instance
    private var service: IMINIVAIService? = null

    // Current AI session info
    private var currentSessionId = -1
    private var isInferOngoing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        connectService()
    }

    /** Setup view instances */
    private fun setupUI() {
        // Initialize instances
        btnSend = findViewById(R.id.btnSend)
        etInput = findViewById(R.id.etInput)
        scrollView = findViewById(R.id.scrollView)
        tvOutput = findViewById(R.id.tvOutput)

        // Set event listener
        btnSend.setOnClickListener { onBtnSend() }
    }

    /** Set inference state as ongoing */
    private fun setInferOngoingState(sessionId: Int) {
        currentSessionId = sessionId
        isInferOngoing = true
    }

    /** Set UI as inference ongoing */
    private fun setInferOngoingUI() {
        // Set send button as cancel
        btnSend.text = getString(R.string.btn_cancel)

        // Clear input edittext
        etInput.setText("")
        etInput.isEnabled = false

        // Clear output textview
        tvOutput.text = ""
    }

    /** Reset inference state */
    private fun resetInferState() {
        currentSessionId = -1
        isInferOngoing = false
    }

    /** Reset inference related UI */
    private fun resetInferUI() {
        // Set send button as send
        btnSend.text = getString(R.string.btn_send)

        // Enable input edittext
        etInput.isEnabled = true
    }

    /** On click [btnSend] */
    private fun onBtnSend() {
        if (isInferOngoing) {
            cancelInference()
        } else {
            startInference()
        }
    }

    /** Connect to MINIVAIService via ServiceManager */
    private fun connectService() {
        // Get service binder
        val binder = ServiceManager.getService(SERVICE_NAME)

        // Exception if binder is null
        if (binder == null) {
            Log.e(TAG, "Service not found: $SERVICE_NAME")
            return
        }

        // Connect to service
        service = IMINIVAIService.Stub.asInterface(binder)
        Log.i(TAG, "Service connected")
    }

    /** Cancel current ongoing inference */
    private fun cancelInference() {
        // Check if current session is ongoing
        val svc = service ?: return
        if (currentSessionId < 0 || isInferOngoing.not()) return

        try {
            svc.cancel(currentSessionId)
            Log.i(TAG, "cancel requested for session $currentSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "cancel() failed", e)
        }

        // Reset state / UI
        resetInferState()
        resetInferUI()
    }

    /** Start new inference */
    private fun startInference() {
        // Check if inference ongoing
        if (isInferOngoing) {
            Log.w(TAG, "Service is already inferring")
            return
        }

        // Check if service is connected
        val svc =
                service
                        ?: run {
                            Log.w(TAG, "Service not connected")
                            return
                        }

        // Check input prompt
        val prompt = etInput.text.toString().trim()
        if (prompt.isBlank()) return

        // Check if service is ready
        try {
            if (svc.isReady.not()) {
                Log.w(TAG, "Service is not ready")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service isReady() failed", e)
            return
        }

        // Generate callback instance for inference
        val inferCallback = getStreamCallback()

        // Start inference
        try {
            val sessionId = svc.inferStream(prompt, MAX_TOKENS, inferCallback)
            Log.i(TAG, "inferStream started, sessionId=$sessionId")

            // Check if created session is available
            if (sessionId < 0) {
                Log.e(TAG, "Failed to start inference: $sessionId")

                // Reset state / UI
                resetInferState()
                resetInferUI()
                return
            }

            // Set state / UI as inference ongoing
            setInferOngoingState(sessionId)
            setInferOngoingUI()
        } catch (e: Exception) {
            Log.e(TAG, "inferStream() failed", e)

            // Reset state / UI
            resetInferState()
            resetInferUI()
        }
    }

    /** Generate callback listener instance */
    private fun getStreamCallback(): LLMStreamCallback {
        val listener =
                object : LLMStreamCallback.Listener {
                    override fun onToken(token: String) {
                        // Show generated token to output
                        runOnUiThread {
                            tvOutput.append(token)
                            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
                        }
                    }

                    override fun onComplete() {
                        // Reset state / UI
                        resetInferState()
                        runOnUiThread { resetInferUI() }
                    }

                    override fun onError(code: Int, message: String) {
                        Log.e(TAG, "Inference error: code=$code msg=$message")

                        // Reset state / UI
                        resetInferState()
                        runOnUiThread { resetInferUI() }
                    }
                }

        return LLMStreamCallback(listener)
    }
}
