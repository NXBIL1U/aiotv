package com.itrepos.aiotv.ui.screen.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MirrorService : Service() {

    companion object {
        const val ACTION_START = "mirror.CAST"
        const val ACTION_STOP  = "mirror.STOP"
        const val EXTRA_URL    = "url"
        const val EXTRA_TITLE  = "title"
        const val PORT      = 8888
        const val MDNS_HOST = "aiotv"          // accessible as http://aiotv.local:8888
        private const val CHANNEL_ID = "mirror_ch"
        private const val NOTIF_ID   = 2001
        // Output resolution — 720p gives good quality without flooding the hotspot
        private const val OUT_W = 1280
        private const val OUT_H = 720

        @Volatile var isRunning  = false
        @Volatile var castTitle  = ""
    }

    private val mainHandler    = Handler(Looper.getMainLooper())
    private val executor       = Executors.newCachedThreadPool()
    private val running        = AtomicBoolean(false)
    private val latestJpeg     = AtomicReference<ByteArray?>(null)
    private val clients        = CopyOnWriteArrayList<OutputStream>()
    private val mdnsResponder  = MdnsResponder(hostname = MDNS_HOST, port = PORT)
    private var multicastLock  : WifiManager.MulticastLock? = null

    private var exoPlayer     : ExoPlayer?    = null
    private var imageReader   : ImageReader?  = null
    private var serverSocket  : ServerSocket? = null

    // HTML page — shows a live MJPEG <img>. No codec needed on Tesla side.
    private val htmlPage = """<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>*{margin:0;padding:0;background:#000}
img{width:100vw;height:100vh;object-fit:contain;display:block}</style>
</head><body>
<img src="/stream" onerror="setTimeout(()=>location.reload(),3000)">
</body></html>""".toByteArray()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    val url   = intent.getStringExtra(EXTRA_URL)   ?: return START_NOT_STICKY
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                    castTitle = title
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIF_ID, buildNotification(title), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIF_ID, buildNotification(title))
                    }
                    if (!running.get()) {
                        acquireMulticastLock()
                        mdnsResponder.start()
                        startHttpServer()
                    }
                    loadMedia(url)
                }
                ACTION_STOP -> stopSelf()
            }
        } catch (e: Exception) {
            android.util.Log.e("MirrorService", "onStartCommand failed", e)
            com.itrepos.aiotv.util.CrashLogger.install(applicationContext)
        }
        return START_STICKY
    }

    // ── ExoPlayer setup (must run on main thread) ─────────────────────────────

    @OptIn(UnstableApi::class)
    private fun loadMedia(url: String) {
        mainHandler.post {
            // Release any previous player
            exoPlayer?.release()
            imageReader?.close()

            imageReader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Flag CPU_READ_RARELY so the allocator picks CPU-accessible memory
                // instead of GPU-only memory — prevents SIGSEGV in captureLoop
                ImageReader.newInstance(OUT_W, OUT_H, PixelFormat.RGBA_8888, 3,
                    HardwareBuffer.USAGE_CPU_READ_RARELY or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            } else {
                ImageReader.newInstance(OUT_W, OUT_H, PixelFormat.RGBA_8888, 3)
            }

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)
            val dsFactory = DefaultDataSource.Factory(this, httpFactory)

            exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                .build()
                .apply {
                    // Render video frames to ImageReader — no display surface needed.
                    // ExoPlayer uses hardware decode; the GPU writes to the Surface.
                    setVideoSurface(imageReader!!.surface)
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                    setWakeMode(C.WAKE_MODE_NETWORK)
                }

            running.set(true)
            isRunning = true
            // Start capturing frames once the player is set up
            executor.execute { captureLoop() }
        }
    }

    // ── Frame capture ─────────────────────────────────────────────────────────

    private fun captureLoop() {
        val bos = ByteArrayOutputStream(OUT_W * OUT_H)
        while (running.get()) {
            try {
                val image = imageReader?.acquireLatestImage()
                if (image == null) { Thread.sleep(16); continue }
                val bitmap = image.use { imageToBitmap(it) }
                if (bitmap == null) { Thread.sleep(16); continue }
                bos.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, bos)
                bitmap.recycle()
                val jpeg = bos.toByteArray()
                latestJpeg.set(jpeg)
                broadcastFrame(jpeg)
                Thread.sleep(33) // ~30 fps
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (_: Exception) {
                if (!running.get()) break
                runCatching { Thread.sleep(33) }
            }
        }
    }

    // ImageReader is created with USAGE_CPU_READ_RARELY on API 29+ so the buffer
    // is allocated in CPU-accessible memory — copyPixelsFromBuffer is safe.
    private fun imageToBitmap(image: Image): Bitmap? = try {
        val plane     = image.planes[0]
        val buf       = plane.buffer
        val rowStride = plane.rowStride
        val pixStride = plane.pixelStride
        val extraCols = (rowStride - pixStride * OUT_W) / pixStride
        val raw = Bitmap.createBitmap(OUT_W + extraCols, OUT_H, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buf)
        if (extraCols > 0) {
            Bitmap.createBitmap(raw, 0, 0, OUT_W, OUT_H).also { raw.recycle() }
        } else raw
    } catch (_: Exception) { null }

    private fun broadcastFrame(jpeg: ByteArray) {
        val header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n".toByteArray()
        val tail   = "\r\n".toByteArray()
        val dead   = mutableListOf<OutputStream>()
        for (out in clients) {
            try { out.write(header); out.write(jpeg); out.write(tail); out.flush() }
            catch (_: Exception) { dead.add(out) }
        }
        clients.removeAll(dead.toSet())
    }

    // ── HTTP server ───────────────────────────────────────────────────────────

    private fun startHttpServer() {
        executor.execute {
            try {
                serverSocket = ServerSocket(PORT)
                while (!serverSocket!!.isClosed) {
                    val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                    executor.execute { handleClient(socket) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5_000
            val requestLine = socket.getInputStream().bufferedReader().readLine() ?: return
            val path = requestLine.split(" ").getOrElse(1) { "/" }
            val out  = socket.getOutputStream()
            when {
                path.startsWith("/stream") -> {
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace;boundary=--frame\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\n\r\n".toByteArray())
                    out.flush()
                    // Push latest frame immediately so Tesla doesn't stall
                    latestJpeg.get()?.let { broadcastFrame(it) }
                    clients.add(out)
                    // Keep socket alive — broadcastFrame() writes to this out
                    // until it fails (client disconnects)
                    while (running.get() && !socket.isClosed) Thread.sleep(500)
                }
                else -> {
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${htmlPage.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(htmlPage)
                    out.flush()
                    socket.close()
                }
            }
        } catch (_: Exception) {
            clients.removeIf { it === socket.runCatching { getOutputStream() }.getOrNull() }
            socket.runCatching { close() }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun acquireMulticastLock() {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("aiotv_mdns").apply { acquire() }
    }

    override fun onDestroy() {
        running.set(false)
        isRunning = false
        mdnsResponder.stop()
        multicastLock?.runCatching { release() }
        clients.forEach { it.runCatching { close() } }
        clients.clear()
        serverSocket?.runCatching { close() }
        mainHandler.post {
            exoPlayer?.release()
            exoPlayer = null
            imageReader?.close()
            imageReader = null
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildNotification(title: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Casting to Tesla")
            .setContentText(title.ifEmpty { "http://$MDNS_HOST.local:$PORT" })
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Tesla Mirror", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }
}
