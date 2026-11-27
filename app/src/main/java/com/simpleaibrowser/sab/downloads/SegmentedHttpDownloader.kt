package com.simpleaibrowser.sab.downloads

import com.simpleaibrowser.sab.core.browser.DownloadSegmentState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class HttpProbeResult(
    val finalUrl: String,
    val contentLength: Long,
    val supportsRange: Boolean,
    val etag: String?,
    val mimeType: String?,
    val suggestedFileName: String?
)

data class HttpDownloadProgress(
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val supportsRange: Boolean,
    val etag: String?,
    val segments: List<DownloadSegmentState>
)

sealed interface HttpTransferOutcome {
    data class Completed(
        val file: File,
        val totalBytes: Long,
        val mimeType: String?,
        val etag: String?
    ) : HttpTransferOutcome

    data class Paused(
        val waitingWifi: Boolean,
        val progress: HttpDownloadProgress
    ) : HttpTransferOutcome

    data object Cancelled : HttpTransferOutcome

    data class Failed(
        val message: String,
        val progress: HttpDownloadProgress?
    ) : HttpTransferOutcome
}

class SegmentedHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        private const val MIN_SEGMENT_BYTES = 512L * 1024L // 512 KB threshold before splitting

        fun buildSegments(totalBytes: Long, threadCount: Int): List<DownloadSegmentState> {
            if (totalBytes <= 0L) return emptyList()
            val clampedThreads = threadCount.coerceIn(1, 16)
            val effectiveThreads = if (totalBytes < MIN_SEGMENT_BYTES * 2L) 1 else {
                clampedThreads.coerceAtMost((totalBytes / MIN_SEGMENT_BYTES).toInt().coerceAtLeast(1))
            }
            if (effectiveThreads <= 1) {
                return listOf(
                    DownloadSegmentState(
                        index = 0,
                        startByte = 0L,
                        endByte = totalBytes - 1L,
                        downloadedBytes = 0L,
                        completed = false
                    )
                )
            }
            val chunkSize = totalBytes / effectiveThreads
            return (0 until effectiveThreads).map { i ->
                val start = i * chunkSize
                val end = if (i == effectiveThreads - 1) totalBytes - 1L else ((i + 1) * chunkSize) - 1L
                DownloadSegmentState(
                    index = i,
                    startByte = start,
                    endByte = end,
                    downloadedBytes = 0L,
                    completed = false
                )
            }
        }

        fun parseContentDispositionFileName(header: String?): String? {
            if (header.isNullOrBlank()) return null
            val utf8Match = Regex("filename\\*=UTF-8''([^;\\r\\n]+)", RegexOption.IGNORE_CASE).find(header)
            if (utf8Match != null) {
                val raw = utf8Match.groupValues[1].trim().trim('"')
                val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                if (decoded.isNotBlank()) return decoded
            }
            val basicMatch = Regex("filename=\"?([^\";\\r\\n]+)\"?", RegexOption.IGNORE_CASE).find(header)
            return basicMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    suspend fun probe(
        url: String,
        userAgent: String? = null,
        referrer: String? = null
    ): HttpProbeResult = withContext(Dispatchers.IO) {
        val headReq = buildRequest(url, userAgent, referrer).head().build()
        val headResult = runCatching {
            client.newCall(headReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val acceptRanges = resp.header("Accept-Ranges").orEmpty()
                val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val mime = resp.header("Content-Type")?.substringBefore(';')?.trim()
                val disp = parseContentDispositionFileName(resp.header("Content-Disposition"))
                val etag = resp.header("ETag")
                HttpProbeResult(
                    finalUrl = resp.request.url.toString(),
                    contentLength = contentLength,
                    supportsRange = acceptRanges.contains("bytes", ignoreCase = true) && contentLength > 0L,
                    etag = etag,
                    mimeType = mime,
                    suggestedFileName = disp
                )
            }
        }.getOrNull()

        if (headResult != null && (headResult.supportsRange || headResult.contentLength > 0L)) {
            return@withContext headResult
        }

        // Probe with single-byte GET to accurately detect Range support & Content-Range total size
        val rangeProbeReq = buildRequest(url, userAgent, referrer)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        runCatching {
            client.newCall(rangeProbeReq).execute().use { resp ->
                val contentRange = resp.header("Content-Range") // e.g. "bytes 0-0/123456"
                val totalFromRange = contentRange
                    ?.substringAfter('/')
                    ?.trim()
                    ?.toLongOrNull() ?: -1L
                val lengthHeader = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val total = when {
                    totalFromRange > 0L -> totalFromRange
                    resp.code == 200 && lengthHeader > 0L -> lengthHeader
                    else -> headResult?.contentLength ?: -1L
                }
                val supportsRange = resp.code == 206 && total > 0L
                val mime = resp.header("Content-Type")?.substringBefore(';')?.trim() ?: headResult?.mimeType
                val disp = parseContentDispositionFileName(resp.header("Content-Disposition"))
                    ?: headResult?.suggestedFileName
                val etag = resp.header("ETag") ?: headResult?.etag
                HttpProbeResult(
                    finalUrl = resp.request.url.toString(),
                    contentLength = total,
                    supportsRange = supportsRange,
                    etag = etag,
                    mimeType = mime,
                    suggestedFileName = disp
                )
            }
        }.getOrElse {
            headResult ?: HttpProbeResult(
                finalUrl = url,
                contentLength = -1L,
                supportsRange = false,
                etag = null,
                mimeType = null,
                suggestedFileName = null
            )
        }
    }

    suspend fun download(
        url: String,
        targetFile: File,
        desiredConnections: Int,
        existingSegments: List<DownloadSegmentState>,
        knownTotalBytes: Long,
        knownEtag: String?,
        userAgent: String?,
        referrer: String?,
        shouldPause: () -> Boolean,
        shouldCancel: () -> Boolean,
        isMeteredBlocked: () -> Boolean,
        speedLimitKbpsProvider: () -> Int,
        onProgress: suspend (HttpDownloadProgress) -> Unit
    ): HttpTransferOutcome = withContext(Dispatchers.IO) {
        val partFile = File(targetFile.parentFile, "${targetFile.name}.sabpart")
        partFile.parentFile?.mkdirs()

        val probe = runCatching { probe(url, userAgent, referrer) }.getOrNull()
        val effectiveUrl = probe?.finalUrl ?: url
        val totalBytes = when {
            probe != null && probe.contentLength > 0L -> probe.contentLength
            knownTotalBytes > 0L -> knownTotalBytes
            else -> -1L
        }
        val supportsRange = probe?.supportsRange == true
        val etagChanged = !knownEtag.isNullOrBlank() &&
            !probe?.etag.isNullOrBlank() &&
            !knownEtag.equals(probe?.etag, ignoreCase = true)

        if (isMeteredBlocked()) {
            val savedSegs = if (!etagChanged) existingSegments else emptyList()
            val doneBytes = savedSegs.sumOf { it.downloadedBytes }
            return@withContext HttpTransferOutcome.Paused(
                waitingWifi = true,
                progress = HttpDownloadProgress(
                    fileName = targetFile.name,
                    totalBytes = totalBytes,
                    downloadedBytes = doneBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                    supportsRange = supportsRange,
                    etag = probe?.etag ?: knownEtag,
                    segments = savedSegs
                )
            )
        }

        // Determine if we can reuse existing segments
        val reusableSegments = existingSegments.takeIf {
            it.isNotEmpty() &&
                supportsRange &&
                !etagChanged &&
                partFile.exists() &&
                totalBytes > 0L &&
                it.sumOf { seg -> seg.totalBytes } == totalBytes
        }

        val segments = (reusableSegments ?: if (supportsRange && totalBytes > 0L) {
            buildSegments(totalBytes, desiredConnections)
        } else {
            emptyList()
        }).toMutableList()

        if (supportsRange && totalBytes > 0L && segments.isNotEmpty()) {
            return@withContext executeSegmentedDownload(
                url = effectiveUrl,
                targetFile = targetFile,
                partFile = partFile,
                totalBytes = totalBytes,
                etag = probe?.etag ?: knownEtag,
                mimeType = probe?.mimeType,
                initialSegments = segments,
                userAgent = userAgent,
                referrer = referrer,
                shouldPause = shouldPause,
                shouldCancel = shouldCancel,
                isMeteredBlocked = isMeteredBlocked,
                speedLimitKbpsProvider = speedLimitKbpsProvider,
                onProgress = onProgress
            )
        }

        return@withContext executeSingleStreamDownload(
            url = effectiveUrl,
            targetFile = targetFile,
            partFile = partFile,
            knownTotalBytes = totalBytes,
            supportsRange = supportsRange,
            etag = probe?.etag ?: knownEtag,
            mimeType = probe?.mimeType,
            userAgent = userAgent,
            referrer = referrer,
            shouldPause = shouldPause,
            shouldCancel = shouldCancel,
            isMeteredBlocked = isMeteredBlocked,
            speedLimitKbpsProvider = speedLimitKbpsProvider,
            onProgress = onProgress
        )
    }

    private suspend fun executeSegmentedDownload(
        url: String,
        targetFile: File,
        partFile: File,
        totalBytes: Long,
        etag: String?,
        mimeType: String?,
        initialSegments: List<DownloadSegmentState>,
        userAgent: String?,
        referrer: String?,
        shouldPause: () -> Boolean,
        shouldCancel: () -> Boolean,
        isMeteredBlocked: () -> Boolean,
        speedLimitKbpsProvider: () -> Int,
        onProgress: suspend (HttpDownloadProgress) -> Unit
    ): HttpTransferOutcome {
        // Pre-size .sabpart cleanly on RandomAccessFile
        RandomAccessFile(partFile, "rw").use { raf ->
            if (raf.length() != totalBytes) {
                raf.setLength(totalBytes)
            }
        }

        val segmentArray = initialSegments.toTypedArray()
        val segmentLocks = Any()
        val downloadedCounter = AtomicLong(initialSegments.sumOf { it.downloadedBytes })
        val failureRef = AtomicReference<Throwable?>(null)
        val meterBlockedFlag = AtomicReference(false)
        val startMillis = System.currentTimeMillis()
        val initialBytes = downloadedCounter.get()
        val bucket = BandwidthThrottle(speedLimitKbpsProvider)

        coroutineScope {
            val monitorJob = async(Dispatchers.IO) {
                var lastBytes = initialBytes
                var lastTick = System.currentTimeMillis()
                while (true) {
                    delay(100L)
                    if (shouldCancel() || shouldPause() || isMeteredBlocked() || failureRef.get() != null) {
                        if (isMeteredBlocked()) meterBlockedFlag.set(true)
                        break
                    }
                    val now = System.currentTimeMillis()
                    val currentBytes = downloadedCounter.get()
                    val dtMs = (now - lastTick).coerceAtLeast(1L)
                    val speed = ((currentBytes - lastBytes).coerceAtLeast(0L) * 1000L) / dtMs
                    val avgSpeed = if (now - startMillis > 1200L) {
                        ((currentBytes - initialBytes).coerceAtLeast(0L) * 1000L) / (now - startMillis)
                    } else speed
                    val effectiveSpeed = if (speed > 0L) speed else avgSpeed
                    val eta = if (effectiveSpeed > 0L && totalBytes > currentBytes) {
                        (totalBytes - currentBytes) / effectiveSpeed
                    } else -1L
                    val snapshot = synchronized(segmentLocks) { segmentArray.toList() }
                    onProgress(
                        HttpDownloadProgress(
                            fileName = targetFile.name,
                            totalBytes = totalBytes,
                            downloadedBytes = currentBytes.coerceAtMost(totalBytes),
                            speedBytesPerSec = effectiveSpeed,
                            etaSeconds = eta,
                            supportsRange = true,
                            etag = etag,
                            segments = snapshot
                        )
                    )
                    lastBytes = currentBytes
                    lastTick = now
                    if (snapshot.all { it.completed }) break
                }
            }

            val workers = initialSegments.indices.map { idx ->
                async(Dispatchers.IO) {
                    val seg = synchronized(segmentLocks) { segmentArray[idx] }
                    if (seg.completed) return@async
                    val resumeOffset = seg.startByte + seg.downloadedBytes
                    if (resumeOffset > seg.endByte) {
                        synchronized(segmentLocks) {
                            segmentArray[idx] = seg.copy(completed = true)
                        }
                        return@async
                    }

                    val req = buildRequest(url, userAgent, referrer)
                        .header("Range", "bytes=$resumeOffset-${seg.endByte}")
                        .get()
                        .build()

                    try {
                        client.newCall(req).execute().use { resp ->
                            if (resp.code != 206 && resp.code != 200) {
                                throw IOException("HTTP ${resp.code} ${resp.message}")
                            }
                            val body = resp.body ?: throw IOException("Empty response body")
                            val raf = RandomAccessFile(partFile, "rw")
                            raf.use { fileOut ->
                                fileOut.seek(resumeOffset)
                                val stream = body.byteStream()
                                val buf = ByteArray(16 * 1024)
                                var writtenInSeg = seg.downloadedBytes
                                var expectedLimit = seg.totalBytes
                                while (writtenInSeg < expectedLimit) {
                                    if (shouldCancel()) throw CancellationException("Cancelled")
                                    if (shouldPause()) throw PausedSignal(false)
                                    if (isMeteredBlocked()) {
                                        meterBlockedFlag.set(true)
                                        throw PausedSignal(true)
                                    }
                                    val toRead = minOf(buf.size.toLong(), expectedLimit - writtenInSeg).toInt()
                                    val read = stream.read(buf, 0, toRead)
                                    if (read <= 0) break
                                    fileOut.write(buf, 0, read)
                                    writtenInSeg += read
                                    downloadedCounter.addAndGet(read.toLong())
                                    synchronized(segmentLocks) {
                                        segmentArray[idx] = segmentArray[idx].copy(
                                            downloadedBytes = writtenInSeg,
                                            completed = writtenInSeg >= expectedLimit
                                        )
                                    }
                                    bucket.throttle(read)
                                }
                                if (writtenInSeg < expectedLimit) {
                                    throw IOException("Segment $idx ended prematurely ($writtenInSeg / $expectedLimit bytes)")
                                }
                            }
                        }
                    } catch (p: PausedSignal) {
                        // Clean pause exit
                        if (p.waitingWifi) meterBlockedFlag.set(true)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        failureRef.compareAndSet(null, t)
                    }
                }
            }

            workers.awaitAll()
            monitorJob.cancel()
        }

        val finalSegments = synchronized(segmentLocks) { segmentArray.toList() }
        val finalProgress = HttpDownloadProgress(
            fileName = targetFile.name,
            totalBytes = totalBytes,
            downloadedBytes = downloadedCounter.get().coerceAtMost(totalBytes),
            speedBytesPerSec = 0L,
            etaSeconds = -1L,
            supportsRange = true,
            etag = etag,
            segments = finalSegments
        )

        if (shouldCancel()) {
            runCatching { partFile.delete() }
            return HttpTransferOutcome.Cancelled
        }
        if (meterBlockedFlag.get() || isMeteredBlocked()) {
            return HttpTransferOutcome.Paused(waitingWifi = true, progress = finalProgress)
        }
        if (shouldPause()) {
            return HttpTransferOutcome.Paused(waitingWifi = false, progress = finalProgress)
        }
        val err = failureRef.get()
        if (err != null) {
            return HttpTransferOutcome.Failed(
                message = err.message ?: "Download failed",
                progress = finalProgress
            )
        }
        if (!finalSegments.all { it.completed }) {
            return HttpTransferOutcome.Failed("Incomplete segments", finalProgress)
        }

        onProgress(finalProgress)
        finalizePartFile(partFile, targetFile)
        return HttpTransferOutcome.Completed(
            file = targetFile,
            totalBytes = totalBytes,
            mimeType = mimeType,
            etag = etag
        )
    }

    private suspend fun executeSingleStreamDownload(
        url: String,
        targetFile: File,
        partFile: File,
        knownTotalBytes: Long,
        supportsRange: Boolean,
        etag: String?,
        mimeType: String?,
        userAgent: String?,
        referrer: String?,
        shouldPause: () -> Boolean,
        shouldCancel: () -> Boolean,
        isMeteredBlocked: () -> Boolean,
        speedLimitKbpsProvider: () -> Int,
        onProgress: suspend (HttpDownloadProgress) -> Unit
    ): HttpTransferOutcome {
        val existingBytes = if (supportsRange && partFile.exists()) partFile.length() else 0L
        val builder = buildRequest(url, userAgent, referrer).get()
        if (existingBytes > 0L) {
            builder.header("Range", "bytes=$existingBytes-")
        }

        val bucket = BandwidthThrottle(speedLimitKbpsProvider)
        var downloaded = 0L
        var total = knownTotalBytes

        try {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return HttpTransferOutcome.Failed("HTTP ${resp.code} ${resp.message}", null)
                }
                val append = resp.code == 206 && existingBytes > 0L
                downloaded = if (append) existingBytes else 0L
                val bodyLen = resp.body?.contentLength() ?: -1L
                if (total <= 0L && bodyLen > 0L) {
                    total = downloaded + bodyLen
                }
                val resolvedMime = mimeType ?: resp.header("Content-Type")?.substringBefore(';')?.trim()
                val resolvedEtag = etag ?: resp.header("ETag")
                val body = resp.body ?: return HttpTransferOutcome.Failed("Empty server response", null)

                FileOutputStream(partFile, append).use { out ->
                    val input = body.byteStream()
                    val buf = ByteArray(16 * 1024)
                    var lastTick = System.currentTimeMillis()
                    var bytesSinceTick = 0L

                    while (true) {
                        if (shouldCancel()) {
                            runCatching { partFile.delete() }
                            return HttpTransferOutcome.Cancelled
                        }
                        if (shouldPause() || isMeteredBlocked()) {
                            val waitingWifi = isMeteredBlocked()
                            return HttpTransferOutcome.Paused(
                                waitingWifi = waitingWifi,
                                progress = HttpDownloadProgress(
                                    fileName = targetFile.name,
                                    totalBytes = total,
                                    downloadedBytes = downloaded,
                                    speedBytesPerSec = 0L,
                                    etaSeconds = -1L,
                                    supportsRange = supportsRange,
                                    etag = resolvedEtag,
                                    segments = emptyList()
                                )
                            )
                        }
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        downloaded += read
                        bytesSinceTick += read
                        bucket.throttle(read)

                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 450L) {
                            val speed = (bytesSinceTick * 1000L) / (now - lastTick).coerceAtLeast(1L)
                            val eta = if (speed > 0L && total > downloaded) (total - downloaded) / speed else -1L
                            onProgress(
                                HttpDownloadProgress(
                                    fileName = targetFile.name,
                                    totalBytes = total,
                                    downloadedBytes = downloaded,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta,
                                    supportsRange = supportsRange,
                                    etag = resolvedEtag,
                                    segments = emptyList()
                                )
                            )
                            lastTick = now
                            bytesSinceTick = 0L
                        }
                    }
                    out.flush()
                }

                finalizePartFile(partFile, targetFile)
                return HttpTransferOutcome.Completed(
                    file = targetFile,
                    totalBytes = targetFile.length().coerceAtLeast(downloaded),
                    mimeType = resolvedMime,
                    etag = resolvedEtag
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return HttpTransferOutcome.Failed(
                message = t.message ?: "Network error",
                progress = HttpDownloadProgress(
                    fileName = targetFile.name,
                    totalBytes = total,
                    downloadedBytes = downloaded,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                    supportsRange = supportsRange,
                    etag = etag,
                    segments = emptyList()
                )
            )
        }
    }

    private fun finalizePartFile(partFile: File, targetFile: File) {
        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }
    }

    private fun buildRequest(
        url: String,
        userAgent: String?,
        referrer: String?
    ): Request.Builder {
        val builder = Request.Builder().url(url)
        if (!userAgent.isNullOrBlank()) {
            builder.header("User-Agent", userAgent.trim())
        }
        if (!referrer.isNullOrBlank() && (referrer.startsWith("http://") || referrer.startsWith("https://"))) {
            builder.header("Referer", referrer)
        }
        return builder
    }

    private class PausedSignal(val waitingWifi: Boolean) : RuntimeException()

    private class BandwidthThrottle(private val limitKbpsProvider: () -> Int) {
        private var windowStartMs = System.currentTimeMillis()
        private val bytesInWindow = AtomicLong(0L)

        fun throttle(bytesRead: Int) {
            val kbps = limitKbpsProvider()
            if (kbps <= 0) return
            val bytesPerSec = kbps.toLong() * 1024L
            val accumulated = bytesInWindow.addAndGet(bytesRead.toLong())
            val elapsedMs = System.currentTimeMillis() - windowStartMs
            val expectedMs = (accumulated * 1000L) / bytesPerSec.coerceAtLeast(1024L)
            if (expectedMs > elapsedMs) {
                val sleepMs = (expectedMs - elapsedMs).coerceAtMost(500L)
                if (sleepMs > 2L) {
                    runCatching { Thread.sleep(sleepMs) }
                }
            }
            if (elapsedMs >= 1000L) {
                synchronized(this) {
                    val now = System.currentTimeMillis()
                    if (now - windowStartMs >= 1000L) {
                        windowStartMs = now
                        bytesInWindow.set(0L)
                    }
                }
            }
        }
    }
}
