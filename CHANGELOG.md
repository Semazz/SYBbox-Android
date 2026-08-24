# Changelog

## v2.0.10 (2026-08-24)

### Changed — per-app mode switch
"Only selected apps" / "All apps except selected" was 66 lines of copy-paste whose two
halves had drifted apart: 14dp icon against 16dp, 4dp padding against 8dp, and a hard-coded
`fontSize = 11.sp` overriding the type scale. Selecting a mode repainted instantly with no
transition.

It now uses the same segmented control as the settings rows — one implementation, so the
two cannot drift again — with matching metrics on both halves and the selection animating
between them. 3745 characters replaced by 594.

### Performance
- **App list.** Every installed app's launcher icon was decoded before the list could
  appear, and then held for the lifetime of the screen — a few hundred drawables for the
  dozen that are visible. Icons are now fetched per row and kept in a small LRU cache, so
  scrolling back finds them already loaded. `GET_META_DATA` dropped from the query as well;
  nothing read those bundles.
- **App filtering** is keyed on the app list, the query and the system-apps toggle. It used
  to re-filter every installed app on every recomposition, including each checkbox tick.
- **Log list** rows now have stable keys. `LogEntry` carries a monotonic id: timestamps
  collide, because the core writes many lines inside one millisecond, so without an id
  every appended line re-bound the entire list.

### Verified
- 86 unit tests; 1440 configs start on the real core.

## v2.0.9 (2026-08-24)

### Changed — how a setting with fixed values is presented
Choice rows opened a bare `DropdownMenu`. It anchored to the row's upper-left corner rather
than to the value being changed, and drew itself on Material defaults that shared nothing
with the app's own surfaces.

Replaced with two layouts, picked at runtime from the rendered labels rather than by a flag
at each call site:

- **Segments** when there are two to four options and every label is short. All choices are
  visible at once and switching is a single tap, with the selection animating between them.
  This is what mode rows like MTU, max streams and multiplex protocol get.
- **A sheet** otherwise. Russian labels run long — `gVisor (в пространстве пользователя,
  надёжнее)` — so those get full-width rows with the selected one tinted and ticked.

The row's current value is now a tinted pill with a downward chevron, so it reads as
something that opens a picker rather than as navigation to another screen.

`SettingsChoice` moved to its own file; its signature is unchanged, so all eleven call sites
were untouched.

### Verified
- 84 unit tests.

## v2.0.8 (2026-08-24)

### Fixed — the first latency reading was far larger than the rest
A single TCP connect was being timed, and the first connect to a host pays for waking a
dormant radio, ARP and building the route — hundreds of milliseconds on mobile that have
nothing to do with the server. Latency is now the lowest of three samples, which throws
that cost away. A server that fails twice stops being retried, so an unreachable one still
resolves in about the same time as before.

### Fixed — a default that silently disabled two features
`ServerProfile.flow` defaulted to `xtls-rprx-vision`. Only the VLESS parser sets flow, so
every trojan, vmess, shadowsocks, hysteria2 and tuic profile inherited it. Nothing emitted
it into the config, so it was invisible — but multiplex and ClientHello fragmentation both
skip a profile that claims vision, so both were off for those protocols without a word.
Default is now empty.

The same shape of bug applied to multiplex: `multiplexProtocol` defaults to `h2mux` in the
profile, so reading it unconditionally meant the settings screen could never change it. A
profile now overrides the global settings only when it enabled multiplex itself.

### Added — settings
- **TCP Fast Open** (off by default; some networks drop a SYN carrying data).
- **Multiplex protocol / max streams / padding** — the fields existed on profiles with no
  way to set them.
- **Check the tunnel after connecting** — the traffic check added in v2.0.7, now switchable.

Each new option is covered by a test asserting it reaches the generated config, because a
switch that changes nothing is worse than no switch.

### Changed — one spacing scale
Card insets came in four different combinations and chips in three, so identical-looking
elements sat at different depths depending on the screen. `ui/theme/Spacing.kt` now holds
the scale and the screens use it — 22 hand-written values replaced.

Removed the "N servers" line under the group and subscription headers.

### Verified
- 84 unit tests; 1440 configs start on the real core.

## v2.0.7 (2026-08-24)

### Found — the same subscription serves different servers from different paths
`https://api.liteweb.cc/vpn-configs/<id>/VLESS` and `https://api.liteweb.cc/vpn-configs/<id>`
are not the same subscription:

| path | contents |
| --- | --- |
| `/VLESS` | v2ray JSON array: REALITY on 443, REALITY gRPC on 9443, Hysteria2 on 1443 |
| no suffix | base64 link list: the above **plus** plain-TLS VLESS over gRPC on 8443 |

Measured against the live servers:

| entry | result |
| --- | --- |
| VLESS + TLS + gRPC, port 8443 | **works** |
| VLESS REALITY, 443 and 9443 | handshake refused, decoy served |
| Hysteria2, 1443 | works |

The working VLESS nodes exist only on the suffix-less path, which is why the subscription
behaves differently depending on which URL was added. The REALITY keys are byte-identical
between the two paths, so nothing is stale and the parser reads both correctly — an earlier
suspicion of stale subscription data is now conclusively ruled out.

### Added — the app checks that the tunnel actually carries traffic
A TCP handshake succeeds against a REALITY server whether or not it will accept us, so a
server could report healthy latency and pass nothing while the app displayed "connected".

- The config gains a loopback `mixed` inbound on 127.0.0.1 (the app is excluded from its
  own tun, so without one it cannot put a request through the tunnel it just started).
- After connecting, the app fetches a 204 endpoint through it. On failure it says so
  plainly instead of leaving a connected-looking tunnel that carries nothing, and moves to
  the next server when auto-failover is on.
- Verified against the live servers: the refusing REALITY node reports no traffic, the
  working TLS node reports traffic.

### Fixed — the config validator reported failures that were its own load
Eight cores starting at once under a four-second cap produced failures that passed when
re-run alone. Now four at a time with an eight-second cap.

### Verified
- 77 unit tests.

## v2.0.6 (2026-08-24)

### Changed — remote resolver moved to DoH
The tunnel carried traffic while DNS stalled: connections completed, but lookups sat
pending for 3–18 seconds and were still pending when the tunnel was torn down.

Default remote resolver changed from `tcp://1.1.1.1` (introduced in v2.0.4) to
`https://1.1.1.1/dns-query`. It runs on port 443 rather than 53, which some providers drop
on egress, and naming it by address means nothing has to resolve the resolver first.

Measured through the provider's live Hysteria2 node, 30 concurrent lookups: `tcp://:53`,
`udp://:53`, DoH and DoT all returned 27/30 in about 2 seconds, so this is not a proven fix
for the stall — the transports were indistinguishable under test. DoH is chosen because it
removes an entire failure mode (blocked port 53) at no measured cost.

### Correction — an earlier measurement in this investigation was invalid
Load-testing the app's generated config on a desktop produced 0/25, which looked like a
config defect. It was not: `route.auto_detect_interface` is what failed. Android supplies
interface detection and socket protection through the platform interface; a desktop
sing-box has neither and binds outbound sockets to the wrong interface. Verified by adding
that single option to a working config, which took it from 5/5 to 0/5.

Consequently `tools/validate-configs.sh` proves configs **start**, not that they carry
traffic; traffic behaviour cannot be measured this way on a desktop.

Also ruled out by measurement, each after being suspected:
- `tls.alpn: ["h3"]` on Hysteria2, carried over from the subscription — a working config
  with it added still returned 5/5.
- Port 53 being blocked by the provider — plain TCP and UDP on 53 both resolved fine.

## v2.0.5 (2026-08-24)

### Diagnosed — a subscription whose REALITY nodes will not carry traffic
Measured against the provider's live servers, using the app's own generated config run
through a real sing-box built from `libcore/singbox-fork`:

| entry | result |
| --- | --- |
| Hysteria2 (2 nodes) | HTTP 204 — works |
| VLESS REALITY, TCP and gRPC (4 servers) | handshake refused, server serves its decoy |

So the account is active and the hosts are reachable; only the REALITY nodes fail.

`common/tls/reality_client.go` in the fork is identical to upstream sing-box v1.13.4 —
the fork differs from upstream in 9 files, all of them XHTTP transport or WireGuard. The
REALITY client is stock. The same subscription is reported working in Happ, which uses
Xray; Xray was not tested here, so that half is the user's report, not a measurement.

An earlier revision of this entry claimed the subscription's SNI and public key were stale.
That was wrong. It rested on reading `reality verification failed` (seen with a google SNI)
as the server accepting that SNI. Both that error and the `x509` one mean the same thing —
the server served its decoy — and they differ only in whether the decoy's certificate
happens to match the SNI being asked for.

### Fixed — failures were unreadable
- Core messages are condensed for display. A single REALITY fallback error carries the full
  SAN list of Google's certificate, around 1.5 KB, and a handful of them evicted everything
  else from the 300-entry log buffer.
- Known failures now get a one-line explanation in the log, once per tunnel: REALITY refused,
  bootstrap resolution failures, rejected credentials, and the detour misconfiguration from
  v2.0.3. The raw message is kept above it.

### Verified
- 69 unit tests, including the log pipeline end to end on the verbatim device message;
  1440 configs still start on the real core.

## v2.0.4 (2026-08-24)

### Fixed — latency reported, no traffic
Every connection sat waiting on the core resolving the proxy's own hostname:

    outbound/vless[proxy]: outbound connection to 1.1.1.1:53
    dns: lookup domain node.subsyb.online
    ... 8s ...
    dns: lookup failed for node.subsyb.online: context canceled

`domain_resolver` names a transport directly and bypasses the DNS rules
(`dns/router.go:353`), so the bootstrap went straight to `dns-local`. When that resolver is
unreachable — plain UDP to a public resolver is dropped rather than refused on some
networks — the lookup hangs until it is cancelled, and nothing the tunnel carries ever
gets dialed. The latency check kept reporting the server as healthy because it resolves in
Kotlin, on the physical network, which works.

- The server's hostname is now resolved in Kotlin before the config is built, over the same
  non-VPN network the latency check uses, and the outbound is given the address. With an
  address there is nothing to look up, so `domain_resolver` is omitted entirely.
- `profile.address` is untouched, so TLS still gets the hostname for SNI — the certificate
  is issued for the name, never for the address.
- Falls back to dialing by name (with `dns-local` as resolver) when resolution fails.

Measured against a deliberately unreachable bootstrap resolver:

| dialing | core log |
| --- | --- |
| by name | `lookup domain se.example.com` -> `context deadline exceeded` after 10.2s |
| by address | `outbound connection to example.com:443`, no lookup at all |

### Config validation
- Matrix extended to **1440 configs**: both the pre-resolved and the dial-by-name path
  across every protocol, transport and settings combination. All start on the real core.

### Known, not fixed
- Android's Private DNS probes DoT against the tunnel's own address
  (`open connection to <tun-ip>:853`). The core only hijacks port 53, so these attempts
  fail. Harmless, and left alone rather than papered over with a rule that could break
  legitimate DoT.
- `router: failed to search process: dial netlink: permission denied` is Android refusing
  netlink for process matching. Noise, no effect on traffic.

## v2.0.3 (2026-08-24)

### Fixed — "Не удалось подключиться"
- `dns-local` and `dns-direct` carried `detour: "direct"`. The core refuses that outright:
  `start dns/udp[dns-direct]: detour to an empty direct outbound makes no sense`
  (`common/dialer/detour.go`). Detour dropped — without one the default dialer already
  stays on the underlying network, which is the behaviour that was wanted.
- Added `route.default_domain_resolver`, clearing the 1.12 deprecation warning that becomes
  an error in sing-box 1.14. Startup is now warning-free.

### Added — configs are validated against the real core
Two DNS bugs shipped in a row because configs were only ever eyeballed and unit-tested.
Neither was catchable that way: both are raised when transports *start*, and `sing-box
check` does not catch them either — it only parses.

- `ConfigMatrixDumpTest` writes 720 configs: 15 protocol/transport profiles
  (VLESS reality-vision / TLS over TCP, WS, gRPC, HTTP, HTTPUpgrade, XHTTP with a v2ray
  extra block, plus VMess, Trojan, Shadowsocks, Hysteria2, TUIC, AnyTLS, ShadowTLS,
  WireGuard) x 12 settings variants x with/without a system resolver x with/without
  rule sets.
- `tools/validate-configs.sh` starts each one with a real `sing-box` built from
  `libcore/singbox-fork` using the same build tags as `libcore/build.sh`. The tun inbound
  is swapped for a loopback listener so it runs unprivileged; dns, outbounds and route —
  where both bugs were — are untouched.
- Current status: **720 passed, 0 failed**. Reintroducing either bug fails the matrix
  (the direct detour fails 360 of 720 with the exact user-reported error) and the unit
  tests alongside it.

## v2.0.2 (2026-08-24)

### Fixed — regression from v2.0.1: nothing connected at all
- `dns-local` was emitted as the core's `type: "local"` transport. That transport reads
  `/etc/resolv.conf`, which Android does not have, so it falls back to `127.0.0.1:53` and
  every lookup was refused:
  `lookup failed for <server>: read udp [::1]:53: connection refused`.
  Because the proxy outbound bootstraps its own hostname through that resolver, no
  connection could be opened — including servers that had worked before.
- The bootstrap resolver is now the one the device actually uses, read from
  ConnectivityManager for the non-VPN network and queried over the `direct` outbound.
  Falls back to a public resolver, never to localhost.
- IPv4 resolvers are preferred, and loopback addresses reported by the device are skipped.
- Default remote resolver changed from `udp://1.1.1.1` to `tcp://1.1.1.1`: UDP through the
  proxy depends on the server relaying UDP, while TCP always works.
- Regression guard added: a test asserts no resolver in any configuration uses the local
  transport or points at loopback. Reintroducing it fails six tests.

## v2.0.1 (2026-08-24)

### Fixed — tunnel connected but no traffic
- DNS `final` switched from `dns-direct` to `dns-remote`. Every lookup was being sent to a
  DoH resolver over the **direct** outbound; where that resolver is blocked the tunnel came
  up and answered a TCP handshake (so latency showed) while nothing resolved.
- Added a `type: "local"` DNS server (`dns-local`) and pointed the proxy outbound's
  `domain_resolver` at it, plus a DNS rule for the server's own hostname. This is what
  breaks the bootstrap loop, and it no longer requires leaking every other query.
- Resolver addresses are no longer rewritten to `https://`. `udp://`, `tcp://`, `tls://`,
  `quic://`, `h3://`, a bare IP and `local` are each honoured as written.
- Default resolvers changed to `udp://1.1.1.1` remote (private because it rides the tunnel)
  and the system resolver for direct.
- Rule sets download through the proxy instead of direct — they are hosted on GitHub.

### Fixed — subscriptions
- Removed the manual `Accept-Encoding: gzip` request header. Setting it disables OkHttp's
  transparent decompression, so any panel that gzips its response returned binary to the
  parsers and imported zero servers.
- Clash subscriptions are parsed as YAML (SnakeYAML) instead of being handed to Gson, which
  threw on every Clash config and silently produced an empty list.
- `parseUri` no longer propagates exceptions, and Clash / sing-box / LiteVPN entries are
  isolated per item, so one malformed entry no longer discards the whole subscription.
- IPv6 literals (`[2001:db8::1]:443`) parse correctly; they used to be split at the first
  colon inside the address.
- Links with no port, no `@`, or a stray `%` are rejected individually instead of throwing.
- TUIC reads its password from the userinfo (`tuic://uuid:password@host`), not the query.
- `vmess://` payloads decode across padded, unpadded and url-safe base64.
- Single `parseAny` entry point tries every known subscription shape, including base64
  wrapping JSON or YAML.

### Fixed — routing
- Per-app proxy reached the config; it was dead code behind `if (false && ...)`.
- `auto_route` and `strict_route` honour their settings instead of being hardcoded.
- Dropped the blanket STUN/TURN reject. The `domain_keyword` list matched `saturn`,
  `return` and `turnitin` as readily as `stun`, and the traffic rides the tunnel anyway, so
  rejecting it only broke voice and video calls.
- XHTTP transport emits only the keys the core accepts. Spreading a v2ray `xhttpSettings`
  block into it produced unknown fields, and the core rejects the entire config on those.

### Performance
- Settings are read in one pass instead of ~25 separate reads per connect.
- Subscription refresh reuses one HTTP client, stops walking the User-Agent fallback list
  after a transport failure, and refreshes subscriptions concurrently. Worst case went from
  roughly 4.5 minutes of stacked timeouts to about 25 seconds.

### Tests
- 48 unit tests, covering the DNS routing contract, per-item subscription resilience,
  Clash YAML, IPv6 links and malformed input.

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
