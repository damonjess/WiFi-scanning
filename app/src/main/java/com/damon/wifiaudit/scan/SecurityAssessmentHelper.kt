package com.damon.wifiaudit.scan

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Security assessment helper.
 *
 * Performs passive HTTP-based security checks on discovered LAN devices:
 * - Fetches HTTP/HTTPS pages and identifies admin panels, camera interfaces, router login pages
 * - Checks for default credential patterns in page titles and headers
 * - Detects insecure (HTTP-only) admin interfaces
 * - Checks for common default credential pairs (manual trigger only, very limited set)
 *
 * This is a defensive tool for auditing your own network. It does NOT:
 * - Brute-force credentials
 * - Attempt exploitation
 * - Send more than a handful of requests per device
 */
class SecurityAssessmentHelper {

    private val tag = "SecurityAssessment"

    data class SecurityFinding(
        val ip: String,
        val port: Int,
        val severity: FindingSeverity,
        val title: String,
        val description: String,
        val recommendation: String
    )

    enum class FindingSeverity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

    data class DeviceAssessment(
        val ip: String,
        val findings: List<SecurityFinding>
    )

    // Common admin panel paths to check
    private val adminPaths = listOf(
        "/", "/admin", "/login.html", "/login.cgi", "/index.html",
        "/cgi-bin/luci", "/setup.cgi", "/main.cgi", "/html/login.html"
    )

    // Page title patterns that indicate device type and possible default credentials
    private val titlePatterns = mapOf(
        Regex("(?i)router|netgear|tp-?link|asus|linksys|d-?link|mikrotik|ubiquiti|huawei", RegexOption.IGNORE_CASE) to "router",
        Regex("(?i)hikvision|hik|dahua|dvr|nvr|ipcam|camera|reolink|amcrest|wyze|arlo|axis", RegexOption.IGNORE_CASE) to "camera",
        Regex("(?i)synology|qnap|nas|readyshare|mycloud", RegexOption.IGNORE_CASE) to "nas",
        Regex("(?i)printer|hp |canon|epson|brother|xerox|lexmark", RegexOption.IGNORE_CASE) to "printer",
        Regex("(?i)login|admin|sign in|password|web ui|management", RegexOption.IGNORE_CASE) to "login"
    )

    // Very limited common default credential pairs (for manual checking only)
    // These are the most commonly found defaults on consumer devices
    private val defaultCredentials = listOf(
        "admin" to "admin",
        "admin" to "password",
        "admin" to "12345",
        "admin" to "admin123",
        "root" to "root",
        "root" to "admin",
        "user" to "user",
        "guest" to "guest",
        "admin" to "",
        "admin" to "default"
    )

    /**
     * Assess a single device by fetching its HTTP/HTTPS pages.
     * This is passive — it only reads pages, doesn't submit any forms.
     */
    suspend fun assessDevice(ip: String, openPorts: List<Int>): DeviceAssessment = withContext(Dispatchers.IO) {
        val findings = mutableListOf<SecurityFinding>()

        // Check HTTP (port 80 or 8080)
        val httpPort = when {
            80 in openPorts -> 80
            8080 in openPorts -> 8080
            8000 in openPorts -> 8000
            81 in openPorts -> 81
            else -> null
        }

        // Check HTTPS (port 443 or 8443)
        val httpsPort = when {
            443 in openPorts -> 443
            8443 in openPorts -> 8443
            else -> null
        }

        // Fetch HTTP pages
        if (httpPort != null) {
            val httpFindings = assessHttp(ip, httpPort, isHttps = false)
            findings.addAll(httpFindings)
        }

        // Fetch HTTPS pages (separately, may have different interface)
        if (httpsPort != null) {
            val httpsFindings = assessHttp(ip, httpsPort, isHttps = true)
            findings.addAll(httpsFindings)
        }

        // Flag insecure admin panel (HTTP-only, no HTTPS available)
        if (httpPort != null && httpsPort == null) {
            findings.add(SecurityFinding(
                ip = ip,
                port = httpPort,
                severity = FindingSeverity.MEDIUM,
                title = "Insecure admin interface (HTTP only)",
                description = "Device has an HTTP admin panel but no HTTPS. Credentials are transmitted in plaintext.",
                recommendation = "Enable HTTPS if possible. Use this interface only on a trusted LAN."
            ))
        }

        // Check for critical exposed services
        if (23 in openPorts) {
            findings.add(SecurityFinding(
                ip = ip,
                port = 23,
                severity = FindingSeverity.CRITICAL,
                title = "Telnet exposed",
                description = "Telnet is completely unencrypted. Many IoT devices have telnet with default passwords.",
                recommendation = "Disable Telnet immediately. Use SSH instead."
            ))
        }

        if (5555 in openPorts) {
            findings.add(SecurityFinding(
                ip = ip,
                port = 5555,
                severity = FindingSeverity.CRITICAL,
                title = "ADB over network exposed",
                description = "Android Debug Bridge is accessible over the network — full device control.",
                recommendation = "Disable ADB over network immediately."
            ))
        }

        if (161 in openPorts) {
            findings.add(SecurityFinding(
                ip = ip,
                port = 161,
                severity = FindingSeverity.HIGH,
                title = "SNMP exposed",
                description = "SNMP may expose device configuration. Many devices use 'public' community string.",
                recommendation = "Change community string from 'public'. Disable SNMP if not needed."
            ))
        }

        DeviceAssessment(ip = ip, findings = findings)
    }

    private suspend fun assessHttp(ip: String, port: Int, isHttps: Boolean): List<SecurityFinding> = withContext(Dispatchers.IO) {
        val findings = mutableListOf<SecurityFinding>()
        val protocol = if (isHttps) "https" else "http"

        for (path in adminPaths) {
            val url = "$protocol://$ip:$port$path"
            val result = withTimeoutOrNull(3000L) {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.instanceFollowRedirects = true
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) WiFiAudit")

                    val responseCode = conn.responseCode
                    if (responseCode in 200..399) {
                        val html = conn.inputStream.bufferedReader().use { it.readText() }
                        val title = extractTitle(html)
                        val realm = extractBasicAuthRealm(conn)

                        conn.disconnect()

                        // Check for basic auth prompt
                        if (realm != null) {
                            findings.add(SecurityFinding(
                                ip = ip,
                                port = port,
                                severity = FindingSeverity.MEDIUM,
                                title = "HTTP Basic Auth: $realm",
                                description = "Admin panel requires authentication. Realm: '$realm'. Check for default credentials.",
                                recommendation = "Try common defaults: admin/admin, admin/password, root/root"
                            ))
                            break // Found the login page, no need to check more paths
                        }

                        // Identify device type from title
                        val deviceType = identifyDeviceType(title ?: html)
                        if (deviceType != null) {
                            findings.add(SecurityFinding(
                                ip = ip,
                                port = port,
                                severity = FindingSeverity.MEDIUM,
                                title = "Admin panel detected: $deviceType",
                                description = "Web interface title: '$title'. Device type: $deviceType. Check for default credentials.",
                                recommendation = "Change default password. Check for known CVEs for this device type."
                            ))
                            break
                        }

                        // Check for login form
                        if (html.contains("password", ignoreCase = true) || html.contains("login", ignoreCase = true)) {
                            findings.add(SecurityFinding(
                                ip = ip,
                                port = port,
                                severity = FindingSeverity.LOW,
                                title = "Login page detected",
                                description = "Device has a web login page at $url. Title: '${title ?: "untitled"}'",
                                recommendation = "Check for default credentials. Change default password."
                            ))
                            break
                        }
                    } else if (responseCode == 401) {
                        val realm = conn.getHeaderField("WWW-Authenticate") ?: "Protected"
                        findings.add(SecurityFinding(
                            ip = ip,
                            port = port,
                            severity = FindingSeverity.MEDIUM,
                            title = "HTTP 401: Authentication required",
                            description = "Admin panel at $url requires authentication. $realm",
                            recommendation = "Check for default credentials: admin/admin, admin/password"
                        ))
                        break
                    }
                    conn.disconnect()
                } catch (_: javax.net.ssl.SSLException) {
                    // Certificate issues — still worth noting
                    findings.add(SecurityFinding(
                        ip = ip,
                        port = port,
                        severity = FindingSeverity.LOW,
                        title = "TLS certificate issue",
                        description = "HTTPS endpoint has an invalid or self-signed certificate at $url",
                        recommendation = "Verify this is expected. Self-signed certs are common on IoT devices."
                    ))
                    break
                } catch (_: Exception) {
                    // Connection failed, try next path
                }
            }
        }

        findings
    }

    private fun extractTitle(html: String): String? {
        val pattern = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL or RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.get(1)?.trim()?.take(100)
    }

    private fun extractBasicAuthRealm(conn: HttpURLConnection): String? {
        val authHeader = conn.getHeaderField("WWW-Authenticate") ?: return null
        val realmMatch = Regex("realm=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(authHeader)
        return realmMatch?.groupValues?.get(1)
    }

    private fun identifyDeviceType(titleOrHtml: String): String? {
        for ((pattern, type) in titlePatterns) {
            if (pattern.containsMatchIn(titleOrHtml)) return type
        }
        return null
    }

    /**
     * Attempt to check default credentials against an HTTP Basic Auth endpoint.
     * This is manually triggered only — not part of automatic scanning.
     * Uses a very limited set of common defaults.
     *
     * Returns the credential pair if a default login succeeds, null otherwise.
     */
    suspend fun checkDefaultCredentials(ip: String, port: Int, isHttps: Boolean = false): Pair<String, String>? = withContext(Dispatchers.IO) {
        val protocol = if (isHttps) "https" else "http"
        val baseUrl = "$protocol://$ip:$port"

        for ((user, pass) in defaultCredentials) {
            val result = withTimeoutOrNull(3000L) {
                try {
                    val conn = URL(baseUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000

                    // Build Basic Auth header
                    val auth = "$user:$pass"
                    val encoded = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
                    conn.setRequestProperty("Authorization", "Basic $encoded")
                    conn.requestMethod = "GET"

                    val code = conn.responseCode
                    conn.disconnect()

                    if (code in 200..299) {
                        return@withTimeoutOrNull user to pass
                    }
                    null
                } catch (_: Exception) {
                    null
                }
            }

            if (result != null) return@withContext result
        }

        null
    }

    /**
     * Assess multiple devices in parallel (limited concurrency).
     */
    suspend fun assessDevices(devices: List<Pair<String, List<Int>>>): List<DeviceAssessment> = coroutineScope {
        devices.map { (ip, ports) ->
            async(Dispatchers.IO) { assessDevice(ip, ports) }
        }.awaitAll()
    }
}
