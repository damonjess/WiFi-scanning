package com.damon.wifiaudit.util

object MacOuiExtractor {
    /**
     * Extracts normalized 6-char OUI from any MAC format:
     * "AC:23:3F:A4:B5:C6" → "AC233F"
     * "AC-23-3F-A4-B5-C6" → "AC233F"
     * "AC23.3FA4.B5C6"    → "AC233F"
     * "ac:23:3f:a4:b5:c6" → "AC233F"
     */
    fun extractOui(mac: String): String {
        return mac.uppercase()
            .replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .take(6)
    }
}
