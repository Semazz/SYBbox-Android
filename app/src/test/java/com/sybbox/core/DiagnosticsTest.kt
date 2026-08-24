package com.sybbox.core

import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {

    // Verbatim from a device log, trimmed in the middle. One of these is roughly 1.5 KB,
    // and a few of them used to push everything else out of a 300-entry log buffer.
    private val realityFallback =
        "connection: open connection to 194.221.250.50:443 using outbound/vless[proxy]: " +
            "x509: certificate is valid for *.google.com, *.appengine.google.com, *.gstatic.com, " +
            "*.youtube.com, youtube.com, google.com, android.com, *.android.com, goo.gl, yt.be, " +
            "*.yt.be, android.clients.google.com, *.aistudio.google.com, not api-maps.yandex.ru"

    @Test
    fun `a reality fallback is explained in terms of the subscription`() {
        val hint = Diagnostics.explain(realityFallback)
        assertNotNull(hint)
        assertTrue(hint!!.contains("api-maps.yandex.ru"))
        assertTrue(hint.contains("REALITY"))
        // The point is to say what to do, not to restate the certificate.
        assertFalse(hint.contains("*.gstatic.com"))
    }

    @Test
    fun `a rejected public key is distinguished from a rejected sni`() {
        val hint = Diagnostics.explain("reality verification failed")
        assertNotNull(hint)
        assertTrue(hint!!.contains("public key"))
    }

    @Test
    fun `a bootstrap failure names the host that could not be resolved`() {
        val hint = Diagnostics.explain(
            "connection: open connection to 1.1.1.1:53 using outbound/vless[proxy]: " +
                "lookup node.subsyb.online: context canceled",
        )
        assertNotNull(hint)
        assertTrue(hint!!.contains("node.subsyb.online"))
    }

    @Test
    fun `the config error the core refused is explained`() {
        val hint = Diagnostics.explain(
            "start service: start dns/udp[dns-direct]: detour to an empty direct outbound makes no sense",
        )
        assertNotNull(hint)
        assertTrue(hint!!.contains("resolver"))
    }

    @Test
    fun `ordinary messages are left alone`() {
        assertNull(Diagnostics.explain("inbound/tun[tun-in]: inbound connection from 172.19.0.1:41308"))
        assertNull(Diagnostics.explain("router: sniffed protocol: tls, domain: www.google.com"))
    }

    @Test
    fun `long messages are condensed but stay identifiable`() {
        // The real message carries the full SAN list of Google's certificate, ~1.5 KB.
        val full = realityFallback.replace(
            "*.gstatic.com,",
            "*.gstatic.com, " + (1..40).joinToString(", ") { "*.google.co.x$it" } + ",",
        )
        assertTrue(full.length > Diagnostics.MAX_MESSAGE)
        val condensed = Diagnostics.condense(full)
        assertTrue(condensed.length < full.length)
        assertTrue(condensed.length <= Diagnostics.MAX_MESSAGE + 32)
        assertTrue(condensed.startsWith("connection: open connection to 194.221.250.50:443"))
    }

    @Test
    fun `short messages are untouched`() {
        val short = "router: sniffed protocol: tls"
        assertEquals(short, Diagnostics.condense(short))
    }
}
