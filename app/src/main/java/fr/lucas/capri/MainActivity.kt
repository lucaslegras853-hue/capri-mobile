package fr.lucas.capri

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

// L'écran de réglages : sa clé, son modèle, sa taille, ses animations, et les boutons pour la lancer.
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    private fun dp(value: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun label(text: String, big: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        setTextColor(if (big) Color.rgb(255, 46, 136) else Color.rgb(168, 174, 184))
        textSize = if (big) 22f else 14f
        typeface = if (big) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.DEFAULT
        setPadding(0, dp(if (big) 8 else 14), 0, dp(4))
    }

    private fun field(value: String, hint: String, secret: Boolean = false): EditText = EditText(this).apply {
        setText(value)
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        setSingleLine(true)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }
        root.addView(label("CAPRI  //  MOBILE", true))
        status = label("")
        root.addView(status)

        root.addView(label("Clé NVIDIA (gratuite sur build.nvidia.com, commence par nvapi-)"))
        val key = field(prefs.key, "nvapi-...", secret = true)
        root.addView(key)
        root.addView(label("Modèle"))
        val model = field(prefs.model, Prefs.DEFAULT_MODEL)
        root.addView(model)
        root.addView(label("Comment elle t'appelle"))
        val nickname = field(prefs.nickname, "Ton prénom ou un surnom")
        root.addView(nickname)

        root.addView(label("Taille"))
        val size = SeekBar(this).apply {
            max = 220
            progress = prefs.size - 80
        }
        root.addView(size)

        val popIns = CheckBox(this).apply { text = "Elle surgit avec des petites phrases"; setTextColor(Color.WHITE); isChecked = prefs.popIns }
        val chatter = CheckBox(this).apply { text = "Elle lance la conversation d'elle-même"; setTextColor(Color.WHITE); isChecked = prefs.chatter }
        val remember = CheckBox(this).apply { text = "Elle se souvient de vos discussions"; setTextColor(Color.WHITE); isChecked = prefs.remember }
        root.addView(popIns)
        root.addView(chatter)
        root.addView(remember)

        root.addView(button("Enregistrer") {
            prefs.key = key.text.toString()
            prefs.model = model.text.toString()
            prefs.nickname = nickname.text.toString()
            prefs.size = size.progress + 80
            prefs.popIns = popIns.isChecked
            prefs.chatter = chatter.isChecked
            prefs.remember = remember.isChecked
            Toast.makeText(this, "Enregistré. Relance Capri pour la nouvelle taille.", Toast.LENGTH_SHORT).show()
        })

        root.addView(label("Permissions"))
        root.addView(button("1. Autoriser l'affichage par-dessus les applis") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        if (Build.VERSION.SDK_INT >= 33) {
            root.addView(button("2. Autoriser les notifications") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            })
        }

        root.addView(label("Animations"))
        root.addView(label("Sans animations importées, elle apparaît en petit robot néon. Pour mettre les tiennes, choisis les fichiers PNG : idle, blink, walk, sleep, grin, glitch, dangle, wake."))
        root.addView(button("Importer les animations (PNG)") { pickSprites() })
        root.addView(button("Retirer les animations importées") {
            File(filesDir, "sprites").deleteRecursively()
            Toast.makeText(this, "Animations retirées. Relance Capri.", Toast.LENGTH_SHORT).show()
            refresh()
        })

        root.addView(label("Capri"))
        root.addView(button("Lancer Capri") { start() })
        root.addView(button("Arrêter Capri") { stopService(Intent(this, CapriService::class.java)); refresh() })
        root.addView(button("Ouvrir le chat") { startActivity(Intent(this, ChatActivity::class.java)) })
        root.addView(button("Voir ses souvenirs") {
            val text = Memory(this).memories().ifBlank { "Pas encore de souvenirs. Parle-lui un peu !" }
            AlertDialog.Builder(this).setTitle("Ses souvenirs").setMessage(text).setPositiveButton("OK", null).show()
        })
        root.addView(button("Tout lui faire oublier") {
            AlertDialog.Builder(this).setTitle("Tout oublier ?").setMessage("Elle oubliera vos discussions et ses souvenirs.")
                .setPositiveButton("Oublier") { _, _ -> Memory(this).forgetAll(); Toast.makeText(this, "Oublié.", Toast.LENGTH_SHORT).show() }
                .setNegativeButton("Annuler", null).show()
        })
        root.addView(label("Toucher Capri : discuter. Double-toucher : la caresser. La glisser : la déplacer (lâche-la en l'air, elle tombe). Appui long : ces réglages."))

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val running = CapriService.instance != null
        val sprites = File(File(filesDir, "sprites"), "idle.png").exists()
        status.text = (if (running) "● Capri est lancée" else "○ Capri est arrêtée") +
            (if (!overlay) "\n⚠ Autorise l'affichage par-dessus les applis" else "") +
            (if (prefs.key.isEmpty()) "\n⚠ Colle ta clé NVIDIA pour qu'elle puisse parler" else "") +
            (if (sprites) "\nAnimations importées" else "\nAnimations : robot néon par défaut")
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorise d'abord l'affichage par-dessus les applis.", Toast.LENGTH_LONG).show()
            return
        }
        if (CapriService.instance != null) stopService(Intent(this, CapriService::class.java))
        startForegroundService(Intent(this, CapriService::class.java))
        status.postDelayed({ refresh() }, 600)
    }

    private fun pickSprites() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 7)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7 || resultCode != Activity.RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        else data.data?.let { uris.add(it) }
        val folder = File(filesDir, "sprites")
        folder.mkdirs()
        val wanted = setOf("idle", "blink", "walk", "sleep", "grin", "glitch", "dangle", "wake")
        var copied = 0
        for (uri in uris) {
            val name = displayName(uri)?.lowercase() ?: continue
            val base = name.removeSuffix(".png")
            if (base !in wanted) continue
            contentResolver.openInputStream(uri)?.use { input -> File(folder, "$base.png").outputStream().use { input.copyTo(it) } }
            copied++
        }
        Toast.makeText(this, "$copied animation(s) importée(s). Relance Capri.", Toast.LENGTH_LONG).show()
        refresh()
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
