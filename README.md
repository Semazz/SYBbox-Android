# SYBbox

[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

Android VPN клиент на базе [sing-box](https://github.com/SagerNet/sing-box).

## Протоколы

VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, WireGuard, AnyTLS, ShadowTLS

## Транспорты

TCP, WebSocket, HTTP/2, gRPC, HTTP Upgrade, QUIC, KCP, XHTTP

## Возможности

- Material 3 с динамическими цветами
- Управление подписками с автообновлением
- Измерение задержки (TCP handshake + proxy URLTest)
- Обход DPI (фрагментация ClientHello)
- DNS: локальный / удалённый / обход
- Прокси для отдельных приложений
- Логи в реальном времени
- Языки: English, Русский, Español

## Сборка

### Ядро (Go)

```bash
cd libcore && ./build.sh
```

Требуется: Go, gomobile, Android SDK + NDK.

### Приложение

```bash
./gradlew assembleRelease
```

## Лицензия

[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0)
