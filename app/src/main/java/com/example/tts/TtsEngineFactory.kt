package com.example.tts

import com.example.data.TtsModelId

object TtsEngineFactory {
    fun create(modelId: TtsModelId): TtsEngine = when (modelId) {
        TtsModelId.KITTEN_NANO_V08 -> KittenTtsEngine()
        TtsModelId.KOKORO_EN_V019 -> KokoroTtsEngine()
        TtsModelId.SUPERTONIC_3_INT8 -> SupertonicTtsEngine()
    }
}
