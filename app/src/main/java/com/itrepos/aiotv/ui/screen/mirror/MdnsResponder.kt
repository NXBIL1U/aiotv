package com.itrepos.aiotv.ui.screen.mirror

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal mDNS responder — listens on 224.0.0.251:5353 (the mDNS multicast group)
 * and replies to A-record queries for [hostname] with [hostname].local → current IP.
 *
 * Tesla's Chromium browser resolves .local hostnames via mDNS, so after this runs,
 * navigating to http://aiotv.local:8888 always reaches the phone regardless of IP.
 */
class MdnsResponder(
    private val hostname: String = "aiotv",   // advertised as <hostname>.local
    private val port: Int = MirrorService.PORT,
) {
    private val running = AtomicBoolean(false)
    private var socket: MulticastSocket? = null

    fun start() {
        if (running.getAndSet(true)) return
        Thread({
            try {
                val group = InetAddress.getByName(MDNS_GROUP)
                socket = MulticastSocket(MDNS_PORT).apply {
                    timeToLive = 255
                    joinGroup(group)
                }
                val buf = DatagramPacket(ByteArray(512), 512)
                while (running.get()) {
                    socket?.receive(buf) ?: break
                    val data = buf.data.copyOf(buf.length)
                    if (!isQueryForUs(data)) continue
                    val ip = localIp() ?: continue
                    val response = buildResponse(ip)
                    val out = DatagramPacket(response, response.size, group, MDNS_PORT)
                    socket?.send(out)
                }
            } catch (_: Exception) {}
        }, "mdns-responder").apply { isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        socket?.runCatching { leaveGroup(InetAddress.getByName(MDNS_GROUP)); close() }
        socket = null
    }

    // ── DNS packet helpers ────────────────────────────────────────────────────

    private val fqdn = "$hostname.local"

    private fun isQueryForUs(pkt: ByteArray): Boolean {
        if (pkt.size < 12) return false
        val flags = ((pkt[2].toInt() and 0xFF) shl 8) or (pkt[3].toInt() and 0xFF)
        if (flags and 0x8000 != 0) return false   // skip responses
        val qCount = ((pkt[4].toInt() and 0xFF) shl 8) or (pkt[5].toInt() and 0xFF)
        if (qCount == 0) return false
        return try {
            val name = readName(pkt, 12)
            name.equals(fqdn, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    private fun readName(pkt: ByteArray, startOffset: Int): String {
        val sb = StringBuilder()
        var i = startOffset
        while (i < pkt.size) {
            val len = pkt[i].toInt() and 0xFF
            if (len == 0) break
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(pkt, i + 1, len, Charsets.UTF_8))
            i += len + 1
        }
        return sb.toString()
    }

    private fun buildResponse(ip: InetAddress): ByteArray {
        // RFC 6762 mDNS response with a single A record
        val out = mutableListOf<Byte>()
        // Header: ID=0, Flags=0x8400 (QR=1 AA=1), Answers=1, rest=0
        out += listOf(0x00, 0x00, 0x84, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00).map { it.toByte() }
        // Answer: NAME
        fqdn.split(".").forEach { label ->
            out.add(label.length.toByte())
            out += label.toByteArray().toList()
        }
        out.add(0x00) // end of name
        // TYPE A (0x0001), CLASS IN with cache-flush (0x8001), TTL=120s, RDLENGTH=4
        out += listOf(0x00, 0x01, 0x80, 0x01, 0x00, 0x00, 0x00, 0x78, 0x00, 0x04).map { it.toByte() }
        // RDATA: IPv4 address
        out += ip.address.toList()
        return out.toByteArray()
    }

    private fun localIp(): InetAddress? = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.sortedBy { if (it.name.startsWith("ap") || it.name.startsWith("wlan")) 0 else 1 }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { a ->
                !a.isLoopbackAddress && a is java.net.Inet4Address &&
                    (a.hostAddress?.startsWith("192.168.") == true ||
                        a.hostAddress?.startsWith("10.") == true)
            }
    } catch (_: Exception) { null }

    companion object {
        const val MDNS_GROUP = "224.0.0.251"
        const val MDNS_PORT  = 5353
    }
}
