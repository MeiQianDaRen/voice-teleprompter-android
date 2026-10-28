package com.olerast.suflyor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.olerast.suflyor.R
import java.io.File

/** Camera and gallery share one entry screen; a denied camera never prevents choosing an image. */
@Composable
internal fun ScanCamera(onBack: () -> Unit, onGallery: () -> Unit, newPhoto: () -> File, onPhoto: (File) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val orientation = LocalConfiguration.current.orientation
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(owner) {
        val listener = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(listener)
        onDispose { owner.lifecycle.removeObserver(listener) }
    }
    LaunchedEffect(Unit) {
        if (!allowed && !requested) { requested = true; permission.launch(Manifest.permission.CAMERA) }
    }
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    DisposableEffect(allowed, owner, orientation) {
        var active = true
        val future = if (allowed) ProcessCameraProvider.getInstance(context) else null
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        ready = false
        future?.addListener({
            if (active) {
                try {
                    provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    boundPreview = preview
                    capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                    provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    ready = true
                    failed = false
                } catch (_: Exception) { failed = true }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            boundPreview?.let { provider?.unbind(it, capture) }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (allowed) AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Ic(R.drawable.ic_back, stringResource(R.string.scan_back), tint = Color.White) }
                Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                if (!allowed || failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(if (!allowed) R.string.scan_camera_permission else R.string.scan_camera_failed), color = Color.White)
                        if (!allowed) TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.scan_allow_camera), color = Palette.Accent) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .7f)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.scan_camera_hint), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onGallery, enabled = !capturing) { Text(stringResource(R.string.scan_gallery), color = Palette.Accent) }
                    Box(Modifier.size(80.dp).border(3.dp, if (ready) Color.White else Color.Gray, CircleShape).padding(7.dp)
                        .clip(CircleShape).background(if (ready) Palette.Accent else Color.DarkGray)
                        .clickable(enabled = ready && !capturing) {
                            capturing = true
                            capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                            val file = try { newPhoto() } catch (_: Exception) { capturing = false; failed = true; return@clickable }
                            runCatching { capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) { capturing = false; onPhoto(file) }
                                    override fun onError(exception: ImageCaptureException) { file.delete(); capturing = false; failed = true }
                                }) }.onFailure { file.delete(); capturing = false; failed = true }
                        }, contentAlignment = Alignment.Center) {
                        Ic(R.drawable.ic_scan, stringResource(R.string.scan_camera), tint = Palette.OnAccent, size = 30.dp)
                    }
                    Text(stringResource(R.string.scan_offline), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
