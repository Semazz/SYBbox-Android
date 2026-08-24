# SYBbox

[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

## Скачать

[<img src="https://img.shields.io/badge/Скачать-RELEASE-brightgreen?style=for-the-badge" alt="Download" height="40">](https://github.com/Semazz/SYBbox/releases/latest)

| ABI | Ссылка |
| --- | --- |
| arm64-v8a | [SYBbox-2.0.1-arm64-v8a.apk](https://github.com/Semazz/SYBbox/releases/download/v2.0.1/SYBbox-2.0.1-arm64-v8a.apk) |
| armeabi-v7a | [SYBbox-2.0.1-armeabi-v7a.apk](https://github.com/Semazz/SYBbox/releases/download/v2.0.1/SYBbox-2.0.1-armeabi-v7a.apk) |
| x86_64 | [SYBbox-2.0.1-x86_64.apk](https://github.com/Semazz/SYBbox/releases/download/v2.0.1/SYBbox-2.0.1-x86_64.apk) |

Не знаете, какой ABI — берите **arm64-v8a**, он подходит почти всем современным телефонам.

Android VPN клиент на базе [sing-box](https://github.com/SagerNet/sing-box).

## Протоколы

VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, WireGuard, AnyTLS, ShadowTLS

## Транспорты

TCP, WebSocket, HTTP/2, gRPC, HTTP Upgrade, QUIC, KCP, XHTTP

## Возможности

- Подписки: ссылка со списком, base64, sing-box JSON, v2ray JSON, Clash YAML — с автообновлением
- Проверка туннеля после подключения: приложение само загружает страницу через туннель и сообщает, если трафик не идёт
- Измерение задержки по физической сети, лучшее из нескольких замеров
- Обход DPI: фрагментация ClientHello, uTLS, REALITY
- DNS: удалённый через туннель, прямой и системный — по отдельности
- Прокси для отдельных приложений: только выбранные или все, кроме выбранных
- Свои правила маршрутизации, обход для РФ и КНР, блокировка рекламы и трекеров
- Логи в реальном времени с пояснениями к типовым отказам
- Material 3 с динамическими цветами
- Языки: English, Русский, Español, 简体中文

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

APK появятся в `app/build/outputs/apk/release/`, по одному на каждый ABI.

### Подпись

Сборка подписывается ключом, путь и пароли к которому берутся из `keystore.properties`
в корне проекта — он и сам `.jks` не входят в репозиторий:

```properties
storeFile=sybbox-release.jks
storePassword=...
keyAlias=sybbox
keyPassword=...
```

Свой ключ создаётся так:

```bash
keytool -genkeypair -v -keystore sybbox-release.jks -alias sybbox   -keyalg RSA -keysize 4096 -validity 10950
```

Без `keystore.properties` сборка не падает, а выдаёт неподписанный APK — чтобы проект
собирался и без ключа. Установить на устройство можно только подписанный.

### Тесты

```bash
./gradlew test
```

Конфигурации проверяются не только разбором, но и запуском настоящего ядра — часть ошибок
возникает при старте транспортов, и `sing-box check` их не ловит:

```bash
cd libcore/singbox-fork && go build -tags "with_gvisor,with_quic,with_utls,with_clash_api,with_wireguard" -o /tmp/sing-box ./cmd/sing-box
cd ../.. && ./gradlew :app:testDebugUnitTest --tests '*ConfigMatrixDumpTest*'
tools/validate-configs.sh /tmp/sing-box
```

## Лицензия

[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0)
