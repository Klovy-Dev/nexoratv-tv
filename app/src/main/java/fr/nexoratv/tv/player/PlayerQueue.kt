package fr.nexoratv.tv.player

import fr.nexoratv.tv.core.model.Channel

/**
 * Passe-plat entre l'écran appelant et [PlayerActivity] : Parcelable-iser une
 * liste de milliers de chaînes serait lourd. La liste vit ici le temps de la
 * lecture.
 */
object PlayerQueue {
    var sourceId: String = ""
    var items: List<Channel> = emptyList()
    var startIndex: Int = 0

    fun set(sourceId: String, items: List<Channel>, startIndex: Int) {
        this.sourceId = sourceId
        this.items = items
        this.startIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
}
