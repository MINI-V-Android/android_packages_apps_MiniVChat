package com.miniv.chat

import android.util.Log
import com.miniv.ai.ILLMStreamCallback

/**
 * LLM Stream Callback
 * - Implementation of [ILLMStreamCallback.Stub] interface
 */
class LLMStreamCallback(
    private val listener: Listener,
): ILLMStreamCallback.Stub() {
        companion object {
        private const val TAG = "LLMStreamCallback"
    }

    // Listener interface for application
    interface Listener {
        /**
         * When token received
         */
        fun onToken(token: String)
        /**
         * When inference completed
         */
        fun onComplete()
        /**
         * When error occurred
         */
        fun onError(code: Int, message: String)
    }

    /**
     * When token received
     */
    override fun onToken(sessionId: Int, token: String) {
        Log.v(TAG, "onToken [$sessionId]: $token")
        listener.onToken(token)
    }

    /**
     * When inference completed
     */
    override fun onComplete(sessionId: Int) {
        Log.i(TAG, "onComplete [$sessionId]")
        listener.onComplete()
    }

    /**
     * When error occurred
     */
    override fun onError(sessionId: Int, code: Int, message: String) {
        Log.e(TAG, "onError [$sessionId] code=$code msg=$message")
        listener.onError(code, message)
    }
}