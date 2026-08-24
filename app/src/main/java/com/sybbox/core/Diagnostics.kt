package com.sybbox.core

object Diagnostics {

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

    fun condense(message: String): String {
        if (message.length <= MAX_MESSAGE) return message
        return message.take(MAX_MESSAGE).trimEnd().trimEnd(',') + "… (+${message.length - MAX_MESSAGE} chars)"
    }

    fun explain(message: String): String? {
        for ((pattern, build) in rules) {
            val match = pattern.find(message) ?: continue
            return build(match)
        }
        return null
    }
}
