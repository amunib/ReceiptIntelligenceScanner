package com.receiptintel.scanner.data.repository

/**
 * Simple in-process handoff for a batch of [ReceiptSource]s between the
 * Scan/Import screens and the Processing screen. Compose Navigation args are
 * string/primitive only, and re-encoding potentially hundreds of bitmaps to
 * pass as arguments would be wasteful — so we stash them here for the very
 * short window between "user taps Start processing" and the Processing
 * screen picking them up in a LaunchedEffect.
 *
 * Not persisted across process death by design: if the app is killed mid
 * batch, the safest behavior is to ask the user to re-scan/re-import rather
 * than silently resuming with stale in-memory bitmaps.
 */
object PendingBatchHolder {
    var sources: List<ReceiptSource> = emptyList()
}
