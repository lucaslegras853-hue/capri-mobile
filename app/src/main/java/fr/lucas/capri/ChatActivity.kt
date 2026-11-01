package fr.lucas.capri

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// La fenêtre de discussion : l'historique, une case pour écrire, et le bouton Envoyer.
class ChatActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var memory: Memory
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())

    private fun dp(value: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memory = Memory(this)
        prefs = Prefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(32), dp(12), dp(12))
        }
        val title = TextView(this).apply {
            text = "CAPRI  //  CHAT"
            setTextColor(Color.rgb(255, 46, 136))
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        root.addView(title)
        scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Dis quelque chose à Capri"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setSingleLine(false)
            maxLines = 4
        }
        send = Button(this).apply { text = "Envoyer" }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(send)
        root.addView(row)
        setContentView(root)
        // Android 15 dessine sous le clavier et les barres : on décale nous-mêmes pour que la case reste visible.
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val keyboard = insets.getInsets(WindowInsets.Type.ime())
                v.setPadding(dp(12), bars.top + dp(12), dp(12), maxOf(bars.bottom, keyboard.bottom) + dp(8))
                insets
            }
        }

        for (line in memory.history()) if (!line.text.startsWith("(")) addLine(line.role == "user", line.text)
        send.setOnClickListener { sendMessage() }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        if (prefs.key.isEmpty()) addLine(false, "Il me faut un cerveau d'abord : colle ta clé NVIDIA dans l'appli Capri.")
    }

    private fun addLine(mine: Boolean, text: String) {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(232, 236, 240))
            textSize = 15f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(if (mine) Color.rgb(40, 20, 50) else Color.rgb(9, 11, 15))
                setStroke(dp(1), if (mine) Color.rgb(255, 46, 136) else Color.rgb(58, 214, 235))
                cornerRadius = dp(8).toFloat()
            }
        }
        val layout = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            setMargins(if (mine) dp(48) else 0, dp(4), if (mine) 0 else dp(48), dp(4))
        }
        list.addView(bubble, layout)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        addLine(true, text)
        send.isEnabled = false
        val service = CapriService.instance
        if (service != null) {
            service.ask(text) { answer, problem -> reply(answer, problem) }
        } else {
            // Capri n'est pas lancée : on demande directement à son cerveau.
            Thread {
                var answer: String? = null
                var problem: String? = null
                try {
                    val brain = Brain(prefs, memory)
                    answer = brain.reply(text)
                    try { brain.foldIfNeeded() } catch (e: Exception) { }
                } catch (e: Exception) {
                    problem = e.message
                }
                val said = answer
                val trouble = problem
                main.post { reply(said, trouble) }
            }.start()
        }
    }

    private fun reply(answer: String?, problem: String?) {
        send.isEnabled = true
        addLine(false, answer ?: (problem ?: "Mon cerveau a planté."))
    }
}
