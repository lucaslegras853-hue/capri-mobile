package fr.lucas.capri

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

// Ce qui est dessiné dans sa petite fenêtre flottante : l'image courante de son animation.
@SuppressLint("ViewConstructor")
class CompanionView(context: Context, private val sprites: Sprites) : View(context) {
    var clipName = "idle"
        private set
    private var clipStart = System.nanoTime()
    var facingLeft = false
    var squash = 1f
    // Debout et en train de parler : sa bouche bouge par-dessus son image.
    var talking = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    fun play(name: String) {
        if (name == clipName) return
        clipName = name
        clipStart = System.nanoTime()
    }

    private fun seconds(): Double = (System.nanoTime() - clipStart) / 1e9

    // True quand une animation qui ne boucle pas est arrivée au bout.
    fun finished(): Boolean {
        val clip = sprites.clips[clipName] ?: return true
        if (clip.loop) return false
        if (!sprites.ready || !sprites.has(clipName)) return seconds() > 0.8
        return seconds() * clip.fps >= clip.frames
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val clip = sprites.clips[clipName]
        if (clip == null || !sprites.ready) {
            canvas.save()
            canvas.scale(if (facingLeft) -1f else 1f, squash, width / 2f, height.toFloat())
            drawOwn(canvas)
            canvas.restore()
            return
        }
        // Tournée vers la gauche : la version dessinée par l'artiste si elle est là, sinon l'image retournée.
        val usable = if (sprites.has(clipName)) clip else sprites.clips.getValue("idle")
        val flipped = facingLeft && sprites.has(usable.name + "_flipped")
        val strip = sprites.strip(if (flipped) usable.name + "_flipped" else usable.name) ?: return
        canvas.save()
        canvas.scale(if (facingLeft && !flipped) -1f else 1f, squash, width / 2f, height.toFloat())
        val cell = strip.width / usable.frames
        var frame = (seconds() * usable.fps).toInt()
        frame = if (usable.loop) frame % usable.frames else frame.coerceAtMost(usable.frames - 1)
        src.set(frame * cell, 0, (frame + 1) * cell, strip.height)
        // Allongée, son image est moins haute : 250 pixels sur 384.
        val tall = if (usable.pose == Sprites.Pose.LIE) height * 250f / 384f else height.toFloat()
        val wide = cell * tall / strip.height
        dst.set((width - wide) / 2f, height - tall, (width + wide) / 2f, height.toFloat())
        canvas.drawBitmap(strip, src, dst, paint)
        if (talking && usable.pose == Sprites.Pose.STAND && (usable.name == "idle" || usable.name == "blink") && sprites.has("mouth")) {
            val mouth = sprites.strip("mouth", keep = usable.name)
            if (mouth != null) {
                val mouthCell = mouth.width / 5
                val mouthFrame = ((System.nanoTime() / 1e9) * 12).toInt() % 5
                src.set(mouthFrame * mouthCell, 0, (mouthFrame + 1) * mouthCell, mouth.height)
                canvas.drawBitmap(mouth, src, dst, paint)
            }
        }
        canvas.restore()
    }

    // Le compagnon néon par défaut (un petit robot original), tant qu'aucune animation n'est importée.
    private fun drawOwn(canvas: Canvas) {
        val t = (System.nanoTime() / 1e9)
        val w = width.toFloat()
        val h = height.toFloat()
        val bob = if (clipName.contains("sleep")) 0f else (sin(t * 2.2) * h * 0.03).toFloat()
        val glitch = clipName.contains("glitch") && ((t * 20).toInt() % 2 == 0)
        val body = RectF(w * 0.18f, h * 0.38f + bob, w * 0.82f, h * 0.86f + bob)
        paint.style = Paint.Style.FILL
        paint.color = if (glitch) Color.rgb(58, 214, 235) else Color.rgb(255, 46, 136)
        canvas.drawRoundRect(body, w * 0.12f, w * 0.12f, paint)
        paint.color = Color.rgb(11, 13, 18)
        val screen = RectF(body.left + w * 0.07f, body.top + h * 0.06f, body.right - w * 0.07f, body.bottom - h * 0.08f)
        canvas.drawRoundRect(screen, w * 0.06f, w * 0.06f, paint)
        paint.color = Color.rgb(60, 240, 140)
        paint.strokeWidth = w * 0.025f
        canvas.drawLine(w * 0.5f, body.top, w * 0.5f, body.top - h * 0.14f, paint)
        canvas.drawCircle(w * 0.5f, body.top - h * 0.16f, w * 0.05f, paint)
        paint.color = Color.rgb(58, 214, 235)
        val closed = clipName.contains("sleep") || clipName.contains("blink") || ((t % 4.0) < 0.12)
        val eyeH = if (closed) h * 0.012f else h * 0.07f
        val eyeY = screen.centerY() - h * 0.01f
        if (clipName == "grin") {
            paint.style = Paint.Style.STROKE
            canvas.drawArc(RectF(w * 0.32f, eyeY - h * 0.04f, w * 0.44f, eyeY + h * 0.04f), 200f, 140f, false, paint)
            canvas.drawArc(RectF(w * 0.56f, eyeY - h * 0.04f, w * 0.68f, eyeY + h * 0.04f), 200f, 140f, false, paint)
            paint.style = Paint.Style.FILL
        } else {
            canvas.drawRect(w * 0.34f, eyeY - eyeH / 2, w * 0.42f, eyeY + eyeH / 2, paint)
            canvas.drawRect(w * 0.58f, eyeY - eyeH / 2, w * 0.66f, eyeY + eyeH / 2, paint)
        }
        if (talking) {
            paint.color = Color.rgb(255, 46, 136)
            val open = ((t * 8).toInt() % 2) * h * 0.02f
            canvas.drawRect(w * 0.45f, eyeY + h * 0.07f, w * 0.55f, eyeY + h * 0.08f + open, paint)
        }
        if (clipName == "walk" || clipName == "dance") {
            paint.color = Color.rgb(255, 46, 136)
            val step = (sin(t * 10) * w * 0.05).toFloat()
            canvas.drawRect(w * 0.34f + step, body.bottom, w * 0.42f + step, body.bottom + h * 0.08f, paint)
            canvas.drawRect(w * 0.58f - step, body.bottom, w * 0.66f - step, body.bottom + h * 0.08f, paint)
        }
        if (clipName.contains("sleep")) {
            paint.color = Color.rgb(58, 214, 235)
            paint.textSize = h * 0.12f
            canvas.drawText("z", w * 0.78f, h * 0.3f - ((t * 10).toInt() % 20).toFloat(), paint)
        }
    }
}
