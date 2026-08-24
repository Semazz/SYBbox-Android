package com.sybbox.core

/**
 * Turns the core's failure messages into something a person can act on.
 *
 * The messages themselves are accurate but not self-explanatory: a REALITY server that
 * refuses a handshake hands the connection to its fallback site, so the client reports a
 * certificate mismatch against a site nobody asked for, listing every name on that
 * certificate. Read literally it looks like a TLS bug in the app; it actually means the
 * subscription's REALITY parameters no longer match the server.
 */
object Diagnostics {

    /** The certificate SAN list alone runs well past a kilobyte and floods the log buffer. */
    const val MAX_MESSAGE = 400

    private val rules: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""x509: certificate is valid for .*?, not ([A-Za-z0-9.\-*]+)""", RegexOption.DOT_MATCHES_ALL) to { m ->
            "The server did not accept the REALITY handshake and fell back to a decoy site, " +
                "so the certificate does not match \"${m.groupValues[1]}\". " +
                "The SNI or public key in this subscription no longer matches this server — " +
                "refresh the subscription, or use another protocol from it."
        },
        Regex("""reality verification failed""") to {
            "The server took the SNI but rejected the REALITY public key. " +
                "This subscription's key is out of date for this server."
        },
        Regex("""lookup ([A-Za-z0-9.\-]+): (?:context canceled|context deadline exceeded|i/o timeout)""") to { m ->
            "Could not resolve \"${m.groupValues[1]}\" before the tunnel was up. " +
                "Check the direct DNS setting."
        },
        Regex("""detour to an empty direct outbound""") to {
            "Invalid generated config: a resolver was detoured through the bare direct outbound."
        },
        Regex("""authentication failed|invalid user|user not found""", RegexOption.IGNORE_CASE) to {
            "The server rejected the credentials. The subscription may have expired."
        },
        Regex("""connection reset by peer""") to {
            "The server closed the connection. It may be refusing repeated failed handshakes."
        },
    )

    /** Shortens a message for display without losing the part that identifies it. */
    fun condense(message: String): String {
        if (message.length <= MAX_MESSAGE) return message
        return message.take(MAX_MESSAGE).trimEnd().trimEnd(',') + "… (+${message.length - MAX_MESSAGE} chars)"
    }

    /** A one-line explanation for a known failure, or null when there is nothing useful to add. */
    fun explain(message: String): String? {
        for ((pattern, build) in rules) {
            val match = pattern.find(message) ?: continue
            return build(match)
        }
        return null
    }
}
