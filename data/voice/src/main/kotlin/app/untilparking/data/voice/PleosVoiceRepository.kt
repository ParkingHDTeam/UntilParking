package app.untilparking.data.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import app.untilparking.domain.AppError
import app.untilparking.domain.repository.VoiceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttServerListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsServerListener

/**
 * Pleos STT·TTS SDK 어댑터.
 * My Project의 Client ID·Secret이 있으면 HYBRID(서버 엔진, 끊기면 온디바이스), 없으면 ON_DEVICE로 연다.
 * 자격 증명은 저장소에 두지 않고 local.properties → BuildConfig로 app 모듈이 넘긴다.
 */
class PleosVoiceRepository(
    context: Context,
    private val clientId: String,
    private val clientSecret: String,
) : VoiceRepository {

    private val hasCredentials = clientId.isNotBlank() && clientSecret.isNotBlank()
    private val tts = TextToSpeech(context.applicationContext, if (hasCredentials) TtsMode.HYBRID else TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context.applicationContext, if (hasCredentials) SttMode.HYBRID else SttMode.ON_DEVICE)
    private val ttsReady = CompletableDeferred<Boolean>()
    private val sttReady = CompletableDeferred<Boolean>()

    init {
        // 권한이 없거나 음성 서비스가 없는 기기에서는 SDK가 SecurityException을 바로 던진다.
        // 앱을 멈추지 않고 음성만 끈다(speak는 VoiceFailed, listen은 null).
        startTts()
        startStt()
    }

    private fun startTts() = guard(ttsReady) {
        tts.initialize(object : TtsInitListener {
            override fun onSuccess() {
                if (!hasCredentials) {
                    ttsReady.complete(true)
                    return
                }
                tts.registerApp(clientId, clientSecret, object : TtsServerListener {
                    override fun onConnected() { ttsReady.complete(true) }
                    // 서버 연결이 안 돼도 HYBRID는 온디바이스로 이어 간다
                    override fun onFailed(errorType: TtsErrorType) { ttsReady.complete(true) }
                })
            }

            override fun onFailure(errorType: TtsErrorType) { ttsReady.complete(false) }
        })
    }

    private fun startStt() = guard(sttReady) {
        stt.initialize(object : SttInitListener {
            override fun onSuccess() {
                if (!hasCredentials) {
                    sttReady.complete(true)
                    return
                }
                stt.registerApp(clientId, clientSecret, object : SttServerListener {
                    override fun onConnected() { sttReady.complete(true) }
                    override fun onFailed(errorType: SttErrorType) { sttReady.complete(true) }
                })
            }

            override fun onFailure(errorType: SttErrorType) { sttReady.complete(false) }
        })
    }

    private inline fun guard(ready: CompletableDeferred<Boolean>, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            ready.complete(false)
        }
    }

    override suspend fun speak(text: String) {
        if (withTimeoutOrNull(READY_TIMEOUT_MS) { ttsReady.await() } != true) throw AppError.VoiceFailed("TTS 초기화 실패")
        suspendCancellableCoroutine { cont ->
            val listener = object : EventListener {
                override fun onStart() = Unit
                override fun onReady() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onDone() = finish(null)
                override fun onStop() = finish(null)
                override fun onError(errorType: TtsErrorType) = finish(AppError.VoiceFailed(errorType.toString()))

                private fun finish(error: Throwable?) {
                    tts.removeEventListener(this)
                    if (!cont.isActive) return
                    if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
                }
            }
            cont.invokeOnCancellation {
                tts.stop()
                tts.removeEventListener(listener)
            }
            try {
                tts.addEventListener(listener)
                tts.setLocale(Locale.KOREA)
                tts.speak(text, true)
            } catch (e: SecurityException) {
                tts.removeEventListener(listener)
                cont.resumeWithException(AppError.VoiceFailed("TTS 권한 없음", e))
            }
        }
    }

    override suspend fun listen(): String? {
        if (withTimeoutOrNull(READY_TIMEOUT_MS) { sttReady.await() } != true) return null
        return withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : ResultListener {
                    override fun onReady() = Unit
                    override fun onStartedRecognition() = Unit
                    override fun onEndedRecognition() = Unit
                    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onUpdated(stt: String, completed: Boolean) {
                        if (completed) finish(stt)
                    }

                    override fun onError(errorType: SttErrorType) = finish(null)

                    private fun finish(result: String?) {
                        this@PleosVoiceRepository.stt.removeListener(this)
                        if (cont.isActive) cont.resume(result)
                    }
                }
                cont.invokeOnCancellation {
                    stt.stop()
                    stt.removeListener(listener)
                }
                // 결과를 놓치지 않게 request() 전에 리스너를 단다 (SDK 문서)
                try {
                    stt.addListener(listener)
                    stt.setLocale(Locale.KOREA)
                    stt.request()
                } catch (e: SecurityException) {
                    stt.removeListener(listener)
                    cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val READY_TIMEOUT_MS = 5_000L
        /** 스펙 R10 "무음 10초 뒤 null" [가설]. mock(MockEnvironment.listen)과 같은 값으로 맞춘다. */
        const val LISTEN_TIMEOUT_MS = 10_000L
    }
}
