package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "ResilientDns"

/**
 * Multi-tier OkHttp [Dns] resolver for sideloaded environments with broken system DNS:
 * 1. System DNS ([Dns.SYSTEM]), prioritizing IPv4 addresses.
 * 2. DNS-over-HTTPS (Cloudflare 1.1.1.1 / 1.0.0.1 and Google 8.8.8.8 / 8.8.4.4 by literal IP).
 * 3. Direct UDP port-53 RFC 1035 A-record queries to 8.8.8.8 / 1.1.1.1 / 8.8.4.4.
 */
class ResilientDns(private val appContext: Context? = null) : Dns {

    private data class CachedAddresses(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedAddresses>()

    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        cache[hostname]?.let { cached ->
            if (cached.expiresAtMs > now && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
        }

        // Tier 1: System DNS
        val systemResult = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull()
        if (!systemResult.isNullOrEmpty()) {
            val sorted = systemResult.sortedByDescending { it is Inet4Address }
            cache[hostname] = CachedAddresses(sorted, now + CACHE_TTL_MS)
            return sorted
        }

        Log.w(TAG, "System DNS failed for $hostname; trying DoH and UDP DNS fallbacks")

        // Tier 2: DNS-over-HTTPS via literal IPv4 resolvers
        val dohEndpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://1.0.0.1/dns-query?name=$hostname&type=A",
            "https://8.8.8.8/resolve?name=$hostname&type=A",
            "https://8.8.4.4/resolve?name=$hostname&type=A"
        )
        for (url in dohEndpoints) {
            val resolved = queryDohJson(hostname, url)
            if (resolved.isNotEmpty()) {
                cache[hostname] = CachedAddresses(resolved, now + CACHE_TTL_MS)
                return resolved
            }
        }

        // Tier 3: Raw UDP DNS port 53 via literal IPv4 resolvers
        val udpResolvers = listOf("8.8.8.8", "1.1.1.1", "8.8.4.4")
        for (resolverIp in udpResolvers) {
            val resolved = queryUdpDnsA(hostname, resolverIp)
            if (resolved.isNotEmpty()) {
                cache[hostname] = CachedAddresses(resolved, now + CACHE_TTL_MS)
                return resolved
            }
        }

        val networkNote = diagnoseNetworkState()
        throw UnknownHostException(
            "Unable to resolve \"$hostname\" ($networkNote). Check device internet, VPN, or Private DNS settings."
        )
    }

    private fun diagnoseNetworkState(): String {
        val ctx = appContext ?: return "System DNS, DoH, and UDP DNS unreachable"
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "No ConnectivityManager available"
            val network = cm.activeNetwork ?: return "No active network on device"
            val caps = cm.getNetworkCapabilities(network)
                ?: return "No network capabilities on active interface"
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            when {
                !hasInternet -> "Active network lacks internet capability"
                !isValidated -> "Network connected without validated internet access"
                else -> "Network online, but DNS (System, DoH, UDP:53) is blocked"
            }
        } catch (_: Exception) {
            "System DNS, DoH, and UDP DNS unreachable"
        }
    }

    private fun queryDohJson(hostname: String, url: String): List<InetAddress> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()

            dohClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseDohAnswerIps(hostname, body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoH query failed for $url: ${e.message}")
            emptyList()
        }
    }

    private fun queryUdpDnsA(hostname: String, resolverIp: String): List<InetAddress> {
        return try {
            val packetBytes = buildDnsQueryPacket(hostname)
            val resolverAddr = InetAddress.getByName(resolverIp)
            DatagramSocket().use { socket ->
                socket.soTimeout = 3000
                socket.send(DatagramPacket(packetBytes, packetBytes.size, resolverAddr, 53))
                val buffer = ByteArray(512)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(responsePacket)
                parseDnsAResponse(hostname, buffer, responsePacket.length)
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP DNS query failed for $resolverIp: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        internal fun parseDohAnswerIps(hostname: String, jsonBody: String): List<InetAddress> {
            return try {
                val root = JSONObject(jsonBody)
                val answers = root.optJSONArray("Answer") ?: return emptyList()
                val addresses = mutableListOf<InetAddress>()
                for (i in 0 until answers.length()) {
                    val item = answers.optJSONObject(i) ?: continue
                    if (item.optInt("type") == 1) {
                        val ipText = item.optString("data").trim()
                        if (ipText.isNotEmpty()) {
                            runCatching { InetAddress.getByName(ipText) }
                                .getOrNull()
                                ?.let { raw ->
                                    addresses.add(InetAddress.getByAddress(hostname, raw.address))
                                }
                        }
                    }
                }
                addresses
            } catch (_: Exception) {
                emptyList()
            }
        }

        internal fun buildDnsQueryPacket(hostname: String, transactionId: Int = 0x4A7C): ByteArray {
            val out = ByteArrayOutputStream()
            // Header: ID, Flags (RD=1 -> 0x0100), QDCOUNT=1, ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
            out.write((transactionId ushr 8) and 0xFF)
            out.write(transactionId and 0xFF)
            out.write(0x01)
            out.write(0x00)
            out.write(0x00); out.write(0x01)
            out.write(0x00); out.write(0x00)
            out.write(0x00); out.write(0x00)
            out.write(0x00); out.write(0x00)

            // Question: labels + 0x00 + QTYPE=1 (A) + QCLASS=1 (IN)
            hostname.trimEnd('.').split('.').forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size and 0x3F)
                out.write(bytes)
            }
            out.write(0x00)
            out.write(0x00); out.write(0x01)
            out.write(0x00); out.write(0x01)
            return out.toByteArray()
        }

        internal fun parseDnsAResponse(
            hostname: String,
            data: ByteArray,
            length: Int
        ): List<InetAddress> {
            if (length < 12) return emptyList()
            val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            val anCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            var pos = 12

            // Skip questions
            repeat(qdCount) {
                pos = skipDnsName(data, pos, length)
                if (pos < 0 || pos + 4 > length) return emptyList()
                pos += 4 // QTYPE + QCLASS
            }

            val results = mutableListOf<InetAddress>()
            repeat(anCount) {
                if (pos >= length) return results
                pos = skipDnsName(data, pos, length)
                if (pos < 0 || pos + 10 > length) return results
                val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                val rdLength = ((data[pos + 8].toInt() and 0xFF) shl 8) or (data[pos + 9].toInt() and 0xFF)
                pos += 10
                if (pos + rdLength > length) return results
                if (type == 1 && rdLength == 4) {
                    val ipv4 = data.copyOfRange(pos, pos + 4)
                    runCatching { InetAddress.getByAddress(hostname, ipv4) }
                        .getOrNull()
                        ?.let { results.add(it) }
                }
                pos += rdLength
            }
            return results
        }

        private fun skipDnsName(data: ByteArray, startPos: Int, length: Int): Int {
            var pos = startPos
            while (pos < length) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) return pos + 1
                if ((len and 0xC0) == 0xC0) {
                    return if (pos + 2 <= length) pos + 2 else -1
                }
                pos += 1 + len
            }
            return -1
        }
    }
}
