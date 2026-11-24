package com.example.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

interface VoiceCueSpeaker {
    fun speak(text: String, interrupt: Boolean = false)
    fun stop()
    fun release()
}

class AndroidVoiceCueSpeaker(
    context: Context
) : VoiceCueSpeaker {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val lock = Any()
    private var tts: TextToSpeech? = null
    private var isInitializing = false
    private var isReady = false
    private var isReleased = false
    private var pendingCue: Pair<String, Boolean>? = null
    private var activeUtteranceId: String? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    override fun speak(text: String, interrupt: Boolean) {
        if (text.isBlank()) return
        synchronized(lock) {
            if (isReleased) return
            if (tts == null && !isInitializing) {
                pendingCue = text to interrupt
                initTtsLocked()
                return
            }
            if (!isReady) {
                pendingCue = text to interrupt
                return
            }
            dispatchSpeakLocked(text, interrupt)
        }
    }

    override fun stop() {
        synchronized(lock) {
            pendingCue = null
            activeUtteranceId = null
            runCatching { tts?.stop() }
            abandonAudioFocusLocked()
        }
    }

    override fun release() {
        synchronized(lock) {
            if (isReleased) return
            isReleased = true
            isInitializing = false
            isReady = false
            pendingCue = null
            activeUtteranceId = null
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
            tts = null
            abandonAudioFocusLocked()
        }
    }

    private fun initTtsLocked() {
        isInitializing = true
        val created = runCatching {
            TextToSpeech(appContext) { status ->
                onTtsInitialized(status)
            }
        }.getOrNull()

        if (created == null) {
            isInitializing = false
            isReady = false
            pendingCue = null
            return
        }

        runCatching {
            created.setAudioAttributes(speechAttributes)
            created.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        clearUtteranceFocus(utteranceId)
                    }

                    @Deprecated("Deprecated in Android API", ReplaceWith("onError(utteranceId, errorCode)"))
                    override fun onError(utteranceId: String?) {
                        clearUtteranceFocus(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        clearUtteranceFocus(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        clearUtteranceFocus(utteranceId)
                    }
                }
            )
        }
        tts = created
    }

    private fun onTtsInitialized(status: Int) {
        synchronized(lock) {
            isInitializing = false
            val engine = tts
            if (isReleased || engine == null || status != TextToSpeech.SUCCESS) {
                isReady = false
                pendingCue = null
                return
            }

            runCatching {
                val defaultLang = engine.setLanguage(Locale.getDefault())
                if (defaultLang == TextToSpeech.LANG_MISSING_DATA ||
                    defaultLang == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.US)
                }
                engine.setAudioAttributes(speechAttributes)
            }

            isReady = true
            val queued = pendingCue
            pendingCue = null
            if (queued != null) {
                dispatchSpeakLocked(queued.first, queued.second)
            }
        }
    }

    private fun dispatchSpeakLocked(text: String, interrupt: Boolean) {
        val engine = tts ?: return
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        requestTransientDuckFocusLocked()

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = runCatching {
            engine.speak(text, queueMode, params, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        if (result == TextToSpeech.ERROR) {
            activeUtteranceId = null
            abandonAudioFocusLocked()
        }
    }

    private fun clearUtteranceFocus(utteranceId: String?) {
        synchronized(lock) {
            if (utteranceId == null || utteranceId == activeUtteranceId) {
                activeUtteranceId = null
                abandonAudioFocusLocked()
            }
        }
    }

    private fun requestTransientDuckFocusLocked() {
        val manager = audioManager ?: return
        if (activeFocusRequest != null) return
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener {}
            .build()
        val granted = runCatching {
            manager.requestAudioFocus(focusRequest)
        }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            activeFocusRequest = focusRequest
        }
    }

    private fun abandonAudioFocusLocked() {
        val manager = audioManager ?: return
        val request = activeFocusRequest ?: return
        activeFocusRequest = null
        runCatching { manager.abandonAudioFocusRequest(request) }
    }
}
