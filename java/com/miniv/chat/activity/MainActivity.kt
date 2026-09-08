package com.miniv.chat.activity

import android.app.Activity
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miniv.ai.ILLMStreamCallback
import com.miniv.ai.IMINIVAIService
import com.miniv.chat.R
import com.miniv.chat.adapter.ChatAdapter
import com.miniv.chat.chat_data.ChatData
import com.miniv.chat.llm_engine.LLMStreamCallback
import java.util.UUID

/**
 *  Main Activity
 */
class MainActivity : Activity() {
    companion object {
        private const val TAG = "MiniVChat"
        private const val SERVICE_NAME = "miniv_ai"
        private const val MAX_TOKENS = 512
    }

    // View Instances
    private lateinit var btnSend: Button
    private lateinit var etInput: EditText
    private lateinit var rvChat: RecyclerView

    // Chat adapter
    private val chatAdapter = ChatAdapter(mutableListOf())

    // Service Instance
    private var service: IMINIVAIService? = null

    // Current AI session info
    // - each session is created when start new chat
    //   destroyed when app destroyed
    //   or forgetCurrentSession() is called
    private var currentSessionId = -1
    private var isInferOngoing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        connectService()
    }

    override fun onDestroy() {
        // Destroy currently enabled session
        destroyCurrentSession()
        super.onDestroy()
    }

    /**
     * Setup view instances
     */
    private fun setupUI() {
        // Setup window insets
        setupInsets()

        // Initialize instances
        btnSend = findViewById(R.id.btnSend)
        etInput = findViewById(R.id.etInput)
        rvChat = findViewById(R.id.rvChat)

        // Setup recycler view
        rvChat.adapter = chatAdapter
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            // Chatting must be reversed.
            // - Newer is at bottom
            stackFromEnd = true
        }

        // Set event listener
        btnSend.setOnClickListener { onBtnSend() }
    }

    /**
     * Setup window insets
     */
    private fun setupInsets() {
        val root = findViewById<View>(R.id.rootLayout)
        val basePad = (16 * resources.displayMetrics.density).toInt()

        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(basePad + bars.left,
                        basePad + bars.top,
                        basePad + bars.right,
                        basePad + bars.bottom)
            insets
        }
    }

    /**
     * Set inference state as ongoing
     */
    private fun setInferOngoingState() {
        isInferOngoing = true
    }

    /**
     *  Set UI as inference ongoing
     */
    private fun setInferOngoingUI() {
        // Set send button as cancel
        btnSend.text = getString(R.string.btn_cancel)

        // Clear input edittext
        etInput.setText("")
        etInput.isEnabled = false
    }

    /**
     * Reset inference state
     */
    private fun resetInferState() {
        isInferOngoing = false
    }

    /**
     *  Reset inference related UI
     */
    private fun resetInferUI() {
        // Set send button as send
        btnSend.text = getString(R.string.btn_send)

        // Enable input edittext
        etInput.isEnabled = true
    }

    /**
     *  On click [btnSend]
     */
    private fun onBtnSend() {
        if (isInferOngoing) {
            cancelInference()
        } else {
            startInference()
        }
    }

    /**
     * Connect to MINIVAIService via ServiceManager
     */
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

    /**
     * Ensure a session exists, creates one if needed
     * - Returns the session id, or negative if creation failed
     */
    private fun ensureSession(svc: IMINIVAIService): Int {
        if (currentSessionId >= 0) return currentSessionId

        return try {
            val newId = svc.createSession()
            if (newId < 0) {
                Log.e(TAG, "createSession() failed: $newId")
                newId
            } else {
                Log.i(TAG, "createSession() succeeded: $newId")
                currentSessionId = newId
                newId
            }
        } catch (e: Exception) {
            Log.e(TAG, "createSession() threw", e)
            -1
        }
    }

    /**
     * Drop the locally tracked session without notifying the vendor
     * - Used when the service already told us the session is gone
     * (ILLMStreamCallback.ERROR_SESSION_EVICTED / IMINIVAIService.INFER_ERR_UNKNOWN_SESSION)
     */
    private fun forgetCurrentSession() {
        currentSessionId = -1
    }

    /**
     * Explicitly destroy the current session on the vendor side
     * - Safe to call with no active session
     */
    private fun destroyCurrentSession() {
        val svc = service ?: return
        val sessionId = currentSessionId
        if (sessionId < 0) return

        try {
            svc.destroySession(sessionId)
            Log.i(TAG, "destroySession($sessionId) requested")
        } catch (e: Exception) {
            Log.e(TAG, "destroySession() failed", e)
        }
        currentSessionId = -1
    }

    /**
     * Cancel current ongoing inference
     */
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

    /**
     * Start new inference
     */
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

        // Make sure we have a live session to talk into
        val sessionId = ensureSession(svc)
        if (sessionId < 0) {
            Log.e(TAG, "No session available, aborting send")
            Toast.makeText(this, R.string.err_session_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        // Add chat message
        addUserMessage(prompt)
        addThinkingLLMMessage()

        // Generate callback instance for inference
        val inferCallback = getStreamCallback()

        // Start inference
        try {
            val status = svc.inferStream(sessionId, prompt, MAX_TOKENS, inferCallback)
            Log.i(TAG, "inferStream started, sessionId=$sessionId status=$status")

            // Check if created session is available
            if (status < 0) {
                Log.e(TAG, "Failed to start inference: $status")

                // Session died between ensureSession() and this call
                // (e.g. evicted) — drop it so the next send re-creates
                if (status == IMINIVAIService.INFER_ERR_UNKNOWN_SESSION) {
                    forgetCurrentSession()
                    Toast.makeText(this, R.string.err_session_reset, Toast.LENGTH_SHORT).show()
                }

                // Reset state / UI
                resetInferState()
                resetInferUI()
                return
            }

            // Set state / UI as inference ongoing
            setInferOngoingState()
            setInferOngoingUI()
        } catch (e: Exception) {
            Log.e(TAG, "inferStream() failed", e)

            // Reset state / UI
            resetInferState()
            resetInferUI()
        }
    }

    /**
     * Generate callback listener instance
     */
    private fun getStreamCallback(): LLMStreamCallback {
        val listener =
                object : LLMStreamCallback.Listener {
                    override fun onToken(token: String) {
                        // Show generated token to output
                        runOnUiThread {
                            chatAdapter.appendTokenToLast(token)
                            scrollToBottom()
                        }
                    }

                    override fun onComplete() {
                        // Reset state / UI
                        resetInferState()
                        runOnUiThread { resetInferUI() }
                    }

                    override fun onError(code: Int, message: String) {
                        Log.e(TAG, "Inference error: code=$code msg=$message")

                        // Vendor already discarded this session
                        // — drop it locally so the next send starts a fresh conversation
                        if (code == ILLMStreamCallback.ERROR_SESSION_EVICTED) {
                            forgetCurrentSession()
                            runOnUiThread {
                                Toast.makeText(
                                                this@MainActivity,
                                                R.string.err_session_reset,
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                        }

                        // Reset state / UI
                        resetInferState()
                        runOnUiThread { resetInferUI() }
                    }
                }

        return LLMStreamCallback(listener)
    }

    // region - chat message management
    /**
     * Add a user message to the chat list
     */
    private fun addUserMessage(text: String) {
        chatAdapter.addMessage(
            ChatData.UserChatData(
                id = UUID.randomUUID(),
                message = text,
                timestamp = System.currentTimeMillis(),
            )
        )
        scrollToBottom()
    }

    /**
     * Add a thinking LLM message placeholder before tokens are streamed
     */
    private fun addThinkingLLMMessage() {
        chatAdapter.addMessage(
            ChatData.LLMThinkingData(
                id = UUID.randomUUID(),
                message = "Thinking...",
                timestamp = System.currentTimeMillis(),
            )
        )
        scrollToBottom()
    }

    /**
     * Scroll chat list to the latest message
     */
    private fun scrollToBottom() {
        rvChat.scrollToPosition(chatAdapter.itemCount - 1)
    }
    // endregion
}