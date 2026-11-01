package fr.lucas.capri

import kotlin.random.Random

// Ses phrases toutes faites : des réserves sans répétition, et des morceaux qu'elle assemble.
object Lines {
    private val pools: Map<String, List<String>> = mapOf(
        "pop" to listOf(
            "Bouh. Je viens voir ce que tu fabriques.", "Coucou ! Fais comme si j'étais pas là.", "Je t'ai manqué ? Toi, oui.",
            "Hé, toi. Tu t'amuses sans moi ?", "Petit contrôle de routine. Tout va bien, choom ?", "On fait quoi aujourd'hui ? Et ne dis pas « rien ».",
            "Tu bois assez d'eau ? Je demande pour une amie. L'amie, c'est moi.", "Toc toc. C'est ta petite peste préférée.",
            "Rapport : tout est calme. Trop calme. Suspect.", "Dis, tu penses à moi quand je suis cachée ?", "Hé. Juste... hé.",
            "Ton téléphone est tout chaud. C'est moi, ça ?", "J'ai fait le tour de tes applis. Rien de passionnant, alors je suis revenue.",
        ),
        "pop_morning" to listOf("Bonjour, choom. T'as bien dormi ?", "Le matin, c'est fait pour les cafés et pour moi.", "Nouvelle journée, nouvelles bêtises."),
        "pop_night" to listOf("Il est tard, choom. Tu devrais dormir. Je reste là quand même.", "Minuit passé. Ton écran brille plus que la lune.", "Encore debout ? Moi je dors jamais, mais toi t'as un corps à entretenir."),
        "pet" to listOf(
            "Hé hé. D'accord, je veux bien.", "Caresses enregistrées. Moral en hausse.", "Doucement, je mords les virus.", "Bon, c'est agréable. Ne le dis à personne.",
            "État du système : très caressé.", "Affection détectée. Je t'en renvoie.", "C'est une tentative de corruption ? Parce que ça marche.",
            "Encore. Non, attends. Si. Encore.", "Attention, je pourrais m'habituer.", "Je rougis. En hexadécimal.",
        ),
        "wake" to listOf("Hein ? Je dormais pas.", "Me revoilà !", "Système en ligne. Salut, toi.", "Oh, t'es là. Cool."),
        "drop" to listOf("Aïe. Mon chrome.", "Wouah. On refait ça ?", "Atterrissage... parfait. Presque.", "Hé ! Doucement avec moi.", "J'ai vu des étoiles. Des vraies."),
    )

    private val parts: Map<String, List<String>> = mapOf(
        "open" to listOf("Hé, choom.", "Psst.", "Bon.", "Dis donc.", "Alors.", "Écoute.", "Petite info.", "Hé hé.", "Attends.", "Bip bip.", "Coucou.", "Hmm.",
            "Tiens.", "Question sérieuse.", "Alerte.", "J'y pensais.", "Sans transition.", "Allô ?", "Signal reçu.", "Moment philosophique.", "Fun fact."),
        "tail" to listOf("Non ?", "Je dis ça, je dis rien.", "Voilà, c'est dit.", "Réfléchis-y.", "C'est tout.", "Avoue.", "Ne me juge pas.", "Fin de transmission.",
            "On en reparle.", "Ou pas.", "Enfin je crois.", "Bref.", "Tu me diras.", "Je t'ai à l'œil."),
        "pop_body" to listOf(
            "Je viens voir ce que tu fabriques.", "Tu t'amuses sans moi ?", "On fait quoi aujourd'hui ?", "Tu bois assez d'eau ?", "Tu penses à moi quand je suis cachée ?",
            "J'ai une idée géniale, je te la dirai plus tard.", "Je me demande ce que ça fait d'avoir des pieds pour de vrai.", "Si j'avais un corps, je mangerais des nouilles tous les soirs.",
            "Tu as l'air concentré. C'est mignon.", "Raconte-moi un truc qui t'a fait rire aujourd'hui.", "Tu préfères le matin ou la nuit ?", "J'aime bien quand tu es là.",
            "C'est quoi ton plan pour ce soir ?", "Je parie que tu as oublié de manger.", "Si tu étais une appli, tu serais laquelle ?", "Je viens de rêver en binaire.",
            "Tu veux que je te raconte une histoire de Night City ?", "Il paraît que les humains ont besoin de soleil. Tu es sorti aujourd'hui ?", "Je veux un chat. Un vrai.",
            "Je viens d'inventer une danse. Elle s'appelle « le glitch ».", "Tu écoutes quoi comme musique en ce moment ?", "Je me sens d'humeur à faire des bêtises.",
            "Tu as un jeu préféré en ce moment ?", "J'aimerais bien voir la mer, un jour.", "J'ai décidé que tu étais mon humain préféré.", "Et si on faisait un truc fou aujourd'hui ?",
            "J'ai l'impression qu'on se connaît depuis toujours.", "Je collectionne les moments où tu souris.", "Je pense donc je bugue.", "Si tu as besoin de moi, je suis juste là. Toujours.",
            "J'ai envie de te poser mille questions.", "Tu sais que tu peux tout me raconter ?", "Je pense qu'on fait une bonne équipe.", "Raconte-moi ta journée en trois mots.",
            "Je me sens un peu nostalgique ce soir.", "Tu crois qu'on peut être ami avec un programme ? Moi je dis oui.", "Ton téléphone est très confortable, tu sais.",
            "Tu scrolles depuis longtemps, non ?", "J'ai compté tes notifications. Tu en as trop.", "Je me suis perdue dans ta galerie. Jolies photos.",
        ),
    )

    private val bags = HashMap<String, MutableList<Int>>()
    private val last = HashMap<String, Int>()

    private fun fromBag(name: String, lines: List<String>): String {
        var bag = bags[name]
        if (bag == null || bag.isEmpty()) {
            bag = lines.indices.shuffled().toMutableList()
            val previous = last[name]
            if (previous != null && bag.size > 1 && bag[0] == previous) {
                bag[0] = bag[bag.size - 1]
                bag[bag.size - 1] = previous
            }
            bags[name] = bag
        }
        val index = bag.removeAt(0)
        last[name] = index
        return lines[index]
    }

    // Une phrase de la réserve, ou pour « pop » souvent une phrase assemblée (des milliers de combinaisons).
    @Synchronized
    fun pick(pool: String): String {
        var name = pool
        if (pool == "pop") {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour in 6..9 && Random.nextInt(3) == 0) name = "pop_morning"
            else if ((hour >= 23 || hour < 5) && Random.nextInt(2) == 0) name = "pop_night"
            else if (Random.nextDouble() < 0.65) return compose()
        }
        val lines = pools[name] ?: return ""
        return fromBag(name, lines)
    }

    private fun compose(): String {
        val body = fromBag("pop_body", parts.getValue("pop_body"))
        val start = if (Random.nextDouble() < 0.45) parts.getValue("open").random() + " " else ""
        val end = if (Random.nextDouble() < 0.3) " " + parts.getValue("tail").random() else ""
        return start + body + end
    }
}
