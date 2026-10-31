package com.olerast.suflyor.session

object ReadingPace {
    fun perMinute(words: Int, targetSeconds: Int, manualWpm: Int, useDuration: Boolean, percent: Int): Float {
        val base = if (useDuration) words.coerceAtLeast(0) * 60f / targetSeconds.coerceIn(15, 7200)
            else manualWpm.coerceIn(60, 260).toFloat()
        return base * percent.coerceIn(50, 300) / 100f
    }
}
