package fr.lucas.capri

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

// Les animations qu'il a importées lui-même (des bandes PNG, une image après l'autre, de gauche à droite).
// Sans elles, la vue dessine un petit compagnon néon original (voir CompanionView).
class Sprites(context: Context) {
    data class Clip(val name: String, val frames: Int, val fps: Double, val loop: Boolean)

    // Le nom de fichier attendu, le nombre d'images de la bande, et leur vitesse.
    val clips = mapOf(
        "idle" to Clip("idle", 16, 8.0, true),
        "blink" to Clip("blink", 4, 8.0, false),
        "walk" to Clip("walk", 12, 12.0, true),
        "sleep" to Clip("sleep", 16, 6.0, true),
        "grin" to Clip("grin", 16, 8.0, false),
        "glitch" to Clip("glitch", 10, 15.0, false),
        "dangle" to Clip("dangle", 24, 10.0, true),
        "wake" to Clip("wake", 13, 15.0, false),
    )

    val folder = File(context.filesDir, "sprites")
    private val loaded = HashMap<String, Bitmap?>()

    // True quand au moins l'animation de base est importée.
    val ready: Boolean get() = File(folder, "idle.png").exists()

    fun strip(name: String): Bitmap? {
        if (loaded.containsKey(name)) return loaded[name]
        val file = File(folder, "$name.png")
        val bitmap = if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inScaled = false })
            } catch (e: OutOfMemoryError) {
                null
            }
        } else null
        loaded[name] = bitmap
        return bitmap
    }

    fun has(name: String): Boolean = File(folder, "$name.png").exists()

    fun forget() {
        for (bitmap in loaded.values) bitmap?.recycle()
        loaded.clear()
    }
}
