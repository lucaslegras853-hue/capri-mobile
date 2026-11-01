package fr.lucas.capri

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Ce qu'elle retient : la conversation récente, et ses souvenirs résumés (souvenirs.md), le tout sur le téléphone.
class Memory(context: Context) {
    private val historyFile = File(context.filesDir, "historique.json")
    val memoriesFile = File(context.filesDir, "souvenirs.md")
    private val queueFile = File(context.filesDir, "a_resumer.txt")

    data class Line(val role: String, val text: String, val time: Long)

    @Synchronized
    fun history(): List<Line> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val array = JSONArray(historyFile.readText())
            (0 until array.length()).map {
                val item = array.getJSONObject(it)
                Line(item.getString("role"), item.getString("text"), item.optLong("time"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(role: String, text: String, keep: Boolean) {
        val lines = history().toMutableList()
        lines.add(Line(role, text, System.currentTimeMillis()))
        while (lines.size > 60) lines.removeAt(0)
        val array = JSONArray()
        for (line in lines) array.put(JSONObject().put("role", line.role).put("text", line.text).put("time", line.time))
        historyFile.writeText(array.toString())
        if (keep && !text.startsWith("(")) {
            val day = SimpleDateFormat("EEEE d MMMM yyyy HH:mm", Locale.FRENCH).format(Date())
            queueFile.appendText("$day ${if (role == "user") "Lui" else "Capri"} : ${text.replace('\n', ' ')}\n")
        }
    }

    fun memories(): String = if (memoriesFile.exists()) memoriesFile.readText() else ""

    @Synchronized
    fun waiting(): String = if (queueFile.exists()) queueFile.readText() else ""

    @Synchronized
    fun folded(newMemories: String) {
        memoriesFile.writeText(newMemories.trim() + "\n")
        queueFile.delete()
    }

    @Synchronized
    fun forgetAll() {
        historyFile.delete()
        memoriesFile.delete()
        queueFile.delete()
    }
}
