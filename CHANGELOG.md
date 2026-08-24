# Changelog

## v2.0.0 (2026-08-23)

### Core
- sing-box v1.13.4 via local fork with XHTTP transport support
- Go core AAR rebuilt with `with_gvisor`, `with_quic`, `with_utls`, `with_clash_api`
- Added WireGuard protocol support in ConfigBuilder
- Removed stale `libgojni.so` from jniLibs that overrode AAR's fresh .so
- Fixed `sys_linux.go` / `sys_other.go` with duplicated `//go:build` directives (dupFD, getTunnelName, linkFlags)

### XHTTP Transport
- Created local sing-box fork (`libcore/singbox-fork/`) with 4 patches
- Bridge file `xhttp.go` for sing-xhttp v0.1.6 integration
- ConfigBuilder emits real `type: "xhttp"` with mode/path/host

### Protocols
- VLESS (REALITY, XTLS Vision)
- VMess
- Trojan
- Shadowsocks
- Hysteria2 — defaults changed to `hy2UpMbps=0, hy2DownMbps=0` (BBR instead of forced Brutal)
- Hysteria2 — added `insecure=1` / `allowInsecure=1` parsing from links
- Hysteria2 — uTLS skipped for QUIC-based protocols (handshake fix)
- TUIC
- AnyTLS
- ShadowTLS
- WireGuard

### Transport
- TCP, WebSocket, HTTP/2, gRPC, HTTP Upgrade, QUIC, KCP, XHTTP

### UI Design
- Material 3 dark theme (#070707) + lime accent (#D9FF57)
- Glass morphism cards with translucent backgrounds
- White theme with tinted background (#F1F3E6) + pure white cards + stronger outlines
- Comfortaa font for headings, Nunito for body
- SVG logo converted to vector drawable (`ic_logo_sybbox.xml`)
- Original lime circle+ring launcher icon restored
- Notification icon: circle-in-ring
- Dynamic color support (on by default)

### DPI Bypass
- TLS ClientHello fragmentation enabled by default
- Fragment sleep: 10ms
- Record fragmentation support

### DNS
- Default changed from DoH (`https://1.1.1.1/dns-query`) to `udp://8.8.8.8`

### Ping
- Pure TCP handshake timing (no ICMP)
- Result outside 1..2999ms range → "unavailable"
- While VPN connected: selected server → proxy URLTest latency; others → "unavailable"
- Home screen: ping button (20s display), not persistent number
- Per-subscription ping-all button (Speed icon)
- Ping-all buttons hidden while VPN connected

### Servers
- Server row: name + protocol/transport/security info line
- No ProtocolChip duplicate
- Stable sort order (ORDER BY id ASC) — no floating selection on ping
- Subscription merge preserves existing server IDs/latency/position
- Name falls back to address when link has no fragment

### Subscriptions
- `profile-title` response header read and decoded (including `base64:` prefix)
- User-Agent format: `SYBbox/2.0/Android/{clientId}`
- 30-second refresh cooldown per subscription
- Concurrent refresh guard
- Bottom "Add subscription" card removed from ServersScreen

### Settings
- Language switching: dual mechanism — SharedPreferences mirror + `attachBaseContext` wrapping
- System-level locale call removed (deterministic only)
- Recreate animations suppressed: `overrideActivityTransition(0,0)` on API 34+, `overridePendingTransition(0,0)` below
- Matching `windowBackground` on light (#F1F3E6) / dark (#070707) + splash on API 31+
- SettingsChoice value width capped at 150dp (prevents vertical text wrapping)
- VPN auto-reconnect on settings change (2.5s debounce)
- Server switch while connected: `switchServer()` forces restart with new profile

### Dead Settings Removed
- alwaysOnVpn, blockWithoutVpn, smartAutoConnect, batterySaver, bypassPreset, logBuffer, pingType, fragmentLength

### Locale Support
- English, Русский, Español
- `localeConfig` declared in manifest with `res/xml/locales_config.xml`

### Build
- APK naming: `SYBbox-{version}-{abi}.apk`
- `resourceConfigurations` includes `en`, `ru`, `es`
- All comments stripped from .kt/.xml files (preserved `//go:build` in Go)
- Unit test `ConfigBuilderTest.kt` validates JSON for all protocols

### Bug Fixes
- Fixed QUIC listeners being added to non-QUIC protocols (HY2/TUIC)
- Fixed subscription base64 name not decoded
- Fixed HomeViewModel `selectedProfileId` floating on ping latency update
- Fixed ping showing fake 1ms (was ICMP fallback or local accept)
- Fixed languages falling back to English (system locale override removed)
- Fixed language flash on recreate (matching windowBackground + suppressed transitions)
