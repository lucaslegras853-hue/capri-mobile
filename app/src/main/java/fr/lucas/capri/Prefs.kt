package fr.lucas.capri

import android.content.Context
import android.content.SharedPreferences

// Ses réglages, gardés sur le téléphone.
class Prefs(context: Context) {
    private val store: SharedPreferences = context.getSharedPreferences("capri", Context.MODE_PRIVATE)

    var key: String
        get() = store.getString("key", "") ?: ""
        set(value) = store.edit().putString("key", value.trim()).apply()

    var model: String
        get() = store.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = store.edit().putString("model", value.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var nickname: String
        get() = store.getString("nickname", "") ?: ""
        set(value) = store.edit().putString("nickname", value.trim()).apply()

    // Sa taille à l'écran, en dp.
    var size: Int
        get() = store.getInt("size", 150)
        set(value) = store.edit().putInt("size", value.coerceIn(80, 300)).apply()

    var popIns: Boolean
        get() = store.getBoolean("popIns", true)
        set(value) = store.edit().putBoolean("popIns", value).apply()

    var chatter: Boolean
        get() = store.getBoolean("chatter", true)
        set(value) = store.edit().putBoolean("chatter", value).apply()

    var remember: Boolean
        get() = store.getBoolean("remember", true)
        set(value) = store.edit().putBoolean("remember", value).apply()

    companion object {
        const val DEFAULT_MODEL = "nvidia/nemotron-3.5-super-vl-120b-a12b"
    }
}
