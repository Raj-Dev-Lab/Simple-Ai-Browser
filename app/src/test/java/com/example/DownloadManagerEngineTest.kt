package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadSegmentState
import com.simpleaibrowser.sab.core.browser.TorrentFileEntry
import com.simpleaibrowser.sab.downloads.DownloadFileSystem
import com.simpleaibrowser.sab.downloads.HttpTransferOutcome
import com.simpleaibrowser.sab.downloads.SegmentedHttpDownloader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadManagerEngineTest {

    private lateinit var context: Context
    private lateinit var fileSystem: DownloadFileSystem

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fileSystem = DownloadFileSystem(context)
    }

    @Test
    fun `DownloadKind infers media documents archives apks and torrents accurately`() {
        assertEquals(DownloadKind.MAGNET, DownloadKind.infer("ubuntu", null, "magnet:?xt=urn:btih:0123456789"))
        assertEquals(DownloadKind.TORRENT, DownloadKind.infer("debian.torrent", "application/x-bittorrent", "https://cdimage.debian.org/debian.torrent"))
        assertEquals(DownloadKind.VIDEO, DownloadKind.infer("clip.mp4", "video/mp4", "https://cdn.example.com/clip.mp4"))
        assertEquals(DownloadKind.AUDIO, DownloadKind.infer("track.flac", "audio/flac", "https://cdn.example.com/track.flac"))
        assertEquals(DownloadKind.IMAGE, DownloadKind.infer("wallpaper.webp", "image/webp", "https://cdn.example.com/wallpaper.webp"))
        assertEquals(DownloadKind.DOCUMENT, DownloadKind.infer("manual.pdf", "application/pdf", "https://example.com/manual.pdf"))
        assertEquals(DownloadKind.ARCHIVE, DownloadKind.infer("bundle.tar.gz", "application/gzip", "https://example.com/bundle.tar.gz"))
        assertEquals(DownloadKind.APPLICATION, DownloadKind.infer("app.apk", "application/vnd.android.package-archive", "https://example.com/app.apk"))
        assertEquals(DownloadKind.FILE, DownloadKind.infer("payload.bin", "application/octet-stream", "https://example.com/payload.bin"))
    }

    @Test
    fun `DownloadFileSystem sanitizes unsafe names and supports vault CRUD and SHA-256`(): Unit = runBlocking {
        assertEquals("report.pdf", DownloadFileSystem.sanitizeFileName("../../etc/report.pdf"))
        assertEquals("a_b_c", DownloadFileSystem.sanitizeFileName("a:b?c"))
        assertEquals("Media/Sub", DownloadFileSystem.sanitizeRelativeSubpath("../Media//../Sub/"))

        val createdFolder = fileSystem.createFolder("", "TestVault_${System.nanoTime()}").getOrThrow()
        assertTrue(createdFolder.exists() && createdFolder.isDirectory)

        val relFolder = createdFolder.name
        val target = fileSystem.resolveTargetFile("sample.bin", relFolder, ensureUnique = true)
        val payload = ByteArray(4096) { i -> ((i * 31) and 0xFF).toByte() }
        target.writeBytes(payload)

        val sha = fileSystem.computeSha256(target)
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        assertEquals(expectedSha, sha)

        val entries = fileSystem.listDirectory(relFolder)
        assertEquals(1, entries.size)
        assertEquals("sample.bin", entries.first().name)

        val renamed = fileSystem.renameEntry(target.absolutePath, "renamed_sample.bin").getOrThrow()
        assertEquals("renamed_sample.bin", renamed.name)
        assertTrue(renamed.exists())
        assertFalse(target.exists())

        assertTrue(fileSystem.deleteEntry(createdFolder.absolutePath))
        assertFalse(createdFolder.exists())
    }

    @Test
    fun `segment and torrent entry json helpers round-trip cleanly`() {
        val segments = listOf(
            DownloadSegmentState(0, 0L, 499L, 500L, true),
            DownloadSegmentState(1, 500L, 999L, 250L, false)
        )
        val parsedSegs = DownloadSegmentState.fromJson(DownloadSegmentState.toJson(segments))
        assertEquals(segments, parsedSegs)

        val files = listOf(
            TorrentFileEntry(0, "video/movie.mp4", 1_000_000L, 250_000L, 7),
            TorrentFileEntry(1, "notes/readme.txt", 2048L, 0L, 0)
        )
        val parsedFiles = TorrentFileEntry.fromJson(TorrentFileEntry.toJson(files))
        assertEquals(files, parsedFiles)
        assertTrue(parsedFiles[0].isSelected)
        assertFalse(parsedFiles[1].isSelected)
    }

    @Test
    fun `buildSegments creates exact contiguous byte ranges`() {
        val single = SegmentedHttpDownloader.buildSegments(600_000L, 8)
        assertEquals(1, single.size)
        assertEquals(0L, single[0].startByte)
        assertEquals(599_999L, single[0].endByte)

        val multi = SegmentedHttpDownloader.buildSegments(4_500_000L, 4)
        assertEquals(4, multi.size)
        assertEquals(0L, multi.first().startByte)
        assertEquals(4_499_999L, multi.last().endByte)
        for (i in 0 until multi.lastIndex) {
            assertEquals(multi[i].endByte + 1L, multi[i + 1].startByte)
        }
    }

    @Test
    fun `SegmentedHttpDownloader executes real 4-thread Range download with byte-exact integrity`(): Unit = runBlocking {
        // 2.2 MB deterministic payload (exceeds 1 MB threshold so 4 segments run in parallel)
        val payload = ByteArray(2_250_000) { idx -> ((idx * 17 + 7) and 0xFF).toByte() }
        val server = LocalRangeHttpServer(payload = payload, supportRanges = true)
        server.start()
        try {
            val outFile = File(fileSystem.vaultRoot(), "multi_range_${System.nanoTime()}.bin")
            val downloader = SegmentedHttpDownloader()
            var lastSegments = emptyList<DownloadSegmentState>()

            val outcome = downloader.download(
                url = "${server.baseUrl}/archive.bin",
                targetFile = outFile,
                desiredConnections = 4,
                existingSegments = emptyList(),
                knownTotalBytes = -1L,
                knownEtag = null,
                userAgent = "SAB-Test/1.4",
                referrer = null,
                shouldPause = { false },
                shouldCancel = { false },
                isMeteredBlocked = { false },
                speedLimitKbpsProvider = { 0 },
                onProgress = { progress ->
                    if (progress.segments.size > 1) lastSegments = progress.segments
                }
            )

            assertTrue("Expected Completed outcome, got $outcome", outcome is HttpTransferOutcome.Completed)
            assertTrue(outFile.exists())
            assertEquals(payload.size.toLong(), outFile.length())
            assertArrayEquals(payload, outFile.readBytes())
            assertEquals(4, lastSegments.size)
            assertTrue(server.rangeRequestCount.get() >= 4)
            outFile.delete()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `SegmentedHttpDownloader supports pause mid-stream and clean resume without Range corruption`(): Unit = runBlocking {
        val payload = ByteArray(1_600_000) { idx -> ((idx * 13 + 3) and 0xFF).toByte() }
        val server = LocalRangeHttpServer(payload = payload, supportRanges = true, chunkPauseMs = 15L)
        server.start()
        try {
            val outFile = File(fileSystem.vaultRoot(), "pause_resume_${System.nanoTime()}.bin")
            val downloader = SegmentedHttpDownloader()
            val pauseSignal = AtomicBoolean(false)

            val firstPass = downloader.download(
                url = "${server.baseUrl}/pause.bin",
                targetFile = outFile,
                desiredConnections = 2,
                existingSegments = emptyList(),
                knownTotalBytes = -1L,
                knownEtag = null,
                userAgent = null,
                referrer = null,
                shouldPause = { pauseSignal.get() },
                shouldCancel = { false },
                isMeteredBlocked = { false },
                speedLimitKbpsProvider = { 0 },
                onProgress = { progress ->
                    if (progress.downloadedBytes >= 128_000L) {
                        pauseSignal.set(true)
                    }
                }
            )

            assertTrue("Expected Paused outcome, got $firstPass", firstPass is HttpTransferOutcome.Paused)
            val pausedState = (firstPass as HttpTransferOutcome.Paused).progress
            assertTrue(pausedState.segments.isNotEmpty())
            assertTrue(pausedState.downloadedBytes > 0L)

            val resumed = downloader.download(
                url = "${server.baseUrl}/pause.bin",
                targetFile = outFile,
                desiredConnections = 2,
                existingSegments = pausedState.segments,
                knownTotalBytes = pausedState.totalBytes,
                knownEtag = pausedState.etag,
                userAgent = null,
                referrer = null,
                shouldPause = { false },
                shouldCancel = { false },
                isMeteredBlocked = { false },
                speedLimitKbpsProvider = { 0 },
                onProgress = {}
            )

            assertTrue("Expected Completed after resume, got $resumed", resumed is HttpTransferOutcome.Completed)
            assertArrayEquals(payload, outFile.readBytes())
            outFile.delete()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `SegmentedHttpDownloader falls back cleanly to single stream when server ignores Range`(): Unit = runBlocking {
        val payload = ByteArray(400_000) { idx -> ((idx * 5 + 11) and 0xFF).toByte() }
        val server = LocalRangeHttpServer(payload = payload, supportRanges = false)
        server.start()
        try {
            val outFile = File(fileSystem.vaultRoot(), "single_fallback_${System.nanoTime()}.bin")
            val downloader = SegmentedHttpDownloader()

            val outcome = downloader.download(
                url = "${server.baseUrl}/stream.bin",
                targetFile = outFile,
                desiredConnections = 8,
                existingSegments = emptyList(),
                knownTotalBytes = -1L,
                knownEtag = null,
                userAgent = null,
                referrer = null,
                shouldPause = { false },
                shouldCancel = { false },
                isMeteredBlocked = { false },
                speedLimitKbpsProvider = { 0 },
                onProgress = {}
            )

            assertTrue("Expected Completed outcome, got $outcome", outcome is HttpTransferOutcome.Completed)
            assertArrayEquals(payload, outFile.readBytes())
            outFile.delete()
        } finally {
            server.stop()
        }
    }

    private class LocalRangeHttpServer(
        private val payload: ByteArray,
        private val supportRanges: Boolean,
        private val chunkPauseMs: Long = 0L
    ) {
        private var socket: ServerSocket? = null
        private val pool = Executors.newCachedThreadPool()
        private val running = AtomicBoolean(false)
        val rangeRequestCount = AtomicInteger(0)

        val baseUrl: String
            get() = "http://127.0.0.1:${socket?.localPort}"

        fun start() {
            socket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
            running.set(true)
            pool.submit {
                while (running.get()) {
                    val client = runCatching { socket?.accept() }.getOrNull() ?: break
                    pool.submit { handleClient(client) }
                }
            }
        }

        fun stop() {
            running.set(false)
            runCatching { socket?.close() }
            pool.shutdownNow()
        }

        private fun handleClient(client: java.net.Socket) {
            client.use { sock ->
                val input = sock.getInputStream().bufferedReader()
                val reqLine = input.readLine() ?: return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase()] =
                            line.substring(colon + 1).trim()
                    }
                }
                val method = reqLine.substringBefore(' ').uppercase()
                val rangeHeader = headers["range"]
                val out = BufferedOutputStream(sock.getOutputStream())

                if (supportRanges && rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    rangeRequestCount.incrementAndGet()
                    val spec = rangeHeader.removePrefix("bytes=").trim()
                    val start = spec.substringBefore('-').toIntOrNull() ?: 0
                    val end = spec.substringAfter('-', "").toIntOrNull() ?: (payload.size - 1)
                    val safeEnd = end.coerceAtMost(payload.size - 1)
                    val len = (safeEnd - start + 1).coerceAtLeast(0)
                    val hdr = buildString {
                        append("HTTP/1.1 206 Partial Content\r\n")
                        append("Accept-Ranges: bytes\r\n")
                        append("Content-Type: application/octet-stream\r\n")
                        append("ETag: \"sab-test-etag\"\r\n")
                        append("Content-Range: bytes $start-$safeEnd/${payload.size}\r\n")
                        append("Content-Length: $len\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    out.write(hdr.toByteArray(Charsets.US_ASCII))
                    if (method != "HEAD" && len > 0) {
                        writeChunkedPayload(out, start, len)
                    }
                } else {
                    val hdr = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        if (supportRanges) append("Accept-Ranges: bytes\r\n")
                        append("Content-Type: application/octet-stream\r\n")
                        append("ETag: \"sab-test-etag\"\r\n")
                        append("Content-Length: ${payload.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    out.write(hdr.toByteArray(Charsets.US_ASCII))
                    if (method != "HEAD") {
                        writeChunkedPayload(out, 0, payload.size)
                    }
                }
                out.flush()
            }
        }

        private fun writeChunkedPayload(out: BufferedOutputStream, offset: Int, len: Int) {
            var cursor = 0
            val chunk = 32_768
            while (cursor < len) {
                val n = minOf(chunk, len - cursor)
                out.write(payload, offset + cursor, n)
                out.flush()
                cursor += n
                if (chunkPauseMs > 0L) {
                    Thread.sleep(chunkPauseMs)
                }
            }
        }
    }
}
