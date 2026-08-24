package com.sybbox.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.sybbox.domain.model.*
import java.io.File
import java.io.FileOutputStream

object ConfigShare {

    fun canShare(profile: ServerProfile): Boolean = generateShareLink(profile).isNotEmpty()

    fun generateShareLink(profile: ServerProfile): String {
        return when (profile.protocol) {
            ProtocolType.VLESS -> generateVlessLink(profile)
            ProtocolType.VMESS -> generateVmessLink(profile)
            ProtocolType.TROJAN -> generateTrojanLink(profile)
            ProtocolType.SHADOWSOCKS -> generateSsLink(profile)
            ProtocolType.HYSTERIA2 -> generateHy2Link(profile)
            ProtocolType.TUIC -> generateTuicLink(profile)
            ProtocolType.WIREGUARD -> generateWgLink(profile)
            else -> ""
        }
    }

    private fun generateVlessLink(p: ServerProfile): String {
        val params = mutableListOf<String>()
        params.add("encryption=${p.encryption}")
        params.add("security=${p.security.name.lowercase()}")
        if (p.flow.isNotEmpty()) params.add("flow=${p.flow}")
        when (p.transport) {
            TransportType.WS -> {
                params.add("type=ws")
                if (p.wsHost.isNotEmpty()) params.add("host=${Uri.encode(p.wsHost)}")
                if (p.wsPath.isNotEmpty()) params.add("path=${Uri.encode(p.wsPath)}")
                if (p.maxEarlyData > 0) {
                    params.add("ed=${p.maxEarlyData}")
                    params.add("eh=Sec-WebSocket-Protocol")
                }
            }
            TransportType.HTTP -> {
                params.add("type=http")
                if (p.wsHost.isNotEmpty() || p.h2Host.isNotEmpty()) params.add("host=${Uri.encode(p.wsHost.ifEmpty { p.h2Host })}")
                if (p.wsPath.isNotEmpty() || p.h2Path.isNotEmpty()) params.add("path=${Uri.encode(p.wsPath.ifEmpty { p.h2Path })}")
            }
            TransportType.GRPC -> {
                params.add("type=grpc")
                params.add("serviceName=${Uri.encode(p.grpcServiceName)}")
            }
            TransportType.HTTPUPGRADE -> {
                params.add("type=httpupgrade")
                if (p.wsHost.isNotEmpty()) params.add("host=${Uri.encode(p.wsHost)}")
                if (p.wsPath.isNotEmpty()) params.add("path=${Uri.encode(p.wsPath)}")
            }
            TransportType.XHTTP -> {
                params.add("type=xhttp")
                if (p.wsHost.isNotEmpty() || p.h2Host.isNotEmpty()) params.add("host=${Uri.encode(p.wsHost.ifEmpty { p.h2Host })}")
                if (p.wsPath.isNotEmpty() || p.h2Path.isNotEmpty()) params.add("path=${Uri.encode(p.wsPath.ifEmpty { p.h2Path })}")
                if (p.xhttpMode.isNotEmpty()) params.add("mode=${Uri.encode(p.xhttpMode)}")
                else params.add("mode=auto")
            }
            else -> params.add("type=tcp")
        }
        if (p.security == SecurityType.REALITY) {
            params.add("fp=${p.realityFingerprint}")
            params.add("pbk=${Uri.encode(p.realityPublicKey)}")
            if (p.realityShortId.isNotEmpty()) params.add("sid=${Uri.encode(p.realityShortId)}")
            if (p.serverName.isNotEmpty()) params.add("sni=${Uri.encode(p.serverName)}")
        }
        if (p.security == SecurityType.TLS) {
            params.add("fp=${p.fingerprint}")
            if (p.serverName.isNotEmpty()) params.add("sni=${Uri.encode(p.serverName)}")
            if (p.alpn.isNotEmpty()) params.add("alpn=${Uri.encode(p.alpn.joinToString(", "))}")
        }
        val name = Uri.encode(p.name.ifEmpty { "SYBbox" })
        return "vless://${p.uuid}@${p.address}:${p.port}?${params.joinToString("&")}#$name"
    }

    private fun generateVmessLink(p: ServerProfile): String {
        val json = org.json.JSONObject()
        json.put("v", "2")
        json.put("ps", p.name.ifEmpty { "SYBbox" })
        json.put("add", p.address)
        json.put("port", p.port)
        json.put("id", p.uuid)
        json.put("aid", p.alterId)
        json.put("scy", p.encryption)
        json.put("net", when (p.transport) {
            TransportType.HTTP -> "h2"
            TransportType.XHTTP -> "xhttp"
            else -> p.transport.name.lowercase()
        })
        if (p.transport == TransportType.WS || p.transport == TransportType.HTTPUPGRADE ||
            p.transport == TransportType.XHTTP || p.transport == TransportType.HTTP
        ) {
            json.put("host", p.wsHost.ifEmpty { p.h2Host })
            json.put("path", p.wsPath.ifEmpty { p.h2Path })
        }
        if (p.transport == TransportType.GRPC) json.put("path", p.grpcServiceName)
        if (p.transport == TransportType.XHTTP && p.xhttpMode.isNotEmpty()) json.put("mode", p.xhttpMode)
        json.put("tls", if (p.security != SecurityType.NONE) "tls" else "")
        if (p.security != SecurityType.NONE && p.serverName.isNotEmpty()) json.put("sni", p.serverName)
        return "vmess://${android.util.Base64.encodeToString(json.toString().toByteArray(), android.util.Base64.NO_WRAP)}"
    }

    private fun generateTrojanLink(p: ServerProfile): String {
        val params = mutableListOf<String>()
        if (p.transport == TransportType.WS) {
            params.add("type=ws")
            if (p.wsHost.isNotEmpty()) params.add("host=${Uri.encode(p.wsHost)}")
            if (p.wsPath.isNotEmpty()) params.add("path=${Uri.encode(p.wsPath)}")
        }
        if (p.serverName.isNotEmpty()) params.add("sni=${Uri.encode(p.serverName)}")
        if (p.fingerprint.isNotEmpty()) params.add("fp=${p.fingerprint}")
        val name = Uri.encode(p.name.ifEmpty { "SYBbox" })
        val qs = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "trojan://${Uri.encode(p.tuicPassword)}@${p.address}:${p.port}${qs}#$name"
    }

    private fun generateSsLink(p: ServerProfile): String {
        val userInfo = android.util.Base64.encodeToString(
            "${p.ssMethod}:${p.ssPassword}".toByteArray(), android.util.Base64.NO_WRAP
        )
        val name = Uri.encode(p.name.ifEmpty { "SYBbox" })
        return "ss://${userInfo}@${p.address}:${p.port}#${name}"
    }

    private fun generateHy2Link(p: ServerProfile): String {
        val params = mutableListOf<String>()
        params.add("insecure=${if (p.allowInsecure) "1" else "0"}")
        if (p.hy2ObfsType.isNotEmpty()) {
            params.add("obfs=${p.hy2ObfsType}")
            params.add("obfs-password=${Uri.encode(p.hy2ObfsPassword)}")
        }
        if (p.serverName.isNotEmpty()) params.add("sni=${Uri.encode(p.serverName)}")
        val name = Uri.encode(p.name.ifEmpty { "SYBbox" })
        val qs = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "hysteria2://${Uri.encode(p.hy2Password)}@${p.address}:${p.port}${qs}#$name"
    }

    private fun generateTuicLink(p: ServerProfile): String {
        val params = mutableListOf<String>()
        params.add("congestion_control=${p.tuicCongestionControl}")
        if (p.serverName.isNotEmpty()) params.add("sni=${Uri.encode(p.serverName)}")
        params.add("allowInsecure=${if (p.allowInsecure) "1" else "0"}")
        val name = Uri.encode(p.name.ifEmpty { "SYBbox" })
        val qs = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "tuic://${Uri.encode(p.tuicPassword)}@${p.address}:${p.port}${qs}#${name}"
    }

    private fun generateWgLink(p: ServerProfile): String {
        val params = mutableListOf<String>()
        params.add("privateKey=${Uri.encode(p.wgPrivateKey)}")
        params.add("peerPublicKey=${Uri.encode(p.wgPeerPublicKey)}")
        if (p.wgPresharedKey.isNotEmpty()) params.add("presharedKey=${Uri.encode(p.wgPresharedKey)}")
        params.add("address=${Uri.encode(p.wgLocalAddress)}")
        params.add("mtu=${p.wgMTU}")
        if (p.wgReserved.isNotEmpty()) params.add("reserved=${Uri.encode(p.wgReserved.joinToString(","))}")
        val name = Uri.encode(p.name.ifEmpty { "SYBbox" })
        return "wireguard://${Uri.encode(p.wgPrivateKey)}@${p.address}:${p.port}?${params.joinToString("&")}#${name}"
    }

    fun generateQrBitmap(content: String, size: Int = 600): Bitmap {
        val writer = QRCodeWriter()
        val hints = mapOf(
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            com.google.zxing.EncodeHintType.MARGIN to 2,
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val w = bitMatrix.width
        val h = bitMatrix.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        for (x in 0 until w) {
            for (y in 0 until h) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.WHITE else Color.BLACK)
            }
        }
        return bitmap
    }

    fun shareConfig(context: Context, profile: ServerProfile) {
        val link = generateShareLink(profile)
        if (link.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
            putExtra(Intent.EXTRA_SUBJECT, "SYBbox - ${profile.name.ifEmpty { profile.protocol.name }}")
        }
        context.startActivity(Intent.createChooser(intent, "Share config via"))
    }

    fun shareQrCode(context: Context, profile: ServerProfile) {
        val link = generateShareLink(profile)
        if (link.isEmpty()) return
        val bitmap = generateQrBitmap(link)
        val file = File(context.cacheDir, "qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "QR: ${profile.name.ifEmpty { profile.protocol.name }}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR code via"))
    }

    fun copyToClipboard(context: Context, profile: ServerProfile): Boolean {
        val link = generateShareLink(profile)
        if (link.isEmpty()) return false
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        manager.setPrimaryClip(android.content.ClipData.newPlainText("SYBbox config", link))
        return true
    }
}