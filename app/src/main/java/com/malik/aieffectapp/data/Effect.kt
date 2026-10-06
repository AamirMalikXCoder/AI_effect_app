package com.malik.aieffectapp.data

/** The four v1 effects. Video effects share one backend integration. */
enum class EffectType { VIDEO, IMAGE }

data class Effect(
    val id: String,          // matches Cloud Functions VIDEO_PROMPTS keys
    val name: String,
    val type: EffectType,
    val tagline: String,
    val emoji: String,
)

val EFFECTS = listOf(
    Effect("hug", "AI Hug", EffectType.VIDEO, "Make two people hug", "🤗"),
    Effect("kiss", "AI Kiss", EffectType.VIDEO, "A romantic movie moment", "💋"),
    Effect("dance", "AI Dance", EffectType.VIDEO, "Bring any photo to life", "💃"),
    Effect("anime", "Anime Me", EffectType.IMAGE, "Ghibli-style portrait", "🎨"),
)

fun effectById(id: String): Effect = EFFECTS.first { it.id == id }
