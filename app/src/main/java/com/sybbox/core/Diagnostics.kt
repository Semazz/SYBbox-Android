package com.sybbox.core

object Diagnostics {

    const val MAX_MESSAGE = 240

    private val COLOURS = Regex("""\u001B\[[0-9;]*m""")

    private val LEVEL_PREFIX = Regex("""^(?:PANIC|FATAL|ERROR|WARN|INFO|DEBUG|TRACE)\[\d+]\s*""")

    private val CONNECTION_ID = Regex("""^\[\d+ [^\]]{0,16}]\s*""")

    private val NOISE = Regex("""inbound (?:packet )?connection from """)

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

    fun tidy(message: String): String {
        var text = message
        if (text.indexOf('\u001B') >= 0) text = COLOURS.replace(text, "")
        text = LEVEL_PREFIX.replaceFirst(text, "")
        text = CONNECTION_ID.replaceFirst(text, "")
        return text.trim()
    }

    fun isNoise(message: String): Boolean = NOISE.containsMatchIn(message)

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
