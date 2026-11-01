package fr.lucas.capri

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

// Les animations qu'il a importées lui-même (des bandes PNG : une image après l'autre, de gauche à droite).
// Sans elles, la vue dessine un petit compagnon néon original (voir CompanionView).
class Sprites(context: Context) {
    enum class Pose { STAND, PERCH, LIE, OVERLAY, STILL }

    data class Clip(val name: String, val frames: Int, val fps: Double, val loop: Boolean, val pose: Pose)

    // Le nom de fichier attendu, le nombre d'images de la bande, leur vitesse, et la posture.
    val clips: Map<String, Clip> = listOf(
        Clip("idle", 16, 8.0, true, Pose.STAND),
        Clip("blink", 4, 8.0, false, Pose.STAND),
        Clip("blink_glitch", 4, 8.0, false, Pose.STAND),
        Clip("walk", 12, 12.0, true, Pose.STAND),
        Clip("sleep", 16, 6.0, true, Pose.STAND),
        Clip("grin", 16, 8.0, false, Pose.STAND),
        Clip("glitch", 10, 15.0, false, Pose.STAND),
        Clip("dangle", 24, 10.0, true, Pose.STAND),
        Clip("wake", 13, 15.0, false, Pose.STAND),
        Clip("dance", 16, 12.0, true, Pose.STAND),
        Clip("arrive", 14, 15.0, false, Pose.STAND),
        Clip("leave", 12, 15.0, false, Pose.STAND),
        Clip("mouth", 5, 12.0, true, Pose.OVERLAY),
        Clip("perch", 60, 30.0, true, Pose.PERCH),
        Clip("perch_talk", 60, 30.0, true, Pose.PERCH),
        Clip("perch_music", 60, 30.0, true, Pose.PERCH),
        Clip("perch_blink", 8, 30.0, false, Pose.PERCH),
        Clip("perch_blink_glitch", 8, 30.0, false, Pose.PERCH),
        Clip("perch_glitch", 10, 15.0, false, Pose.PERCH),
        Clip("perch_arrive", 14, 15.0, false, Pose.PERCH),
        Clip("perch_leave", 12, 15.0, false, Pose.PERCH),
        Clip("laydown", 60, 30.0, true, Pose.LIE),
        Clip("laydown_sleep", 60, 30.0, true, Pose.LIE),
        Clip("laydown_talk", 60, 30.0, true, Pose.LIE),
        Clip("laydown_blink", 8, 30.0, false, Pose.LIE),
        Clip("laydown_blink_glitch", 8, 30.0, false, Pose.LIE),
        Clip("laydown_glitch", 10, 15.0, false, Pose.LIE),
        Clip("laydown_leave", 12, 15.0, false, Pose.LIE),
        Clip("face", 1, 1.0, true, Pose.STILL),
    ).associateBy { it.name }

    // Les noms de fichiers qu'il peut importer, avec leurs versions tournées vers la gauche (_flipped).
    val names: Set<String> get() = clips.keys + clips.keys.map { it + "_flipped" }

    val folder = File(context.filesDir, "sprites")
    // Les bandes chargées, les plus récentes en dernier ; au-delà de 6, les plus anciennes sont libérées.
    private val loaded = LinkedHashMap<String, Bitmap?>(16, 0.75f, true)

    // True quand au moins l'animation de base est importée.
    val ready: Boolean get() = File(folder, "idle.png").exists()

    fun has(name: String): Boolean = File(folder, "$name.png").exists()

    fun strip(name: String, keep: String? = null): Bitmap? {
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
        while (loaded.size > 6) {
            val oldest = loaded.keys.firstOrNull { it != name && it != keep } ?: break
            loaded.remove(oldest)?.recycle()
        }
        return bitmap
    }

    fun forget() {
        for (bitmap in loaded.values) bitmap?.recycle()
        loaded.clear()
    }
}
