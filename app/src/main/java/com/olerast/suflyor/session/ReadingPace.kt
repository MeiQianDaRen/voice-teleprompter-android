package com.olerast.suflyor.session

/** Explicit timer modes only; voice following never consumes manual speed settings. */
object ReadingPace {
    fun perMinute(manualWpm: Int, percent: Int): Float = manualWpm.coerceIn(60, 260) * percent.coerceIn(50, 300) / 100f
}
