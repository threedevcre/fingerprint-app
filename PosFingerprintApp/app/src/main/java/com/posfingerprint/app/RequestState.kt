package com.posfingerprint.app

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Small in-memory state shared by the service and the screens. */
object RequestState {
    /** Request tokens already approved/dismissed on this phone (do not notify again). */
    val handled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Tokens whose scanner was already opened automatically (never auto-open the same one twice). */
    val autoOpened: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile var online: Boolean = false
    @Volatile var lastError: String = ""

    /** The request currently waiting on the server for this phone, if any. */
    @Volatile var pending: JSONObject? = null
    @Volatile var pendingAt: Long = 0L

    /** Number of this app's screens currently visible. */
    @Volatile var visibleScreens: Int = 0

    val inForeground: Boolean get() = visibleScreens > 0
}
