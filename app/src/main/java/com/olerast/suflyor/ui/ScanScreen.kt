package com.olerast.suflyor.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.olerast.suflyor.R
import com.olerast.suflyor.doc.OfflineOcr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ScanScreen(onBack: () -> Unit, onSave: (String, String) -> Boolean) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var draftPath by rememberSaveable { mutableStateOf<String?>(null) }
    var rotation by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf("chi_sim") }
    var recognizing by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val photos = remember { File(context.cacheDir, "scan-photos").apply { mkdirs() } }
    val drafts = remember { File(context.noBackupFilesDir, "scan-drafts").apply { mkdirs() } }
    fun cameraFile(): File? = cameraPath?.let { path ->
        File(path).takeIf { it.parentFile?.canonicalFile == photos.canonicalFile }
    }
    fun draftFile(): File? = draftPath?.let { path ->
        File(path).takeIf { it.parentFile?.canonicalFile == drafts.canonicalFile }
    }
    fun releaseImage() {
        imageUri?.let { uri ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        cameraFile()?.delete()
        cameraPath = null
    }
    fun close() {
        releaseImage()
        draftFile()?.delete()
        onBack()
    }
    BackHandler { close() }

    LaunchedEffect(Unit) {
        val activePhoto = cameraPath
        val activeDraft = draftPath
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            for (folder in listOf(photos, drafts)) {
                folder.listFiles()?.filter {
                    it.isFile && it.lastModified() < cutoff && it.absolutePath != activePhoto && it.absolutePath != activeDraft
                }?.forEach { it.delete() }
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            cameraFile()?.let { imageUri = FileProvider.getUriForFile(context, "${context.packageName}.scan", it).toString() }
            rotation = 0
        } else {
            cameraFile()?.delete()
            cameraPath = null
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            releaseImage()
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString()
            rotation = 0
        }
    }

    LaunchedEffect(imageUri, rotation) {
        preview = null
        error = 0
        val uri = imageUri ?: return@LaunchedEffect
        if (draftPath != null) return@LaunchedEffect
        try {
            preview = withContext(Dispatchers.IO) { OfflineOcr.decode(appContext, Uri.parse(uri), rotation) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = R.string.scan_image_failed
        }
    }
    LaunchedEffect(recognizing) {
        if (!recognizing) return@LaunchedEffect
        val uri = imageUri
        if (uri == null) { recognizing = false; return@LaunchedEffect }
        try {
            val text = OfflineOcr.recognize(appContext, Uri.parse(uri), rotation, language)
            if (text.isBlank()) {
                error = R.string.scan_no_text
            } else {
                // Keep large OCR output out of the Activity's saved-state Bundle.
                draftPath = withContext(Dispatchers.IO) {
                    File.createTempFile("scan-", ".txt", drafts).apply { writeText(text) }.absolutePath
                }
                releaseImage()
                preview = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = R.string.scan_recognition_failed
        } finally {
            recognizing = false
        }
    }

    if (draftPath != null) {
        val text = remember(draftPath) { runCatching { draftFile()?.readText() }.getOrNull() }
        if (text != null) {
            EditorScreen(
                isNew = true,
                initialTitle = stringResource(R.string.scan_title),
                initialText = text,
                plainText = true,
                onCancel = ::close,
                onSave = { title, body ->
                    if (onSave(title, body)) draftFile()?.delete()
                },
            )
            return
        }
        LaunchedEffect(draftPath) { draftPath = null; error = R.string.scan_draft_failed }
    }

    Column(Modifier.fillMaxSize().background(Palette.Bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.scan_title), onBack = ::close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.scan_hint), color = Palette.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            if (preview != null) {
                Image(preview!!.asImageBitmap(), stringResource(R.string.scan_preview),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(vertical = 8.dp), contentScale = ContentScale.Fit)
            }
            if (error != 0) Text(stringResource(error), color = Palette.Danger)
            if (recognizing) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
                Text(stringResource(R.string.scan_working), color = Palette.TextSecondary)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((code, label) in listOf("chi_sim" to R.string.scan_lang_simplified,
                        "chi_tra" to R.string.scan_lang_traditional, "eng" to R.string.scan_lang_english)) {
                        TextButton(onClick = { language = code }, enabled = language != code) {
                            Text(stringResource(label), color = if (language == code) Palette.Accent else Palette.TextSecondary)
                        }
                    }
                }
                TextButton(onClick = {
                    releaseImage()
                    imageUri = null
                    error = 0
                    val result = runCatching {
                        val file = File.createTempFile("scan-", ".jpg", photos)
                        cameraPath = file.absolutePath
                        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.scan", file))
                    }
                    if (result.isFailure) { cameraFile()?.delete(); cameraPath = null; error = R.string.scan_camera_failed }
                }) { Text(stringResource(R.string.scan_camera)) }
                TextButton(onClick = {
                    runCatching { gallery.launch(arrayOf("image/*")) }.onFailure { error = R.string.scan_image_failed }
                }) { Text(stringResource(R.string.scan_gallery)) }
                if (preview != null) {
                    TextButton(onClick = { rotation = (rotation + 90) % 360 }) { Text(stringResource(R.string.scan_rotate)) }
                    TextButton(onClick = { error = 0; recognizing = true }) { Text(stringResource(R.string.scan_recognize)) }
                }
            }
        }
    }
}
