package com.adshield.app.service

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles raw IPv4 / UDP / DNS packet parsing and synthesis.
 * Enables zero-latency local DNS sinkholing inside VpnService.
 */
object DnsPacketParser {

    const val UDP_PROTOCOL: Byte = 17
    const val DNS_PORT = 53

    data class DnsQuery(
        val domain: String,
        val queryType: Int,
        val transactionId: Short,
        val ipHeaderOffset: Int,
        val ipHeaderLen: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsPayloadOffset: Int,
        val dnsPayloadLen: Int
    )

    /**
     * Parses an IPv4 UDP DNS query packet.
     * Returns DnsQuery if packet is a valid DNS query on port 53, or null otherwise.
     */
    fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null // 20 byte IP + 8 byte UDP minimum

        val buffer = ByteBuffer.wrap(packet, 0, length).order(ByteOrder.BIG_ENDIAN)

        // Verify IPv4
        val verAndIhl = buffer.get(0).toInt()
        val version = (verAndIhl shr 4) and 0x0F
        if (version != 4) return null

        val ihl = (verAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        // Protocol check (UDP = 17)
        val protocol = buffer.get(9)
        if (protocol != UDP_PROTOCOL) return null

        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)
        System.arraycopy(packet, 12, srcIp, 0, 4)
        System.arraycopy(packet, 16, dstIp, 0, 4)

        // UDP Header
        val srcPort = buffer.getShort(ihl).toInt() and 0xFFFF
        val dstPort = buffer.getShort(ihl + 2).toInt() and 0xFFFF

        if (dstPort != DNS_PORT) return null

        val udpLen = buffer.getShort(ihl + 4).toInt() and 0xFFFF
        val dnsOffset = ihl + 8
        val dnsLen = udpLen - 8
        if (dnsLen < 12 || length < dnsOffset + dnsLen) return null

        // DNS Header
        val transactionId = buffer.getShort(dnsOffset)
        val flags = buffer.getShort(dnsOffset + 2).toInt() and 0xFFFF
        val isResponse = (flags and 0x8000) != 0
        if (isResponse) return null // We only inspect queries

        val qdCount = buffer.getShort(dnsOffset + 4).toInt() and 0xFFFF
        if (qdCount < 1) return null

        // Parse Question Name
        var cursor = dnsOffset + 12
        val domainBuilder = StringBuilder()

        while (cursor < dnsOffset + dnsLen) {
            val labelLen = buffer.get(cursor).toInt() and 0xFF
            cursor++

            if (labelLen == 0) break
            if ((labelLen and 0xC0) == 0xC0) {
                // Compression pointer (rare in queries, skip)
                cursor++
                break
            }

            if (cursor + labelLen > dnsOffset + dnsLen) return null

            if (domainBuilder.isNotEmpty()) {
                domainBuilder.append('.')
            }

            val labelBytes = ByteArray(labelLen)
            System.arraycopy(packet, cursor, labelBytes, 0, labelLen)
            domainBuilder.append(String(labelBytes, Charsets.US_ASCII))
            cursor += labelLen
        }

        if (cursor + 4 > dnsOffset + dnsLen) return null
        val qType = buffer.getShort(cursor).toInt() and 0xFFFF

        return DnsQuery(
            domain = domainBuilder.toString(),
            queryType = qType,
            transactionId = transactionId,
            ipHeaderOffset = 0,
            ipHeaderLen = ihl,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayloadOffset = dnsOffset,
            dnsPayloadLen = dnsLen
        )
    }

    /**
     * Synthesizes a DNS NXDOMAIN (RCODE = 3) or 0.0.0.0 response packet
     * to immediately drop the ad request without connecting to the network.
     */
    fun createBlockedResponse(
        originalPacket: ByteArray,
        query: DnsQuery,
        returnSinkholeIp: Boolean = true
    ): ByteArray {
        val ipHeaderLen = query.ipHeaderLen
        val udpHeaderLen = 8

        // Calculate DNS response length
        // Original question length = query.dnsPayloadLen - 12 (DNS header is 12 bytes)
        val questionLen = query.dnsPayloadLen - 12
        val answerRecordLen = if (returnSinkholeIp && query.queryType == 1) 16 else 0 // Type A 0.0.0.0 record
        val dnsResponseLen = 12 + questionLen + answerRecordLen

        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponseLen
        val response = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN)

        // 1. IP Header
        buf.put(originalPacket, 0, ipHeaderLen) // Copy base IP header
        buf.putShort(2, totalLen.toShort()) // Total length
        buf.put(6, 0x40.toByte()) // Flags: Don't Fragment
        buf.put(7, 0x00.toByte())
        buf.put(8, 64.toByte()) // TTL = 64
        buf.put(9, UDP_PROTOCOL)

        // Swap IP addresses (reply comes from dstIp, goes to srcIp)
        buf.position(12)
        buf.put(query.dstIp)
        buf.put(query.srcIp)

        // Recalculate IP Checksum
        buf.putShort(10, 0)
        val ipChecksum = computeIpChecksum(response, 0, ipHeaderLen)
        buf.putShort(10, ipChecksum.toShort())

        // 2. UDP Header
        buf.position(ipHeaderLen)
        buf.putShort(query.dstPort.toShort()) // src port
        buf.putShort(query.srcPort.toShort()) // dst port
        buf.putShort((udpHeaderLen + dnsResponseLen).toShort()) // UDP length
        buf.putShort(0) // Checksum = 0 (valid for IPv4 UDP)

        // 3. DNS Header
        val dnsStart = ipHeaderLen + udpHeaderLen
        buf.position(dnsStart)
        buf.putShort(query.transactionId)

        val flags = if (returnSinkholeIp && query.queryType == 1) {
            // Standard Response, Authoritative, Recursion Available, No Error (0x8180)
            0x8180.toShort()
        } else {
            // Standard Response, Recursion Available, Name Error NXDOMAIN (0x8183)
            0x8183.toShort()
        }
        buf.putShort(flags)
        buf.putShort(1.toShort()) // QDCOUNT = 1
        buf.putShort(if (returnSinkholeIp && query.queryType == 1) 1.toShort() else 0.toShort()) // ANCOUNT
        buf.putShort(0.toShort()) // NSCOUNT = 0
        buf.putShort(0.toShort()) // ARCOUNT = 0

        // Copy original question
        buf.put(originalPacket, query.dnsPayloadOffset + 12, questionLen)

        // Append 0.0.0.0 Answer Record for Type A
        if (returnSinkholeIp && query.queryType == 1) {
            // Pointer to question name at offset 12 in DNS payload (0xC00C)
            buf.putShort(0xC00C.toShort())
            buf.putShort(1.toShort()) // TYPE = A
            buf.putShort(1.toShort()) // CLASS = IN
            buf.putInt(300) // TTL = 300 seconds
            buf.putShort(4.toShort()) // RDLENGTH = 4 bytes
            buf.put(0.toByte()) // 0.0.0.0
            buf.put(0.toByte())
            buf.put(0.toByte())
            buf.put(0.toByte())
        }

        return response
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
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
}
