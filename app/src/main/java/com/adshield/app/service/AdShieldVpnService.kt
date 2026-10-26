package com.adshield.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adshield.app.AdShieldApp
import com.adshield.app.MainActivity
import com.adshield.app.R
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class AdShieldVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val upstreamDnsIps = listOf("1.1.1.1", "1.0.0.1", "9.9.9.9", "8.8.8.8")
    private var primaryUpstreamDns = "1.1.1.1"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AdShieldVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        if (!isRunning.get()) {
            startForeground(NOTIFICATION_ID, buildNotification("AdShield is actively protecting your device"))
            startVpn()
        }

        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) return

        serviceScope.launch {
            try {
                val blocklistEngine = AdShieldApp.instance.blocklistEngine
                blocklistEngine.initialize()

                val builder = Builder()
                    .setSession("AdShield System Protection")
                    .addAddress("10.1.10.1", 32)
                    .addDnsServer("10.1.10.1")

                // Route common DNS servers to intercept all queries
                builder.addRoute("10.1.10.1", 32)
                for (ip in upstreamDnsIps) {
                    builder.addRoute(ip, 32)
                }

                // Exclude AdShield itself so our upstream socket connects directly
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not disallow self package from VPN", e)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VPN interface")
                    stopSelf()
                    return@launch
                }

                isRunning.set(true)
                AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_VPN_ENABLED, true).apply()
                sendBroadcast(Intent(ACTION_VPN_STATE_CHANGED).putExtra("running", true))

                Log.i(TAG, "AdShield VPN successfully started and listening for DNS queries")
                processPackets()
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN service loop", e)
                stopVpn()
            }
        }
    }

    private fun processPackets() {
        val descriptor = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(descriptor)
        val outputStream = FileOutputStream(descriptor)
        val packetBuffer = ByteArray(4096)

        // Socket for forwarding allowed DNS queries to upstream
        val upstreamSocket = DatagramSocket()
        protect(upstreamSocket) // Ensures socket bypasses VPN
        upstreamSocket.soTimeout = 2000

        var lastNotificationUpdate = System.currentTimeMillis()

        while (isRunning.get()) {
            try {
                val bytesRead = inputStream.read(packetBuffer)
                if (bytesRead <= 0) continue

                val query = DnsPacketParser.parseDnsQuery(packetBuffer, bytesRead)
                if (query != null) {
                    val isBlocked = AdShieldApp.instance.blocklistEngine.isDomainBlocked(query.domain)

                    if (isBlocked) {
                        // Immediately synthesize a 0.0.0.0 / NXDOMAIN response and return into TUN
                        val blockedPacket = DnsPacketParser.createBlockedResponse(
                            packetBuffer,
                            query,
                            returnSinkholeIp = true
                        )
                        outputStream.write(blockedPacket)
                        outputStream.flush()

                        AdShieldApp.instance.incrementBlockedAds()

                        // Update notification periodically
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationUpdate > 5000) {
                            lastNotificationUpdate = now
                            updateNotification()
                        }
                    } else {
                        // Forward legitimate query to upstream DNS
                        val upstreamIp = InetAddress.getByName(primaryUpstreamDns)
                        val dnsPayload = ByteArray(query.dnsPayloadLen)
                        System.arraycopy(packetBuffer, query.dnsPayloadOffset, dnsPayload, 0, query.dnsPayloadLen)

                        val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamIp, DnsPacketParser.DNS_PORT)
                        upstreamSocket.send(outPacket)

                        val respBuffer = ByteArray(2048)
                        val inPacket = DatagramPacket(respBuffer, respBuffer.size)
                        upstreamSocket.receive(inPacket)

                        // Reconstruct IPv4 UDP response packet
                        val responsePacket = buildUdpResponsePacket(query, inPacket.data, inPacket.length)
                        outputStream.write(responsePacket)
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }

        try {
            upstreamSocket.close()
        } catch (ignored: Exception) {}
    }

    private fun buildUdpResponsePacket(query: DnsPacketParser.DnsQuery, dnsPayload: ByteArray, dnsLen: Int): ByteArray {
        val ipLen = query.ipHeaderLen
        val totalLen = ipLen + 8 + dnsLen
        val packet = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // IP Header
        buf.put(0, 0x45.toByte()) // IPv4, IHL = 5 (20 bytes)
        buf.put(1, 0.toByte())
        buf.putShort(2, totalLen.toShort())
        buf.putShort(4, 0)
        buf.put(6, 0x40.toByte()) // Don't Fragment
        buf.put(7, 0)
        buf.put(8, 64.toByte()) // TTL
        buf.put(9, DnsPacketParser.UDP_PROTOCOL)

        // Swap IPs
        buf.position(12)
        buf.put(query.dstIp) // Src IP
        buf.put(query.srcIp) // Dst IP

        // IP Checksum
        buf.putShort(10, 0)
        val ipChecksum = computeChecksum(packet, 0, ipLen)
        buf.putShort(10, ipChecksum.toShort())

        // UDP Header
        buf.position(ipLen)
        buf.putShort(query.dstPort.toShort()) // Src Port (53)
        buf.putShort(query.srcPort.toShort()) // Dst Port
        buf.putShort((8 + dnsLen).toShort())
        buf.putShort(0) // UDP Checksum optional in IPv4

        // DNS Payload
        buf.position(ipLen + 8)
        buf.put(dnsPayload, 0, dnsLen)

        return packet
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val high = data[i].toInt() and 0xFF
            val low = if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun updateNotification() {
        val count = AdShieldApp.instance.getBlockedAdsCount()
        val text = "$count ads and trackers blocked"
        val notification = buildNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AdShieldVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AdShieldApp.CHANNEL_ID_VPN)
            .setContentTitle("AdShield Protection Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_power_off, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopVpn() {
        isRunning.set(false)
        serviceJob.cancel()

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_VPN_ENABLED, false).apply()
        sendBroadcast(Intent(ACTION_VPN_STATE_CHANGED).putExtra("running", false))

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "AdShield VPN service stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.adshield.app.ACTION_STOP"
        const val ACTION_VPN_STATE_CHANGED = "com.adshield.app.ACTION_VPN_STATE_CHANGED"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AdShieldVpnService"
    }
}
