package fr.lucas.capri

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.random.Random

// Le service qui la fait vivre par-dessus les autres applis : sa fenêtre flottante, ses balades, ses bulles.
class CapriService : Service() {

    private lateinit var windows: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var memory: Memory
    private lateinit var brain: Brain
    private lateinit var sprites: Sprites
    private lateinit var view: CompanionView
    private lateinit var params: WindowManager.LayoutParams
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val main = Handler(Looper.getMainLooper())

    private var widthPx = 0
    private var heightPx = 0
    private var walkTo = -1
    private var falling = false
    private var fallSpeed = 0f
    private var lastTouch = System.currentTimeMillis()
    private var nextWander = 0L
    private var nextPop = 0L
    private var nextChatter = 0L
    private var bubbleUntil = 0L
    private var thinking = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        memory = Memory(this)
        brain = Brain(prefs, memory)
        sprites = Sprites(this)
        windows = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addCompanion()
        val now = System.currentTimeMillis()
        nextWander = now + 8000
        nextPop = now + minutes(3, 6)
        nextChatter = now + minutes(12, 25)
        main.post(ticker)
        say(Lines.pick("wake"), 4000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        try { windows.removeView(view) } catch (e: Exception) { }
        bubble?.let { try { windows.removeView(it) } catch (e: Exception) { } }
        sprites.forget()
        instance = null
        super.onDestroy()
    }

    private fun minutes(from: Int, to: Int): Long = (from * 60_000L) + Random.nextLong((to - from) * 60_000L + 1)

    private fun dp(value: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun startAsForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("capri", "Capri", NotificationManager.IMPORTANCE_MIN)
        manager.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "capri")
            .setContentTitle("Capri est là")
            .setContentText("Touche pour ses réglages")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addCompanion() {
        val metrics = resources.displayMetrics
        widthPx = metrics.widthPixels
        heightPx = metrics.heightPixels
        val tall = dp(prefs.size)
        val wide = (tall * 0.75f).toInt()
        view = CompanionView(this, sprites)
        params = WindowManager.LayoutParams(
            wide, tall,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = widthPx - wide - dp(16)
        params.y = floorY()
        windows.addView(view, params)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var lastTap = 0L
        val longPress = Runnable { if (!moved) openApp() }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    main.postDelayed(longPress, 650)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && abs(dx) + abs(dy) > dp(8)) {
                        moved = true
                        main.removeCallbacks(longPress)
                        walkTo = -1
                        falling = false
                        view.play("dangle")
                    }
                    if (moved) {
                        params.x = (startX + dx).toInt().coerceIn(-params.width / 2, widthPx - params.width / 2)
                        params.y = (startY + dy).toInt().coerceIn(0, floorY())
                        update()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    touched()
                    if (moved) {
                        falling = params.y < floorY()
                        fallSpeed = 0f
                        if (!falling) view.play("idle")
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val now = System.currentTimeMillis()
                        if (now - lastTap < 350) {
                            lastTap = 0
                            petted()
                        } else {
                            lastTap = now
                            main.postDelayed({ if (lastTap == now) openChat() }, 360)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun floorY(): Int = heightPx - params.height - dp(24)

    private fun update() {
        try { windows.updateViewLayout(view, params) } catch (e: Exception) { }
        placeBubble()
    }

    private fun touched() {
        lastTouch = System.currentTimeMillis()
        if (view.clipName == "sleep") view.play("wake")
    }

    // Toutes les 33 ms : son animation, sa marche, sa chute, et ce qu'elle décide de faire.
    private val ticker = object : Runnable {
        override fun run() {
            step()
            view.invalidate()
            main.postDelayed(this, 33)
        }
    }

    private fun step() {
        val now = System.currentTimeMillis()
        if (falling) {
            fallSpeed += dp(1) * 1.6f
            params.y = (params.y + fallSpeed).toInt()
            if (params.y >= floorY()) {
                params.y = floorY()
                falling = false
                view.squash = 0.8f
                view.play("idle")
                if (fallSpeed > dp(20) && prefs.popIns) say(Lines.pick("drop"), 3000)
            }
            update()
        }
        if (view.squash < 1f) view.squash = (view.squash + 0.03f).coerceAtMost(1f)
        if (walkTo >= 0) {
            val gap = walkTo - params.x
            val speed = dp(2)
            if (abs(gap) <= speed) {
                params.x = walkTo
                walkTo = -1
                view.play("idle")
            } else {
                params.x += if (gap > 0) speed else -speed
                view.facingLeft = gap < 0
            }
            update()
        }
        if (view.finished()) view.play(if (view.clipName == "blink" || view.clipName == "wake" || view.clipName == "grin" || view.clipName == "glitch") "idle" else view.clipName)
        if (bubble != null && now > bubbleUntil && !thinking) hideBubble()

        val idleFor = now - lastTouch
        if (view.clipName != "sleep" && idleFor > 10 * 60_000L && walkTo < 0 && !falling) { view.play("sleep"); return }
        if (view.clipName == "sleep") return
        if (now >= nextWander && walkTo < 0 && !falling && view.clipName == "idle") {
            nextWander = now + 15_000L + Random.nextLong(30_000L)
            val roll = Random.nextDouble()
            when {
                roll < 0.55 -> {
                    walkTo = Random.nextInt(0, (widthPx - params.width).coerceAtLeast(1))
                    view.play("walk")
                }
                roll < 0.7 -> view.play("glitch")
                roll < 0.85 -> view.play("grin")
                else -> view.play("blink")
            }
        }
        if (prefs.popIns && now >= nextPop && bubble == null) {
            nextPop = now + minutes(4, 9)
            say(Lines.pick("pop"), 6000)
        }
        if (prefs.chatter && prefs.key.isNotEmpty() && now >= nextChatter && bubble == null && !thinking) {
            nextChatter = now + minutes(15, 30)
            val starts = listOf(
                "(Lance une petite conversation de toi-même : une question sur sa vie, en t'aidant de tes souvenirs. Une ou deux phrases courtes.)",
                "(Lance une petite conversation : reviens sur un truc qu'il t'a raconté avant. Une ou deux phrases courtes.)",
                "(Lance une petite conversation : partage une pensée au hasard, comme une amie, puis demande-lui son avis. Une ou deux phrases courtes.)",
                "(Lance une petite conversation : propose-lui de choisir entre deux options rigolotes que tu inventes. Une phrase courte.)"
            )
            ask(starts.random(), quiet = true)
        }
    }

    private fun petted() {
        view.play("grin")
        say(Lines.pick("pet"), 3500)
    }

    // Une question à son cerveau, en arrière-plan ; la réponse s'affiche dans sa bulle.
    fun ask(text: String, quiet: Boolean = false, done: ((String?, String?) -> Unit)? = null) {
        if (thinking) { done?.invoke(null, "Je réfléchis déjà, attends une seconde."); return }
        thinking = true
        if (!quiet) say("...", 60_000)
        Thread {
            var answer: String? = null
            var problem: String? = null
            try {
                answer = brain.reply(text)
                try { brain.foldIfNeeded() } catch (e: Exception) { }
            } catch (e: Exception) {
                problem = e.message ?: "Mon cerveau a planté."
            }
            val said = answer
            val trouble = problem
            main.post {
                thinking = false
                if (said != null) say(said, 4000L + said.length * 60L)
                else if (!quiet) say(trouble ?: "Mon cerveau a planté.", 5000)
                else hideBubble()
                done?.invoke(said, trouble)
            }
        }.start()
    }

    // Sa bulle de texte, au-dessus d'elle.
    fun say(text: String, millis: Long) {
        if (text.isBlank()) return
        bubbleUntil = System.currentTimeMillis() + millis
        var shown = bubble
        if (shown == null) {
            shown = TextView(this).apply {
                setTextColor(Color.rgb(232, 236, 240))
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setPadding(dp(12), dp(8), dp(12), dp(8))
                maxWidth = (widthPx * 0.7f).toInt()
                background = GradientDrawable().apply {
                    setColor(Color.argb(240, 9, 11, 15))
                    setStroke(dp(2), Color.rgb(58, 214, 235))
                    cornerRadius = dp(6).toFloat()
                }
            }
            val layout = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            layout.gravity = Gravity.TOP or Gravity.START
            bubble = shown
            bubbleParams = layout
            shown.text = text
            placeBubble()
            try { windows.addView(shown, layout) } catch (e: Exception) { bubble = null; return }
        } else {
            shown.text = text
            placeBubble()
        }
    }

    private fun placeBubble() {
        val shown = bubble ?: return
        val layout = bubbleParams ?: return
        shown.measure(View.MeasureSpec.makeMeasureSpec((widthPx * 0.7f).toInt(), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
        val w = shown.measuredWidth
        val h = shown.measuredHeight
        layout.x = (params.x + params.width / 2 - w / 2).coerceIn(dp(8), (widthPx - w - dp(8)).coerceAtLeast(dp(8)))
        layout.y = (params.y - h - dp(4)).coerceAtLeast(dp(24))
        if (shown.isAttachedToWindow) try { windows.updateViewLayout(shown, layout) } catch (e: Exception) { }
    }

    private fun hideBubble() {
        val shown = bubble ?: return
        bubble = null
        bubbleParams = null
        try { windows.removeView(shown) } catch (e: Exception) { }
    }

    private fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        var instance: CapriService? = null
            private set
    }
}
