package com.olerast.suflyor.overlay

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.annotation.StringRes
import com.olerast.suflyor.R

/** App that "Over the camera" opens right after the floating prompter appears. */
enum class CameraTarget(@StringRes val label: Int, private val targetPackage: String? = null) {
    CAMERA(R.string.camera_target_camera),
    JIANYING(R.string.camera_target_jianying, "com.lemon.lv"),
    DOUYIN(R.string.camera_target_douyin, "com.ss.android.ugc.aweme"),
    KUAISHOU(R.string.camera_target_kuaishou, "com.smile.gifmaker"),
    XIAOHONGSHU(R.string.camera_target_xiaohongshu, "com.xingin.xhs"),
    NONE(R.string.camera_target_window_only);

    fun launchIntent(context: Context): Intent? {
        val pm = context.packageManager
        val intent = when (this) {
            CAMERA -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            NONE -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            else -> targetPackage?.let { pm.getLaunchIntentForPackage(it) }
        } ?: return null
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun isAvailable(context: Context): Boolean =
        this == NONE || this == CAMERA || launchIntent(context) != null

    companion object {
        fun from(name: String): CameraTarget = entries.firstOrNull { it.name == name } ?: CAMERA
    }
}
