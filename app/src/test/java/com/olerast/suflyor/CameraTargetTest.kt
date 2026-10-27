package com.olerast.suflyor

import com.olerast.suflyor.overlay.CameraTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraTargetTest {
    @Test
    fun cameraComesFirstAndUnsupportedSavedTargetsFallBackToCamera() {
        assertEquals(listOf("CAMERA", "JIANYING", "DOUYIN", "KUAISHOU", "XIAOHONGSHU", "NONE"), CameraTarget.entries.map { it.name })
        assertEquals(CameraTarget.CAMERA, CameraTarget.from("INSTAGRAM"))
        assertEquals(CameraTarget.CAMERA, CameraTarget.from("TIKTOK"))
        assertEquals(CameraTarget.CAMERA, CameraTarget.from("unknown"))
        assertEquals(CameraTarget.DOUYIN, CameraTarget.from("DOUYIN"))
    }
}
