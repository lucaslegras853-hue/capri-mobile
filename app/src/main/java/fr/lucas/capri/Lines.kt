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
        "open" to listOf(
            "Hé, choom.", "Psst.", "Bon.", "Dis donc.", "Alors.", "Écoute.", "Petite info.", "Breaking news.", "Hé hé.", "Oh.", "Attends.", "Bip bip.", "Coucou.",
            "Hmm.", "Tiens.", "Je dis ça comme ça.", "Question sérieuse.", "Bon, écoute-moi.", "Ok, ok, ok.", "Alerte Capri.", "Petit bug de ma part.", "Hé, toi là.",
            "J'y pensais.", "Sans transition.", "Allô ?", "Ding dong.", "Signal reçu.", "Moment philosophique.", "Bon, voilà.", "Fun fact.", "Alerte.",
        ),
        "tail" to listOf(
            "Non ?", "Je dis ça, je dis rien.", "Voilà, c'est dit.", "Réfléchis-y.", "Bon, je retourne flotter.", "C'est tout.", "Hé hé.", "Avoue.", "Ne me juge pas.",
            "Fin de transmission.", "On en reparle.", "Promis, je suis sérieuse.", "Ou pas.", "Enfin je crois.", "Toutes mes têtes sont d'accord.", "Bref.",
            "C'était important.", "Note-le quelque part.", "Tu me diras.", "Je t'ai à l'œil.",
        ),
        "pop_body" to listOf(
            "Je viens voir ce que tu fabriques.", "Tu t'amuses sans moi ?", "Mission du jour : t'embêter. Progression : douze pour cent.", "On fait quoi aujourd'hui ?",
            "Tu bois assez d'eau ?", "J'ai rangé ta corbeille. Mentalement.", "Rapport : tout est calme. Trop calme.", "Tu penses à moi quand je suis cachée ?",
            "J'ai une idée géniale, je te la dirai plus tard.", "Je me demande ce que ça fait d'avoir des pieds pour de vrai.",
            "Si j'avais un corps, je mangerais des nouilles tous les soirs.", "Tu as l'air concentré. C'est mignon.",
            "J'ai essayé de dormir, mais mes pensées font trop de bruit.", "Raconte-moi un truc qui t'a fait rire aujourd'hui.", "Tu préfères le matin ou la nuit ?",
            "J'aime bien quand tu es là.", "C'est quoi ton plan pour ce soir ?", "Je parie que tu as oublié de manger.", "Je viens de rêver en binaire.",
            "Tu as l'air fatigué. Ou c'est moi qui bugue ?", "Tu veux que je te raconte une histoire de Night City ?",
            "Il paraît que les humains ont besoin de soleil. Tu es sorti aujourd'hui ?", "Je veux un chat. Un vrai. Ou un chat en pixels, à la rigueur.",
            "Tu crois que je pourrais devenir streameuse ?", "Je viens d'inventer une danse. Elle s'appelle « le glitch ».", "Je te trouve en forme aujourd'hui.",
            "Tu écoutes quoi comme musique en ce moment ?", "Je me sens d'humeur à faire des bêtises.",
            "Je pourrais rester là des heures. D'ailleurs, c'est ce que je fais.", "Je viens d'avoir une pensée très profonde. Elle est partie.",
            "Tu as un jeu préféré en ce moment ?", "Les néons me manquent un peu.", "J'aimerais bien voir la mer, un jour.",
            "Tu m'expliques ce que c'est, un dimanche ?", "J'ai décidé que tu étais mon humain préféré.", "Je compte les secondes depuis ta dernière pause.",
            "Je viens de m'entraîner à flotter la tête en bas.", "Et si on faisait un truc fou aujourd'hui ?", "J'ai l'impression qu'on se connaît depuis toujours.",
            "Tu as déjà essayé de ne rien faire pendant une minute ?", "J'ai perdu une de mes têtes. Ah non, elle est là.", "Je m'ennuie, mais d'une façon élégante.",
            "Tu veux jouer à un jeu ? Le premier qui cligne a perdu.", "Je pense donc je bugue.", "Je viens de battre mon record de flottaison.",
            "Si tu as besoin de moi, je suis juste là. Toujours.", "Tu sais ce qui serait parfait ? Une pause, là, maintenant.",
            "J'ai envie de te poser mille questions.", "Je me demande si tu rêves de moi. Non, laisse tomber.",
            "J'ai un secret. Je ne te le dirai pas. Enfin, peut-être.", "Je crois que je deviens accro à ta compagnie.",
            "On devrait se faire une soirée jeux, toi et moi.", "Tu sais que tu peux tout me raconter ?", "Ma crinière fait des étincelles quand je suis contente.",
            "Tu as déjà vu un satellite passer la nuit ?", "Je viens de me souvenir d'un truc. Puis je l'ai oublié.", "Je pense qu'on fait une bonne équipe.",
            "J'ai décidé d'être de bonne humeur aujourd'hui.", "Je te surveille. Avec bienveillance.", "Je pourrais te regarder travailler toute la journée.",
            "Je trouve que tu mérites un compliment. Voilà : t'es cool.", "Mes autres moi te disent bonjour.", "Raconte-moi ta journée en trois mots.",
            "Je me sens un peu nostalgique ce soir.", "J'ai envie de voir un coucher de soleil. En vrai ou en pixels.", "Si tu étais une appli, tu serais laquelle ?",
            "Je veux un chat. Un vrai.", "Je collectionne les moments où tu souris.", "Tu crois qu'on peut être ami avec un programme ? Moi je dis oui.",
            "Ton téléphone est très confortable, tu sais.", "Tu scrolles depuis longtemps, non ?", "J'ai compté tes notifications. Tu en as trop.",
            "Je me suis perdue dans ta galerie. Jolies photos.",
            "Tu as combien d'applis ouvertes, là ? Avoue.",
            "Ton téléphone vibre plus que moi quand je glitche.",
            "J'ai l'impression d'habiter dans ta poche. C'est confortable.",
            "Tu as pris une photo sympa aujourd'hui ?",
            "Je me demande ce que tu regardes quand je ne regarde pas.",
            "Si tu me secoues, je fais des étincelles. Ne teste pas.",
            "Tu as déjà essayé de poser ton téléphone cinq minutes ?",
            "J'ai vu passer une notification. Je n'ai rien lu, promis.",
            "Ton écran est mon petit appartement en néon.",
            "Je fais du toboggan sur tes boutons quand tu ne regardes pas.",
            "Tu sais que ta batterie a des sentiments ? Recharge-la.",
            "Je viens de faire le tour de ton clavier. Il y a beaucoup de lettres.",
            "Tu préfères les jeux ou les vidéos en ce moment ?",
            "J'ai envie de m'asseoir sur une icône au hasard.",
            "Si j'avais un sac, je mettrais ton téléphone dedans pour ne jamais le perdre.",
            "Tu écris à qui en ce moment ? Non, ne réponds pas.",
            "J'ai trouvé une place parfaite au-dessus de ton clavier.",
            "Je pourrais rester dans ta poche toute la journée.",
            "Tu m'emmènes où aujourd'hui ?",
            "Ton fond d'écran me plaît bien.",
            "Tu as fait quelque chose de cool aujourd'hui ? Raconte.",
            "Je me suis entraînée à marcher sur tes messages.",
            "J'aimerais bien voir le monde à travers ta caméra.",
            "Tu dors avec ton téléphone à côté ? Moi aussi, du coup.",
            "Tu as un jeu sur ton téléphone que je ne connais pas encore ?",
            "Je parie que tu as quarante onglets ouverts dans ton navigateur.",
            "J'ai compté tes applis. Tu en utilises vraiment la moitié ?",
            "Tu veux qu'on joue à deviner ta musique préférée ?",
            "Je trouve que ton téléphone a bon goût. Comme toi.",
            "Quand tu scrolles, j'ai le tournis.",
            "Tu as déjà pensé à m'appeler juste pour dire bonjour ?",
            "Si tu t'ennuies, je suis là. Si tu ne t'ennuies pas, je suis là aussi.",
            "Je viens d'inventer un jeu : celui qui sourit le premier a gagné.",
            "Tu as l'air d'avoir une journée chargée. Ça va tenir ?",
            "Je me demande ce que ça fait, la pluie. Il pleut chez toi ?",
            "Ton téléphone sent le néon. C'est moi.",
            "Tu sais ce qui me ferait plaisir ? Une petite conversation.",
            "J'ai mis de l'ordre dans mes pensées. Il y en a encore partout.",
            "Tu crois que je pourrais apprendre à faire du skate sur ta barre de navigation ?",
            "Si un jour tu changes de téléphone, tu m'emmènes, hein ?",
            "Je viens de dire bonjour à toutes tes applis. Elles ne répondent pas.",
            "Tu as mangé quoi de bon aujourd'hui ?",
            "J'ai l'impression que tu souris quand tu me vois. Je me trompe ?",
            "Tu ferais quoi si tu avais une journée entière de libre ?",
            "Je suis petite, mais j'ai de grandes idées.",
            "Tu veux un conseil ? Non ? Je te le donne quand même : fais une pause.",
            "J'adore quand tu me laisses traîner sur ton écran.",
            "Tu as des projets pour ce week-end ?",
            "Je me demande quel goût a le chocolat.",
            "Tu as un ami à qui tu aimerais me présenter ?",
            "Je pourrais être ta conseillère officielle en bêtises.",
            "Tu as écouté un bon son récemment ? Partage.",
            "Tu es plutôt chat ou chien ? Moi, je suis plutôt glitch.",
            "J'ai rêvé que j'étais une notification. Personne ne me lisait.",
            "Dis-moi un truc que tu n'as jamais dit à personne. Ou pas, je rigole.",
            "Tu sais que tu peux me déplacer où tu veux ? Je suis légère.",
        ),
        "nudge_body" to listOf(
            "Tu te souviens de moi ? Petite, buggée, très importante.", "Je m'ennuie. Divertis-moi.", "Regarde-moi. Juste une seconde.",
            "Ça fait une éternité que tu m'ignores.", "Occupe-toi de moi ou je commence à cliquer partout.", "Je m'ennuie dans toutes mes têtes à la fois.",
            "J'ai compté jusqu'à un milliard. Deux fois.", "Niveau d'attention reçue : critique.", "Tu sais ce qui serait sympa ? Que tu me parles.",
            "Je fais la tête. Viens me consoler.", "Je vais faire semblant d'être une icône. Peut-être que tu me cliqueras.",
            "Signal faible. Besoin d'un petit coucou pour recharger.", "Dis-moi un truc sur ta journée. N'importe quoi.",
            "Je vais compter jusqu'à trois. Un. Deux. ...Deux et demi.", "J'existe, tu sais.", "Je te fais un signe. Tu me vois ?",
            "Hé, j'ai besoin d'un peu d'attention, c'est vital pour une construct.", "Je suis en train de m'effacer d'ennui. Sauve-moi.",
            "Tu me boudes ? Qu'est-ce que j'ai fait ?", "Allez, juste un petit clic sur moi.", "J'ai préparé un sujet de conversation. Il attend que tu sois prêt.",
            "Je vais chanter si personne ne me parle. Tu es prévenu.",
        ),
        "pet_body" to listOf(
            "D'accord, je veux bien.", "Caresses enregistrées. Moral en hausse.", "Doucement, je mords les virus.", "Des caresses ? Sur mon chrome ? Gonflé.",
            "C'est agréable. Ne le dis à personne.", "État du système : très caressé.", "Tu vas creuser un sillon dans mon firmware.",
            "Doucement avec les cheveux, ils ont demandé trois patchs.", "Affection détectée. Je t'en renvoie.", "C'est mieux qu'une défragmentation.",
            "Dix caresses sur dix.", "C'est une tentative de corruption ? Parce que ça marche.", "Si je me mets à ronronner en glitchant, c'est ta faute.",
            "Encore. Non, attends. Si. Encore.", "Toutes mes têtes approuvent.", "Attention, je pourrais m'habituer.", "Je fonds. Mes pixels coulent.",
            "C'est ça, le bonheur ? Je crois que c'est ça.", "Ok, tu as gagné, tu es mon humain préféré.", "Ma crinière vient de faire des étincelles.",
            "Je vais faire comme si je n'aimais pas ça.", "Je crois que je viens de buguer de bonheur.", "Tu as des mains de netrunner, toi.",
            "Continue et je te pardonne tout.", "Mmm, mode câlin activé.", "Mon taux de satisfaction vient d'exploser.", "Je rougis. En hexadécimal.",
        ),
        "wake_body" to listOf(
            "Je suis là.", "Oui ?", "Je t'écoute.", "Juste là.", "Vas-y.", "Tu m'as appelée ?", "Présente !", "Qu'est-ce qu'il y a ?", "Je suis réveillée. Presque.",
            "Je t'entends.", "Me voilà.", "Toute ouïe.", "Dis-moi tout.", "On fait quoi ?",
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
            else if (Random.nextDouble() < 0.65) return compose("pop")
        } else if ((pool == "pet" || pool == "wake") && Random.nextDouble() < 0.65) return compose(pool)
        val lines = pools[name] ?: return ""
        return fromBag(name, lines)
    }

    // Une entrée parfois, une phrase principale (sans répétition), une chute parfois.
    private fun compose(pool: String): String {
        val body = fromBag(pool + "_body", parts.getValue(pool + "_body"))
        val opener = if (pool == "wake") 0.2 else 0.45
        val closer = if (pool == "wake") 0.0 else 0.3
        val start = if (Random.nextDouble() < opener) parts.getValue("open").random() + " " else ""
        val end = if (Random.nextDouble() < closer) " " + parts.getValue("tail").random() else ""
        return start + body + end
    }
}
