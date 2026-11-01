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
import android.media.AudioManager
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
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// Le service qui la fait vivre par-dessus les autres applis.
// Elle se tient sur un « rebord » : le bas de l'écran, le haut du clavier, d'un bouton, d'une icône ou d'un message
// (trouvés par ses Yeux, le service d'accessibilité). Elle y marche, s'y assoit, saute de l'un à l'autre,
// flotte dans les airs, se téléporte, danse quand de la musique joue, et réagit aux applis que tu ouvres.
class CapriService : Service() {

    private enum class Mode { STAND, WALK, SIT, LIE, JUMP, GLIDE, HOVER, FALL, DRAG, LEAVE, DANCE, SLEEP }

    private lateinit var windows: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var memory: Memory
    private lateinit var brain: Brain
    private lateinit var sprites: Sprites
    private lateinit var view: CompanionView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var audio: AudioManager
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val main = Handler(Looper.getMainLooper())

    private var widthPx = 0
    private var heightPx = 0
    private var mode = Mode.STAND
    // Ses pieds (debout) ou sa hanche (assise), en coordonnées d'écran.
    private var footX = 0f
    private var footY = 0f
    private var ledge: Eyes.Ledge? = null
    private var walkTo = 0f
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var toLedge: Eyes.Ledge? = null
    private var moveStart = 0L
    private var moveTime = 1L
    private var arc = 0f
    private var fallSpeed = 0f
    private var lastTouch = System.currentTimeMillis()
    private var nextMove = 0L
    private var nextPop = 0L
    private var nextChatter = 0L
    private var nextCheck = 0L
    private var hoverUntil = 0L
    private var bubbleUntil = 0L
    private var thinking = false
    private var reactedApp = ""
    private var nextReaction = 0L
    private var baseWide = 0
    private var sitSince = 0L
    private var lieUntil = 0L
    private var nextBlink = 0L
    // Avant de danser ou de dormir : assise ou allongée, pour garder la même posture.
    private var restPose = Mode.STAND

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        memory = Memory(this)
        brain = Brain(prefs, memory)
        sprites = Sprites(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windows = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addCompanion()
        val now = System.currentTimeMillis()
        nextMove = now + 4000
        nextPop = now + minutes(3, 6)
        nextChatter = now + minutes(12, 25)
        reactedApp = Eyes.app
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
        manager.createNotificationChannel(NotificationChannel("capri", "Capri", NotificationManager.IMPORTANCE_MIN))
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

    // ---------- Sa fenêtre et le toucher ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun addCompanion() {
        val metrics = resources.displayMetrics
        widthPx = metrics.widthPixels
        heightPx = metrics.heightPixels
        val tall = dp(prefs.size)
        val wide = (tall * 0.75f).toInt()
        baseWide = wide
        view = CompanionView(this, sprites)
        params = WindowManager.LayoutParams(
            wide, tall,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        footX = widthPx - wide / 2f - dp(16)
        footY = floorY()
        place()
        windows.addView(view, params)

        var downX = 0f
        var downY = 0f
        var grabX = 0f
        var grabY = 0f
        var moved = false
        var lastTap = 0L
        val longPress = Runnable { if (!moved) openApp() }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    grabX = footX; grabY = footY
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
                        if (pose() == Sprites.Pose.PERCH) footY += params.height * 0.21f
                        grabY = footY
                        setMode(Mode.DRAG)
                    }
                    if (moved) {
                        footX = (grabX + dx).coerceIn(0f, widthPx.toFloat())
                        footY = (grabY + dy).coerceIn(params.height.toFloat(), floorY())
                        place()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    touched()
                    if (moved) {
                        ledge = null
                        fallSpeed = 0f
                        setMode(Mode.FALL)
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

    // Le « sol » : le bas de l'écran (au-dessus de la barre de navigation).
    private fun floorY(): Float = (heightPx - dp(8)).toFloat()

    // Place sa fenêtre selon ses pieds (ou sa hanche si elle est assise).
    // Sa posture actuelle : debout, assise (perch) ou allongée (laydown).
    private fun pose(): Sprites.Pose {
        val shown = sprites.clips[view.clipName]?.pose
        if (shown == Sprites.Pose.PERCH && sprites.has(view.clipName)) return Sprites.Pose.PERCH
        if (shown == Sprites.Pose.LIE && sprites.has(view.clipName)) return Sprites.Pose.LIE
        return Sprites.Pose.STAND
    }

    private fun place() {
        val h = params.height
        val lying = pose() == Sprites.Pose.LIE
        val w = if (lying) (h * 1.05f).toInt() else baseWide
        if (params.width != w) params.width = w
        params.x = (footX - w / 2f).toInt()
        params.y = if (pose() == Sprites.Pose.PERCH) (footY - h * 0.79f).toInt() else (footY - h).toInt()
        try { if (view.isAttachedToWindow) windows.updateViewLayout(view, params) } catch (e: Exception) { }
        placeBubble()
    }

    private fun setMode(next: Mode) {
        val was = mode
        if (next == Mode.DANCE || next == Mode.SLEEP) restPose = if (was == Mode.SIT || was == Mode.LIE) was else if (was == Mode.DANCE || was == Mode.SLEEP) restPose else Mode.STAND
        if (next == Mode.SIT && was != Mode.SIT) sitSince = System.currentTimeMillis()
        mode = next
        view.play(
            when (next) {
                Mode.LEAVE -> when {
                    was == Mode.SIT && sprites.has("perch_leave") -> "perch_leave"
                    was == Mode.LIE && sprites.has("laydown_leave") -> "laydown_leave"
                    sprites.has("leave") -> "leave"
                    else -> "glitch"
                }
                else -> loopClip()
            }
        )
        place()
    }

    private fun pick(vararg names: String): String = names.firstOrNull { sprites.has(it) } ?: names.last()

    // L'animation en boucle qui va avec ce qu'elle fait (et avec le fait qu'elle parle).
    private fun loopClip(): String {
        val talking = bubble != null
        return when (mode) {
            Mode.STAND, Mode.HOVER -> "idle"
            Mode.WALK, Mode.GLIDE -> "walk"
            Mode.SIT -> if (talking) pick("perch_talk", "perch", "idle") else pick("perch", "idle")
            Mode.LIE -> if (talking) pick("laydown_talk", "laydown", "perch", "idle") else pick("laydown", "perch", "idle")
            Mode.JUMP, Mode.FALL, Mode.DRAG -> "dangle"
            Mode.LEAVE -> "idle"
            Mode.DANCE -> if (restPose == Mode.SIT || restPose == Mode.LIE) pick("perch_music", "dance", "grin") else pick("dance", "grin")
            Mode.SLEEP -> if (restPose == Mode.LIE) pick("laydown_sleep", "sleep") else "sleep"
        }
    }

    // Une petite animation ponctuelle (clignement, glitch) dans sa posture du moment.
    private fun oneShot(kind: String) {
        val glitchy = Random.nextInt(5) == 0
        view.play(
            when (mode) {
                Mode.SIT -> if (kind == "glitch") pick("perch_glitch", "glitch") else if (glitchy) pick("perch_blink_glitch", "perch_blink", "blink") else pick("perch_blink", "blink")
                Mode.LIE -> if (kind == "glitch") pick("laydown_glitch", "glitch") else if (glitchy) pick("laydown_blink_glitch", "laydown_blink", "blink") else pick("laydown_blink", "blink")
                else -> if (kind == "glitch") "glitch" else if (kind == "grin") "grin" else if (glitchy) pick("blink_glitch", "blink") else "blink"
            }
        )
    }

    private fun touched() {
        lastTouch = System.currentTimeMillis()
        if (mode == Mode.SLEEP) { setMode(Mode.STAND); view.play("wake") }
    }

    // ---------- La boucle (33 ms) ----------

    private val ticker = object : Runnable {
        override fun run() {
            try { step() } catch (e: Exception) { }
            view.invalidate()
            main.postDelayed(this, 33)
        }
    }

    private fun step() {
        val now = System.currentTimeMillis()
        val h = params.height.toFloat()
        val w = params.width.toFloat()
        if (view.squash < 1f) view.squash = (view.squash + 0.03f).coerceAtMost(1f)
        when (mode) {
            Mode.WALK -> {
                val gap = walkTo - footX
                val speed = dp(2).toFloat()
                if (abs(gap) <= speed) { footX = walkTo; setMode(Mode.STAND) } else { footX += if (gap > 0) speed else -speed; view.facingLeft = gap < 0 }
                place()
            }
            Mode.JUMP, Mode.GLIDE -> {
                val p = min(1f, (now - moveStart).toFloat() / moveTime)
                val eased = if (mode == Mode.GLIDE) p * p * (3 - 2 * p) else p
                footX = fromX + (toX - fromX) * eased
                footY = fromY + (toY - fromY) * eased - (if (mode == Mode.JUMP) 4 * arc * p * (1 - p) else 0f)
                place()
                if (p >= 1f) {
                    if (mode == Mode.JUMP) land(toLedge)
                    else { setMode(Mode.HOVER); hoverUntil = now + 3000 + Random.nextLong(6000) }
                }
            }
            Mode.HOVER -> if (now > hoverUntil) { fallSpeed = 0f; setMode(Mode.FALL) }
            Mode.FALL, Mode.DRAG -> if (mode == Mode.FALL) {
                fallSpeed += dp(1) * 1.6f
                val below = ledgeBelow(footX, footY, footY + fallSpeed)
                val stop = below?.top?.toFloat() ?: floorY()
                if (footY + fallSpeed >= stop) {
                    footY = stop
                    if (fallSpeed > dp(20) && prefs.popIns && bubble == null && Random.nextInt(3) == 0) say(Lines.pick("drop"), 3000)
                    land(below)
                } else footY += fallSpeed
                place()
            }
            Mode.LEAVE -> if (view.finished()) {
                // Elle réapparaît ailleurs.
                val spot = randomSpot(w)
                ledge = spot.second
                footX = spot.first
                footY = spot.second?.top?.toFloat() ?: floorY()
                setMode(Mode.STAND)
                if (sprites.has("arrive")) view.play("arrive")
                place()
                nextMove = System.currentTimeMillis() + 4000
            }
            else -> {}
        }
        if (bubble != null && now > bubbleUntil && !thinking) hideBubble()
        // Fin d'une animation ponctuelle, ou elle commence / arrête de parler : retour à sa boucle.
        val loop = sprites.clips[view.clipName]?.loop ?: true
        if (mode != Mode.LEAVE && ((!loop && view.finished()) || (loop && view.clipName != loopClip()))) {
            val before = pose()
            view.play(loopClip())
            if (pose() != before) place()
        }
        view.talking = bubble != null && (mode == Mode.STAND || mode == Mode.HOVER)
        if (now >= nextBlink && (mode == Mode.STAND || mode == Mode.SIT || mode == Mode.LIE) && sprites.clips[view.clipName]?.loop == true) {
            nextBlink = now + 3000 + Random.nextLong(4000)
            oneShot("blink")
        }
        if (mode == Mode.LIE && now > lieUntil && bubble == null) setMode(if (sprites.has("perch")) Mode.SIT else Mode.STAND)

        // Toutes les 300 ms : son rebord existe-t-il encore ? de la musique ? une nouvelle appli ?
        if (now >= nextCheck) {
            nextCheck = now + 300
            val on = ledge
            if ((mode == Mode.STAND || mode == Mode.WALK || mode == Mode.SIT || mode == Mode.LIE || mode == Mode.DANCE || mode == Mode.SLEEP) && on != null && !stillThere(on)) {
                ledge = null
                fallSpeed = 0f
                setMode(Mode.FALL)
            }
            val music = try { audio.isMusicActive } catch (e: Exception) { false }
            if (music && (mode == Mode.STAND || mode == Mode.SIT || mode == Mode.LIE) && (sprites.has("dance") || sprites.has("perch_music"))) setMode(Mode.DANCE)
            if (!music && mode == Mode.DANCE) setMode(restPose)
            react(now)
        }

        if (mode != Mode.SLEEP && now - lastTouch > 10 * 60_000L && (mode == Mode.STAND || mode == Mode.SIT || mode == Mode.LIE)) { setMode(Mode.SLEEP); return }
        if (mode == Mode.SLEEP) return
        if (now >= nextMove && (mode == Mode.STAND || mode == Mode.SIT || (mode == Mode.LIE && now > lieUntil))) {
            nextMove = now + 6_000L + Random.nextLong(9_000L)
            decide(now, w, h)
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

    // Ce qu'elle décide de faire ensuite.
    private fun decide(now: Long, w: Float, h: Float) {
        val roll = Random.nextDouble()
        val on = ledge
        val left = (on?.left?.toFloat() ?: 0f) + w * 0.35f
        val right = (on?.right?.toFloat() ?: widthPx.toFloat()) - w * 0.35f
        val lieRoom = on != null && on.right - on.left >= h * 1.1f
        if (mode == Mode.SIT && now - sitSince > 20_000 && lieRoom && sprites.has("laydown") && Random.nextDouble() < 0.4) {
            lieUntil = now + 60_000L + Random.nextLong(120_000L)
            setMode(Mode.LIE)
            return
        }
        when {
            roll < 0.28 && right - left > w * 0.5f -> {
                if (mode == Mode.SIT || mode == Mode.LIE) setMode(Mode.STAND)
                walkTo = left + Random.nextFloat() * (right - left)
                setMode(Mode.WALK)
            }
            roll < 0.55 -> jump(randomSpot(w))
            roll < 0.68 -> glide()
            roll < 0.76 -> setMode(Mode.LEAVE)
            roll < 0.88 && mode == Mode.STAND && sprites.has("perch") -> setMode(Mode.SIT)
            roll < 0.94 -> if (mode == Mode.STAND) oneShot("grin") else oneShot("glitch")
            else -> oneShot(if (Random.nextBoolean()) "glitch" else "blink")
        }
    }

    // Un endroit au hasard où atterrir : un rebord trouvé par ses Yeux, ou le sol.
    private fun randomSpot(w: Float): Pair<Float, Eyes.Ledge?> {
        val options = usableLedges(w)
        if (options.isNotEmpty() && Random.nextDouble() < 0.85) {
            val pick = options.random()
            val x = pick.left + w * 0.35f + Random.nextFloat() * max(1f, pick.right - pick.left - w * 0.7f)
            return Pair(x, pick)
        }
        return Pair(w / 2 + Random.nextFloat() * (widthPx - w), null)
    }

    private fun usableLedges(w: Float): List<Eyes.Ledge> {
        val h = params.height
        val top = dp(40)
        return Eyes.ledges.filter { it.right - it.left >= w * 0.7f && it.top - h * 0.8f > top && it.top < floorY() - dp(20) }
    }

    private fun stillThere(on: Eyes.Ledge): Boolean {
        if (!Eyes.running) return true
        return Eyes.ledges.any { abs(it.top - on.top) <= dp(6) && it.left <= footX && it.right >= footX }
    }

    private fun ledgeBelow(x: Float, from: Float, to: Float): Eyes.Ledge? {
        var best: Eyes.Ledge? = null
        for (candidate in usableLedges(params.width.toFloat())) {
            if (x < candidate.left || x > candidate.right) continue
            if (candidate.top < from || candidate.top > to) continue
            if (best == null || candidate.top < best.top) best = candidate
        }
        return best
    }

    private fun jump(spot: Pair<Float, Eyes.Ledge?>) {
        if (mode == Mode.SIT || mode == Mode.LIE) setMode(Mode.STAND)
        fromX = footX; fromY = footY
        toX = spot.first
        toY = spot.second?.top?.toFloat() ?: floorY()
        toLedge = spot.second
        val distance = abs(toX - fromX) + abs(toY - fromY)
        if (distance < dp(30)) return
        moveStart = System.currentTimeMillis()
        moveTime = (500 + distance / dp(1) * 1.5f).toLong().coerceIn(600, 1400)
        arc = if (toY < fromY) (fromY - toY) + dp(60) else dp(70).toFloat()
        view.facingLeft = toX < fromX
        setMode(Mode.JUMP)
    }

    private fun glide() {
        if (mode == Mode.SIT || mode == Mode.LIE) setMode(Mode.STAND)
        val h = params.height
        fromX = footX; fromY = footY
        toX = params.width / 2f + Random.nextFloat() * (widthPx - params.width)
        toY = h + dp(40) + Random.nextFloat() * max(1f, floorY() - h - dp(120))
        moveStart = System.currentTimeMillis()
        moveTime = ((abs(toX - fromX) + abs(toY - fromY)) / dp(1) * 9f).toLong().coerceIn(1500, 7000)
        view.facingLeft = toX < fromX
        ledge = null
        setMode(Mode.GLIDE)
    }

    private fun land(on: Eyes.Ledge?) {
        ledge = on
        footY = on?.top?.toFloat() ?: floorY()
        view.squash = 0.8f
        val sit = on != null && !on.keyboard && sprites.has("perch") && Random.nextDouble() < 0.5
        setMode(if (sit) Mode.SIT else Mode.STAND)
        if (sit && sprites.has("perch_arrive")) view.play("perch_arrive")
        place()
    }

    // Elle réagit quand tu ouvres une nouvelle appli ou un jeu (une fois qu'il est resté 12 secondes devant).
    private fun react(now: Long) {
        val app = Eyes.app
        if (app.isEmpty() || app == reactedApp || now - Eyes.appSince < 12_000 || now < nextReaction) return
        if (!prefs.chatter || prefs.key.isEmpty() || thinking || bubble != null) return
        reactedApp = app
        if (Random.nextDouble() > 0.7) return
        nextReaction = now + 4 * 60_000L
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(app, 0)).toString() } catch (e: Exception) { app }
        ask("(Il vient d'ouvrir l'appli « $label » sur son téléphone. Réagis en une phrase courte, comme une amie : une remarque, une taquinerie ou une question. Sers-toi de tes souvenirs si ça colle.)", quiet = true)
    }

    private fun petted() {
        if (mode == Mode.STAND) view.play("grin") else oneShot("blink")
        say(Lines.pick("pet"), 3500)
    }

    // ---------- Parler ----------

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
            // Son visage (face.png, s'il est importé) à gauche de la bulle.
            val face = if (sprites.has("face")) sprites.strip("face", keep = view.clipName) else null
            if (face != null) {
                val icon = android.graphics.drawable.BitmapDrawable(resources, face)
                icon.setBounds(0, 0, dp(32), dp(32))
                shown.setCompoundDrawables(icon, null, null, null)
                shown.compoundDrawablePadding = dp(8)
            }
            val layout = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
        layout.y = if (params.y - h - dp(4) > dp(24)) params.y - h - dp(4) else params.y + params.height + dp(4)
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
