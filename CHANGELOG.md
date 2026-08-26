# Changelog

## v2.0.2 (2026-08-25)

### Added — settings
- **Local proxy.** A SOCKS5 and HTTP inbound on a port of your choosing, so other apps can reach
  the tunnel without being routed through it. **Allow connections from the LAN** moves it off
  loopback onto every interface for other devices on the network.
- **On start.** Refresh subscriptions, test latency, connect. Latency on start was unconditional
  before, and is now a switch you can turn off. Connecting only happens where VPN permission was
  already granted — the app never raises the consent dialog on its own.
- **Tunnel check address** and **ping timeout** are configurable. The configured address is tried
  first and the built-in ones remain as fallbacks.
- **Check for updates** reads the GitHub release feed and says what is out and what you are on.
  It also looks on its own, once a day, and says so with a thin bar at the foot of the home
  screen rather than a notification or a dialog — tap it to open the release, close it and that
  version never asks again. The daily look can be turned off.
- **Reset settings** puts every setting back to its default and leaves servers and subscriptions
  alone.
- **Send device id.** Subscription requests carry `x-hwid`, `x-device-os`, `x-ver-os` and
  `x-device-model`, which is what a panel reads to enforce a device limit. The id is random and
  made once per install — no hardware identifier is read. It shows in Settings and copies on tap,
  and the switch turns the headers off.
- The subscription User-Agent no longer carries an identifier of its own. It was smuggling a
  per-install id into every request whether or not you wanted one; identity now lives in the
  headers, behind the switch.

### Fixed — subscriptions
- A subscription's own update interval is honoured. Panels send it as `profile-update-interval`;
  the app read the traffic counters and the title from the same response and threw this one away,
  so a subscription that asked to be refreshed every twelve hours was refreshed on whatever the
  app's own schedule said. The interval is stored with the subscription and used as its own.
- The background job wakes hourly and refreshes only what is actually due, comparing each
  subscription's interval against when it was last updated, with five minutes of slack. Without
  the slack an hourly subscription would be skipped by a tick landing a minute early and wait
  another hour, turning one hour into two. The interval in Settings is now the
  fallback for subscriptions that do not state one, and says so.
- A subscription that states no interval really does fall back. The field it is kept in started
  life at six hours rather than empty, so every subscription looked as though it had asked for
  six and the setting never applied to anything. It starts empty now, and each refresh records
  what the panel said this time — including that it said nothing.

### Changed — settings
- Settings open as a list of pages rather than one long scroll. The root names four areas —
  interface, tunnel, advanced, other — and each row opens its own screen with a title bar and a
  way back.
- The device id always travels with a subscription request; the switch is gone. A panel that
  limits devices cannot count them if the client may opt out, so the only thing left to do with
  it is read it, and it is still one tap to copy.
- Latency is measured when you ask for it. Opening the server list used to test every server on
  its own, which is a burst of connections nobody asked for; the switch that turned it off went
  with it.
- Per-app routing is listed once. It sat both on the settings root and inside routing.
- The settings tab returns to the settings root. It used to leave the row unhighlighted while a
  page was open, and tapping it from there could land you on the home screen.
- Tapping the tab you are already on does nothing. It used to pop the stack back to the start
  destination and push the tab again, replaying the transition; held down, that read as the
  screen shaking.
- One spacing scale across the screens that had drifted apart — gaps of 10, 12, 16, 28 and 32
  pixels standing in for the same intent now come from the same handful of named steps.

### Added — home screen widgets
- **Two widgets.** A 2×1 with the logo, a state line and a power button, and a 1×1 that is
  nothing but the button. Both use the app's own palette and follow the system between light and
  dark; the button carries the accent while the tunnel is up and goes quiet when it is not.
- The button connects to the last server used and disconnects again. Where VPN permission has not
  been granted, or no server has been picked, it opens the app instead of failing silently.
  On the wide one, tapping beside the button opens the app.
- Both follow the tunnel while the app's process is alive, redrawing whenever the state changes.

### Changed — subscriptions
- The subscription User-Agent carries a per-install key again — `SYBbox/2.0.2/Android/<key>` —
  rather than the build number. Panels key their device lists on it, and a number shared by every
  install of the same build is no key at all.

### Fixed — IP leaks
- **Leak protection**, on by default. IPv6 is rejected and the resolver is pinned to `ipv4_only`.
  The tunnel carries IPv4; leaving AAAA answers in play meant an address the tunnel never covered.
- The tunnel address is fixed rather than randomised every connect, and the IPv6 tunnel address
  is dropped while leak protection is on.
- **Hide the tunnel address**, on by default. The tunnel takes a link-local address
  (`169.254.19.1/30`), which WebRTC skips when it lists local addresses — a full-tunnel VPN
  otherwise hands the probe its own tunnel address to report. Link-local source addresses are
  unusual enough that the app falls back to `172.19.0.1/30`, and says so in the log, if Android
  keeps no resolver for the tunnel. That is the one symptom the address can cause; a server that
  carries no traffic is the server's problem and no longer costs the hidden address — which is
  why XHTTP, slow to come up, was the one transport still showing `172.19.0.1`. The check waits
  up to two seconds for Android to publish the tunnel's resolver rather than judging it at once.
- Apps get a routable resolver address while the tunnel address is link-local. Android would not
  hand a link-local resolver to apps, so name lookups died — anything with hardcoded addresses
  kept working, which made it look like only websites were broken. DNS is hijacked by protocol,
  not by destination, so the advertised address only has to fall inside the tunnel's routes.
- STUN rides the tunnel. Rejecting it is what made a leak test report the tunnel's own address:
  with no server-reflexive candidate to show, the page falls back to the local one. Letting STUN
  through means WebRTC reports the exit address, which is the answer a leak test wants to see.
- **Block WebRTC discovery** is its own switch, off by default, for anyone who would rather ICE
  found nothing at all. It refuses STUN and TURN by port, and by hostnames starting with `stun`
  or `turn` — anchored at the start, so the blanket `domain_keyword` rule that once caught
  `saturn`, `return` and `turnitin` is not coming back. Voice and video calls stop working.

### Fixed — latency checks
- Checking latency no longer raises and drops the tunnel. It used to connect, wait, and
  disconnect purely to take the VPN slot away from another app, which looked like the VPN
  flickering on and off.
- A foreign VPN is now evicted by a service that claims the VPN slot and closes it immediately.
  The other app stops, the tunnel is never built, and the measurement runs over the physical
  network as before.
- “Another VPN app is active” only appears when one really is. A VPN transport alone was enough
  to trigger it, so the tunnel we had just torn down — or our own, still winding up — counted as
  somebody else's.

### Fixed — servers
- The standalone servers card has an overflow menu: test all, and delete all behind a
  confirmation.
- Taps are ignored for 400 ms after the list re-lays out. Selecting a server collapses its
  group and moves every row below it, so a second quick tap used to land on whichever server
  had slid under the finger — pick Hysteria, connect to VLESS.
- Latency shows for ten seconds after the tunnel comes up, without having to ask for it.

### Fixed — switching servers
- Switching servers quickly no longer lands on the first one in the subscription. Each switch
  cancels the connection attempt in flight, and the cancellation was caught as a connection
  failure — which handed it to auto-failover, which walked to the next server and wrapped around
  to the first. Cancellation now passes through, and failover never revisits a server it has
  already tried.

### Changed — server list
- Groups start open and remember being closed. Which subscriptions are collapsed survives leaving
  the screen and restarting the app, and more than one can be open at a time.
- Row and subscription actions live in the overflow menu. The inline test and refresh buttons
  are gone; the latency badge stays.
- A collapsed group shows no rows at all. It used to keep the selected one visible, so choosing
  a server elsewhere made that row vanish and slid the whole list up under the finger — pick
  Hysteria, land on the first server of a subscription. The group header names its selection
  instead.
- The home screen keeps the server you picked. It used to fall back to the first profile in the
  list whenever the stored id was briefly missing, which a subscription refresh causes on its
  own. Deleting a server now repoints the selection deliberately instead.

### Fixed — per-app routing
- Selected apps sort to the top of the list.
- The empty search result read as a row of question marks. The string had been mangled to
  replacement characters in the source; it is a translated resource now.
- Reordering waits until you stop toggling. It used to happen mid-tap, so the row that slid under
  the finger took the press and lit up instead of the one that was tapped, and item animation drew
  two rows over each other while they swapped.

## v2.0.1 (2026-08-24)

### Fixed — tunnel connected but no traffic
- DNS `final` moved from `dns-direct` to `dns-remote`. Every lookup was being sent to a DoH
  resolver over the **direct** outbound, so where that resolver is blocked the tunnel came
  up and answered a TCP handshake — latency looked healthy — while nothing resolved.
- The server hostname is resolved before the config is built, over the same non-VPN network
  the latency check uses. Dialing by name made every connection wait on the core's bootstrap
  resolver; when that resolver was unreachable, lookups sat pending until they timed out.
- The bootstrap resolver is the device's own, read from ConnectivityManager. The core's
  `type: "local"` reads `/etc/resolv.conf`, which Android does not have, and falls back to
  `127.0.0.1:53` where nothing is listening.
- Resolver addresses are no longer rewritten to `https://`. `udp://`, `tcp://`, `tls://`,
  `quic://`, `h3://` and a bare address are each honoured as written.
- Removed `detour` to the bare `direct` outbound on DNS servers, which the core refuses
  outright: `detour to an empty direct outbound makes no sense`.
- Default remote resolver is `https://1.1.1.1/dns-query` — port 443 rather than 53, which
  some providers drop, and an address so nothing has to resolve the resolver first.

### Fixed — subscriptions
- Removed the hand-set `Accept-Encoding: gzip` header. Setting it disables OkHttp's
  transparent decompression, so any panel that gzips its response handed the parsers binary
  and imported nothing.
- Clash subscriptions parse as YAML. They were being given to Gson, which threw on every one
  of them and left an empty list behind the catch; SnakeYAML was already a declared,
  unused dependency.
- One malformed entry no longer discards the whole subscription. `parseUri` cannot throw,
  and Clash, sing-box and config-array entries are isolated per item.
- IPv6 literals keep their address and port — `indexOf(':')` used to cut them in half.
  Links with no port, no `@`, or a stray `%` are rejected individually.
- TUIC reads its password from the userinfo, not the query. `vmess://` payloads decode
  across padded, unpadded and url-safe base64.
- A single `parseAny` entry point tries every known shape instead of guessing from the first
  character, including base64 wrapping JSON or YAML.

### Fixed — routing and protocols
- Per-app proxy reached the config. It was dead code behind `if (false && ...)`.
- `auto_route` and `strict_route` follow their settings instead of being hardcoded.
- Dropped the blanket STUN/TURN reject. Its `domain_keyword` list matched `saturn`, `return`
  and `turnitin` as readily as `stun`, and the traffic rides the tunnel anyway, so rejecting
  it only broke voice and video calls.
- XHTTP emits only the keys the core accepts. Spreading a subscription's v2ray-shaped
  `xhttpSettings` into it produced unknown fields, and the core rejects the entire config on
  those.
- `flow` no longer defaults to `xtls-rprx-vision`. Only the VLESS parser sets flow, so every
  Trojan, VMess, Shadowsocks, Hysteria2 and TUIC profile inherited it — and both multiplex
  and ClientHello fragmentation skip a profile that claims vision, so both were silently off
  for those protocols.
- Rule sets download through the proxy. They are hosted on GitHub, which is exactly what the
  tunnel exists to reach.

### Added
- **Tunnel check.** After connecting, the app fetches a 204 through a loopback inbound to
  find out whether traffic really passes. A TCP handshake succeeds against a REALITY server
  whether or not it accepts us, which is why a server could report healthy latency and carry
  nothing. Two endpoints are tried before a tunnel is called dead. Switchable in settings;
  with auto-failover on, a server that carries nothing hands over to the next one.
- **Readable failures.** Known errors get a one-line explanation in the log, once per
  tunnel — REALITY refused, bootstrap resolution failed, credentials rejected. Core messages
  are condensed: a single REALITY fallback error carries the full SAN list of Google's
  certificate, around 1.5 KB, and a handful of them evicted everything else from the log.
- **Settings:** TCP Fast Open, multiplex protocol / max streams / padding, and the tunnel
  check. Each is covered by a test asserting it reaches the generated config.
- **Config validation against the real core.** `ConfigMatrixDumpTest` writes 1440 configs —
  every protocol and transport across twelve settings variants, with and without a system
  resolver, with and without rule sets, both the pre-resolved and dial-by-name paths — and
  `tools/validate-configs.sh` starts each one with a real sing-box built from
  `libcore/singbox-fork`. Parsing a config does not prove the core will run it, and
  `sing-box check` does not catch start-stage errors either.

### Changed — interface
- One spacing scale in `ui/theme/Spacing.kt`. Card insets came in four different
  combinations and chips in three, so identical-looking elements sat at different depths
  depending on the screen.
- A setting with fixed values no longer opens a bare `DropdownMenu` anchored to the row's
  upper-left corner. Short option sets are laid out as segments; longer labels open a sheet.
  The current value reads as a tinted pill with a downward chevron.
- The per-app mode switch uses that same segmented control. It had been 66 lines of
  copy-paste whose halves had drifted to different icon sizes and padding.
- Removed the "N servers" line under group and subscription headers.

### Performance
- Latency is the lowest of three samples. A single TCP connect was timed, and the first
  connect to a host pays for waking a dormant radio, ARP and building the route — hundreds
  of milliseconds on mobile that say nothing about the server.
- App icons are fetched for the rows on screen and cached, instead of decoding every
  installed app's icon before the list could appear.
- The app filter is keyed on what it depends on rather than re-running on every
  recomposition, and log rows have stable keys so an appended line does not re-bind the list.
- Settings are read in one pass instead of ~25 separate reads per connect.
- Subscription refresh reuses one HTTP client, stops walking the User-Agent list after a
  transport failure, and refreshes subscriptions concurrently.

### Known limitations
- Android's Private DNS probes DoT against the tunnel's own address; the core only hijacks
  port 53, so those attempts fail harmlessly.
- `router: failed to search process: dial netlink: permission denied` is Android refusing
  netlink for process matching. Log noise, no effect on traffic.

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
