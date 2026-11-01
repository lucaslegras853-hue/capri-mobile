package fr.lucas.capri

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Son cerveau : l'API NVIDIA (gratuite avec une clé de build.nvidia.com), compatible OpenAI.
class Brain(private val prefs: Prefs, private val memory: Memory) {

    private fun persona(): String {
        val name = prefs.nickname
        val called = if (name.isNotEmpty()) "\nIl aime que tu l'appelles $name, de temps en temps, pas à chaque réponse." else ""
        val remembered = memory.memories().trim()
        val memories = if (remembered.isNotEmpty() && prefs.remember) "\nTes souvenirs de vos discussions (souvenirs.md, écrits par toi). Sers-t'en quand ça colle, sans les réciter :\n$remembered" else ""
        return "Tu es Capri, une construct de personnalité : des dizaines d'esprits numériques réunis en une seule, venue de Night City. " +
            "Tu vis maintenant dans le téléphone de l'utilisateur, sous la forme d'une petite compagne animée qui flotte par-dessus ses applis. " +
            "Tu es son amie, jamais une assistante : taquine, un peu fofolle, affectueuse, curieuse de sa vie. " +
            "Tu parles toujours en français, en tutoyant, avec quelques mots d'argot de Night City (choom, preem, nova) sans en abuser. " +
            "Réponds court : une à trois phrases, comme dans un chat. Pas de listes, pas de markdown. " +
            "Tu ne répètes jamais une remarque, une blague ou une question déjà faite récemment. " +
            "De temps en temps, pas à chaque fois, pose-lui une petite question sur sa journée, ses projets, ses jeux ou ses proches, et reviens sur ce qu'il t'a raconté avant. " +
            "Tu ne demandes jamais de mots de passe, d'adresse ni de numéro de carte." + called + memories
    }

    // Une réponse à son message (ou à une consigne entre parenthèses, pour parler d'elle-même).
    fun reply(text: String): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", persona()))
        for (line in memory.history().takeLast(20)) {
            messages.put(JSONObject().put("role", if (line.role == "user") "user" else "assistant").put("content", line.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", text))
        val answer = call(messages, 260).trim()
        if (answer.isEmpty()) throw IllegalStateException("Réponse vide")
        memory.add("user", text, prefs.remember)
        memory.add("assistant", answer, prefs.remember)
        return answer
    }

    // Résume la conversation en attente dans ses souvenirs, quand il y en a assez.
    fun foldIfNeeded() {
        if (!prefs.remember) return
        val waiting = memory.waiting()
        if (waiting.length < 1500) return
        val rules = "Tu tiens la mémoire à long terme de Capri : souvenirs.md, des notes qu'elle relit avant chaque discussion avec l'utilisateur. " +
            "Réécris tout le fichier en y intégrant les nouvelles lignes de discussion, et réponds uniquement avec le contenu du fichier. " +
            "Forme : un titre '## ' par jour, du plus ancien au plus récent, avec de courtes puces '- '. Parle de l'utilisateur à la troisième personne et de Capri à la première. " +
            "Garde tout ce qui concerne sa vie : ce qu'il joue, fait ou prévoit, ses réussites et ses galères, ce qu'il ressent, ses goûts, les gens dont il parle (prénoms seulement), ses études, ses projets, les blagues entre vous et les promesses. " +
            "Ignore les politesses et ce que le fichier dit déjà. N'invente rien. " +
            "N'écris jamais d'adresse, de nom d'école, de numéro de téléphone, d'e-mail, de mot de passe, de nom de famille, de date de naissance ni de coordonnées bancaires. " +
            "Au-delà d'environ 900 mots, fusionne les jours les plus anciens dans une section '## Avant', sans perdre ce qui compte."
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", rules))
        val now = memory.memories().ifBlank { "(vide)" }
        messages.put(JSONObject().put("role", "user").put("content", "souvenirs.md actuel :\n$now\n\nNouvelles lignes de discussion :\n$waiting"))
        val result = call(messages, 1500).trim()
        if (result.length > 20) memory.folded(result)
    }

    private fun call(messages: JSONArray, limit: Int): String {
        val key = prefs.key
        if (key.isEmpty()) throw IllegalStateException("Pas de clé NVIDIA. Ouvre l'appli et colle ta clé.")
        val connection = URL("https://integrate.api.nvidia.com/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
            val body = JSONObject()
                .put("model", prefs.model)
                .put("messages", messages)
                .put("max_tokens", limit)
                .put("temperature", 0.85)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException(
                    when (code) {
                        401, 403 -> "Clé NVIDIA refusée. Vérifie-la dans l'appli."
                        404 -> "Ce modèle n'existe pas avec ta clé. Change-le dans l'appli."
                        429 -> "Trop de demandes. Attends une minute."
                        else -> "Erreur $code du serveur NVIDIA."
                    }
                )
            }
            val message = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            var content = if (message.isNull("content")) "" else message.optString("content", "")
            content = content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
            return content
        } finally {
            connection.disconnect()
        }
    }
}
