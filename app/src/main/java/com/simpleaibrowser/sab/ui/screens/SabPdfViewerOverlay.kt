package com.simpleaibrowser.sab.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaibrowser.sab.core.browser.SabViewerRequest
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private class PdfRenderHandle(
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer,
    val tempCacheFile: File?
) {
    val mutex = Mutex()

    fun closeQuietly() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        tempCacheFile?.let { runCatching { it.delete() } }
    }
}

@Composable
fun SabPdfViewerOverlay(
    request: SabViewerRequest.Pdf,
    onShareUri: (String, String) -> Unit,
    onOpenExternal: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val sabColors = LocalSabColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var renderHandle by remember(request) { mutableStateOf<PdfRenderHandle?>(null) }
    var pageCount by remember(request) { mutableIntStateOf(0) }
    var loadError by remember(request) { mutableStateOf<String?>(null) }
    var isLoading by remember(request) { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var invertPaper by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var resolvedShareTarget by remember(request) { mutableStateOf(request.shareUri ?: request.contentUri ?: request.sourceUrl) }

    val bitmapCache = remember(request) {
        object : LruCache<String, Bitmap>(16) {}
    }

    LaunchedEffect(request) {
        isLoading = true
        loadError = null
        renderHandle?.closeQuietly()
        renderHandle = null
        pageCount = 0

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val localFile = request.localFilePath?.takeIf { it.isNotBlank() }?.let { File(it) }
                var tempDownload: File? = null
                val pfd: ParcelFileDescriptor = when {
                    localFile != null && localFile.exists() && localFile.isFile -> {
                        ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    !request.contentUri.isNullOrBlank() -> {
                        val uri = Uri.parse(request.contentUri)
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?: error("Cannot read document URI")
                    }
                    !request.sourceUrl.isNullOrBlank() -> {
                        val url = request.sourceUrl
                        val client = OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(45, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .build()
                        val cacheDir = File(context.cacheDir, "pdf_preview").apply { mkdirs() }
                        val target = File(cacheDir, "sab_pdf_${System.currentTimeMillis()}.pdf")
                        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                            if (!resp.isSuccessful) error("HTTP ${resp.code} loading PDF")
                            val body = resp.body ?: error("Empty PDF response")
                            target.outputStream().use { out ->
                                body.byteStream().copyTo(out)
                            }
                        }
                        tempDownload = target
                        ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    else -> error("No readable PDF source provided")
                }

                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    pfd.close()
                    tempDownload?.delete()
                    error("This PDF contains zero pages")
                }
                PdfRenderHandle(pfd, renderer, tempDownload)
            }
        }

        result.onSuccess { handle ->
            renderHandle = handle
            pageCount = handle.renderer.pageCount
            isLoading = false
        }.onFailure { err ->
            loadError = err.message ?: "Failed to open PDF document"
            isLoading = false
        }
    }

    DisposableEffect(request) {
        onDispose {
            bitmapCache.evictAll()
            renderHandle?.closeQuietly()
            renderHandle = null
        }
    }

    val currentPageIndex by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            first.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("sab_pdf_viewer_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sabColors.chromeBar)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("pdf_close_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close PDF viewer")
                }

                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pageCount > 0) {
                        Text(
                            text = "Page ${currentPageIndex + 1} of $pageCount • ${(zoomScale * 100).roundToInt()}%",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = sabColors.subtleText
                        )
                    }
                }

                IconButton(
                    onClick = { invertPaper = !invertPaper },
                    modifier = Modifier.testTag("pdf_invert_btn")
                ) {
                    Icon(
                        imageVector = if (invertPaper) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (invertPaper) "Switch to paper light view" else "Night invert paper",
                        tint = if (invertPaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                resolvedShareTarget?.let { target ->
                    IconButton(onClick = { onShareUri(target, request.title) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                    }
                }

                val extUri = request.contentUri ?: request.shareUri ?: request.sourceUrl
                if (!extUri.isNullOrBlank()) {
                    IconButton(onClick = { onOpenExternal(extUri, "application/pdf") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in external app")
                    }
                }
            }

            // Document Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (invertPaper) Color(0xFF07090F) else Color(0xFF1A1E29))
            ) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Rendering PDF document...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    loadError != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Text("Unable to open PDF", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = loadError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                            val fallbackUri = request.contentUri ?: request.shareUri ?: request.sourceUrl
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onDismiss) {
                                    Text("Close")
                                }
                                if (!fallbackUri.isNullOrBlank()) {
                                    Button(onClick = { onOpenExternal(fallbackUri, "application/pdf") }) {
                                        Text("Open External")
                                    }
                                }
                            }
                        }
                    }
                    renderHandle != null && pageCount > 0 -> {
                        val handle = renderHandle!!
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val viewportWidthDp = maxWidth
                            val density = LocalDensity.current
                            val basePixelWidth = with(density) { (viewportWidthDp - 24.dp).toPx() }.roundToInt().coerceIn(480, 1800)
                            val horizontalScroll = rememberScrollState()

                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (zoomScale > 1.01f) Modifier.horizontalScroll(horizontalScroll)
                                        else Modifier
                                    )
                            ) {
                                items(pageCount, key = { it }) { pageIdx ->
                                    PdfPageBitmapCard(
                                        handle = handle,
                                        pageIndex = pageIdx,
                                        renderWidthPx = (basePixelWidth * zoomScale.coerceAtMost(2.2f)).roundToInt(),
                                        displayWidthDp = (viewportWidthDp - 24.dp) * zoomScale,
                                        invertPaper = invertPaper,
                                        cache = bitmapCache
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Scrubber & Zoom Bar
            if (pageCount > 0 && loadError == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(sabColors.chromeBar)
                        .border(0.6.dp, sabColors.chromeBorder)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pageCount > 1) {
                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = { pos ->
                                scope.launch {
                                    listState.scrollToItem(pos.roundToInt().coerceIn(0, pageCount - 1))
                                }
                            },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(sabColors.chromeSurfaceElevated)
                                .clickable(enabled = pageCount > 1) { showJumpDialog = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page ${currentPageIndex + 1} / $pageCount",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.25f).coerceIn(1.0f, 3.0f) },
                                enabled = zoomScale > 1.0f,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(sabColors.chromeSurfaceElevated)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Zoom out", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${(zoomScale * 100).roundToInt()}%",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { zoomScale = 1.0f }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.25f).coerceIn(1.0f, 3.0f) },
                                enabled = zoomScale < 3.0f,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(sabColors.chromeSurfaceElevated)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Zoom in", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJumpDialog && pageCount > 1) {
        var pageInput by remember { mutableStateOf((currentPageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Go to Page (1 – $pageCount)") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { ch -> pageInput = ch.filter { it.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Page number") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = (pageInput.toIntOrNull() ?: 1).coerceIn(1, pageCount) - 1
                        showJumpDialog = false
                        scope.launch { listState.scrollToItem(target) }
                    }
                ) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PdfPageBitmapCard(
    handle: PdfRenderHandle,
    pageIndex: Int,
    renderWidthPx: Int,
    displayWidthDp: androidx.compose.ui.unit.Dp,
    invertPaper: Boolean,
    cache: LruCache<String, Bitmap>
) {
    val cacheKey = "${pageIndex}_${renderWidthPx}_$invertPaper"
    var bitmap by remember(cacheKey) { mutableStateOf(cache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap != null) return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) {
            handle.mutex.withLock {
                cache.get(cacheKey) ?: runCatching {
                    handle.renderer.openPage(pageIndex).use { page ->
                        val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val w = renderWidthPx.coerceAtLeast(320)
                        val h = (w * aspect).roundToInt().coerceAtLeast(320)
                        val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        raw.eraseColor(AndroidColor.WHITE)
                        page.render(raw, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val finalBmp = if (invertPaper) {
                            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(out)
                            val matrix = ColorMatrix(
                                floatArrayOf(
                                    -1f, 0f, 0f, 0f, 255f,
                                    0f, -1f, 0f, 0f, 255f,
                                    0f, 0f, -1f, 0f, 255f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                            val paint = Paint().apply {
                                colorFilter = ColorMatrixColorFilter(matrix)
                            }
                            canvas.drawBitmap(raw, 0f, 0f, paint)
                            raw.recycle()
                            out
                        } else {
                            raw
                        }
                        cache.put(cacheKey, finalBmp)
                        finalBmp
                    }
                }.getOrNull()
            }
        }
        bitmap = rendered
    }

    Box(
        modifier = Modifier
            .width(displayWidthDp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (invertPaper) Color(0xFF10131C) else Color.White),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null && !current.isRecycled) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        }
    }
}
