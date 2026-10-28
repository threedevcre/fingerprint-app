package com.posfingerprint.app

import java.util.concurrent.ConcurrentHashMap

/** Small in-memory state shared by the service and the screens. */
object RequestState {
    /** Request tokens already approved/dismissed on this phone (do not notify again). */
    val handled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile var online: Boolean = false
    @Volatile var lastError: String = ""
}
