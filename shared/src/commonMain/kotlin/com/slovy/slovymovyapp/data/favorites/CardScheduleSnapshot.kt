package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.db.FavoritesQueries
import kotlin.uuid.Uuid

data class CardScheduleSnapshot(
    val id: Uuid,
    val suspended: Boolean,
    val availableAfter: Long?,
)

data class RemovedFavoriteSnapshot(
    val senseId: String,
    val language: Language,
    val lemma: String,
    val createdAt: Long,
    val cards: List<CardScheduleSnapshot>,
)

// Replays per-card (suspended, available_after) values captured before a destructive
// operation. Caller is responsible for owning the surrounding transaction.
fun FavoritesQueries.applyCardScheduleSnapshots(snapshots: List<CardScheduleSnapshot>) {
    for (card in snapshots) {
        restoreCardScheduling(
            suspended = card.suspended,
            available_after = card.availableAfter,
            id = card.id,
        )
    }
}
