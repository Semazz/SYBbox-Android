# SYBbox

[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

sing-box based universal proxy toolchain for Android.

一款基于 sing-box 的 Android 通用代理软件.

## Downloads

Private repository — builds available in Releases.

## Supported Proxy Protocols

* VLESS (REALITY, XTLS Vision, XHTTP)
* VMess
* Trojan
* Shadowsocks
* Hysteria 2
* TUIC
* WireGuard
* AnyTLS
* ShadowTLS

## Supported Transports

* TCP
* WebSocket
* HTTP/2
* gRPC
* HTTP Upgrade
* QUIC
* KCP
* XHTTP

## Features

* Material 3 dynamic color (dark + lime accent)
* Subscription management with auto-refresh
* Per-server and global latency measurement (TCP handshake + proxy URLTest)
* DPI bypass: TLS ClientHello fragmentation
* Configurable local DNS / remote DNS / DNS bypass
* Per-app proxy, bypass mode, SOCKS5 inbound
* Inbound sharing (SOCKS5 / HTTP)
* Logs viewer (real-time sing-box log stream)
* Multi-language: English, Русский, Español, 中文

## Supported Subscription Format

* Shadowsocks, ClashMeta, v2rayN formats
* sing-box outbound (parsed directly)

Only node outbound is extracted; routing rules are ignored.

## Architecture

```
VpnService ──openTun──▶ sing-box tun inbound ──▶ route rules ──▶ vless/vmess/... outbound
     ▲                                                                   │
     └──────────────────────── protect(fd) ◀─────────────────────────────┘
```

| Component | Location |
| --- | --- |
| Go binding + platform adapter | `libcore/core/` |
| Kotlin platform interface | `app/src/main/java/com/sybbox/core/SingBoxPlatform.kt` |
| Configuration generation | `app/src/main/java/com/sybbox/core/ConfigBuilder.kt` |
| VPN service lifecycle | `app/src/main/java/com/sybbox/service/SybBoxVpnService.kt` |
| UI screens | `app/src/main/java/com/sybbox/ui/` |

## Building

### The native core

Build the AAR before building the app:

```bash
cd libcore && ./build.sh
```

Requires: Go, gomobile, Android SDK + NDK.

Build tags: `with_gvisor`, `with_quic`, `with_utls`, `with_clash_api`, `badlinkname`, `tfogo_checklinkname0`.

### The app

```bash
./gradlew assembleRelease
```

Output is per-architecture (`SYBbox-{version}-{abi}.apk`).

## Credits

Core:

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)
- [justinwoo280/sing-xhttp](https://github.com/justinwoo280/sing-xhttp)

Android GUI:

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)

## License

[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0)
