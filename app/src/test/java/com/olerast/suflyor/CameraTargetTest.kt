package com.olerast.suflyor

import com.olerast.suflyor.overlay.CameraTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraTargetTest {
    @Test
    fun windowOnlyComesFirstAndUnsupportedSavedTargetsFallBackToWindowOnly() {
        assertEquals(listOf("NONE", "CAMERA", "JIANYING", "DOUYIN", "KUAISHOU"), CameraTarget.entries.map { it.name })
        assertEquals(CameraTarget.NONE, CameraTarget.from("INSTAGRAM"))
        assertEquals(CameraTarget.NONE, CameraTarget.from("TIKTOK"))
        assertEquals(CameraTarget.NONE, CameraTarget.from("unknown"))
        assertEquals(CameraTarget.NONE, CameraTarget.from("XIAOHONGSHU"))
        assertEquals(CameraTarget.DOUYIN, CameraTarget.from("DOUYIN"))
    }
}
