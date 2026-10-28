package com.olerast.suflyor.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.olerast.suflyor.App
import com.olerast.suflyor.R
import com.olerast.suflyor.diag.DiagLog
import com.olerast.suflyor.doc.DocumentImporter
import com.olerast.suflyor.doc.OfflineOcr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ScanScreen(onBack: () -> Unit, onSave: (String, String) -> Boolean) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val defaultTitle = stringResource(R.string.scan_title)
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var rotation by rememberSaveable { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var recognized by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var recognizing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val language = App.instance.settings.scanLanguage
    val photos = remember { File(context.cacheDir, "scan-photos").apply { mkdirs() } }
    fun releaseImage() {
        imageUri?.let { uri -> runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        cameraPath?.let { File(it).takeIf { f -> f.parentFile?.canonicalFile == photos.canonicalFile }?.delete() }
        cameraPath = null
    }
    fun close() { releaseImage(); onBack() }
    fun retake() {
        releaseImage()
        imageUri = null
        recognized = false
        recognizing = false
        text = ""
        preview = null
    }
    BackHandler { if (imageUri != null) retake() else close() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            releaseImage()
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString()
            rotation = 0
            recognized = false
            text = ""
            retry++
        }
    }
    fun pickImage() {
        runCatching { gallery.launch(arrayOf("image/*")) }.onFailure {
            Toast.makeText(context, R.string.scan_image_failed, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        App.instance.engine.releaseIdleRecognizer("document scan")
        val active = cameraPath
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            for (folder in listOf(photos, File(context.noBackupFilesDir, "scan-drafts"))) {
                folder.listFiles()?.filter { it.isFile && it.lastModified() < cutoff && it.absolutePath != active }?.forEach { it.delete() }
            }
        }
    }
    LaunchedEffect(imageUri, rotation, retry) {
        val ticket = ++generation
        val uri = imageUri ?: return@LaunchedEffect
        error = 0
        progress = 0f
        if (!recognized) { recognizing = true; preview = null }
        try {
            preview = withContext(Dispatchers.IO) { OfflineOcr.decode(appContext, Uri.parse(uri), rotation) }
            if (!recognized) {
                recognizing = true
                val result = OfflineOcr.recognize(appContext, Uri.parse(uri), rotation, language) { done, total ->
                    if (generation == ticket) progress = if (total == 0) 0f else done.toFloat() / total
                }
                text = result
                recognized = true
                if (text.isBlank()) error = R.string.scan_no_text
            }
        } catch (e: CancellationException) { throw e }
        catch (e: LinkageError) {
            DiagLog.e("OCR native library initialization failed", e)
            error = R.string.scan_recognition_failed
        }
        catch (_: Exception) { error = R.string.scan_recognition_failed }
        finally { if (generation == ticket) recognizing = false }
    }

    if (imageUri == null) {
        ScanCamera(
            onBack = ::close,
            onGallery = ::pickImage,
            newPhoto = { File.createTempFile("scan-", ".jpg", photos).also { cameraPath = it.absolutePath } },
            onPhoto = { file ->
                imageUri = FileProvider.getUriForFile(context, "${context.packageName}.scan", file).toString()
                rotation = 0
                recognized = false
                retry++
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Palette.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(stringResource(R.string.scan_review_title), onBack = ::retake)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.scan_review_steps), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                Text(stringResource(if (language == "en") R.string.scan_lang_english else R.string.scan_lang_chinese),
                    color = Palette.Accent, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Palette.AccentSoft).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
                preview?.let { Image(it.asImageBitmap(), stringResource(R.string.scan_preview), Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = ::retake) { Text(stringResource(R.string.scan_retake)) }
                TextButton(onClick = ::pickImage, enabled = !recognizing) { Text(stringResource(R.string.scan_change_image)) }
                TextButton(onClick = { rotation = (rotation + 90) % 360; recognized = false; text = "" }, enabled = !recognizing) { Text(stringResource(R.string.scan_rotate)) }
            }
            if (recognizing) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.scan_working), color = Palette.Accent, style = MaterialTheme.typography.titleMedium)
                    if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Palette.Accent)
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Palette.Accent)
                    Text(stringResource(R.string.scan_local_hint), color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (error != 0) {
                Text(stringResource(error), color = Palette.Danger)
                TextButton(onClick = { recognized = false; retry++ }) { Text(stringResource(R.string.scan_retry)) }
            }
            if (recognized && error == 0) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.scan_result), color = Palette.Text, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.scan_review_hint), color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.scan_document_name), color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
                    BasicTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Palette.Text), cursorBrush = SolidColor(Palette.Accent), singleLine = true,
                        decorationBox = { inner -> Box { if (title.isBlank()) Text(stringResource(R.string.scan_title), color = Palette.TextMuted); inner() } })
                    BasicTextField(text, { text = it.take(DocumentImporter.MAX_CHARS) }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text), cursorBrush = SolidColor(Palette.Accent))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { if (onSave(title.ifBlank { defaultTitle }, text)) releaseImage() },
            enabled = recognized && text.isNotBlank() && !recognizing,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
            modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.scan_save), style = MaterialTheme.typography.labelLarge)
        }
    }
}
