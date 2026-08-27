<div align="center">

# SYBbox

**VPN-клиент для Android на ядре Xray.**
VLESS, VMess, Trojan, Shadowsocks, Hysteria2 и WireGuard — с подписками, маршрутизацией
по правилам и обходом блокировок.

[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Release](https://img.shields.io/badge/release-3.0.0-blue?style=flat)](https://github.com/Semazz/SYBbox-Android/releases/latest)
[![License](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

[Скачать](#скачать) · [Возможности](#возможности) · [Как пользоваться](#как-пользоваться) · [Сборка](#сборка) · [Структура](#структура-проекта)

</div>

---

## Скачать

[<img src="https://img.shields.io/badge/Скачать-RELEASE-brightgreen?style=for-the-badge" alt="Download" height="40">](https://github.com/Semazz/SYBbox-Android/releases/latest)

| ABI | Для кого | Ссылка |
| --- | --- | --- |
| **arm64-v8a** | почти все телефоны с 2018 года | [SYBbox-3.0.0-arm64-v8a.apk](https://github.com/Semazz/SYBbox-Android/releases/download/v3.0.0/SYBbox-3.0.0-arm64-v8a.apk) |
| armeabi-v7a | старые 32-битные устройства | [SYBbox-3.0.0-armeabi-v7a.apk](https://github.com/Semazz/SYBbox-Android/releases/download/v3.0.0/SYBbox-3.0.0-armeabi-v7a.apk) |
| x86_64 | эмуляторы, планшеты на Intel | [SYBbox-3.0.0-x86_64.apk](https://github.com/Semazz/SYBbox-Android/releases/download/v3.0.0/SYBbox-3.0.0-x86_64.apk) |

Не знаете, что выбрать — берите **arm64-v8a**. Требуется **Android 7.0** и новее.

---

## Возможности

### Протоколы и транспорты

| | |
| --- | --- |
| **Протоколы** | VLESS · VMess · Trojan · Shadowsocks · Hysteria2 · WireGuard |
| **Транспорты** | TCP · WebSocket · gRPC · HTTP Upgrade · mKCP · XHTTP |
| **Шифрование** | TLS · REALITY · XTLS Vision · подмена отпечатка uTLS |

### Подписки

Формат определяется сам — вставляйте что угодно из перечисленного:

- список ссылок `vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://` — или он же в base64
- sing-box JSON, v2ray JSON, Clash YAML

Одна битая запись не роняет всю подписку: остальные серверы всё равно импортируются.
Автообновление по расписанию, имя читается из заголовка `profile-title`. При запросе можно
представляться браузером — некоторые панели отдают разные серверы в зависимости от того,
кто спрашивает.

### Проверка соединения

После подключения приложение **само загружает страницу через туннель** и говорит, если
трафик не идёт.

Это важнее, чем кажется: обычное TCP-рукопожатие до сервера проходит, даже если тот потом
откажется пропускать трафик. Отсюда классическое «пинг есть, а интернета нет». При
включённом автопереключении приложение само перейдёт к следующему рабочему серверу.

Задержка меряется по физической сети, берётся лучшая из нескольких попыток — чтобы первое
измерение не завышалось из-за пробуждения радиомодуля. Измерить можно и из шторки, не
открывая приложение.

### Маршрутизация

- Режимы: всё через прокси, сбалансированный, только напрямую, свой
- Обход по доменам и адресам РФ и КНР, блокировка рекламы и трекеров
- Свои правила: домен, суффикс, ключевое слово, IP, порт, geosite, geoip
- По приложениям: **только выбранные** или **все, кроме выбранных**
- Отдельные маршруты в обход туннеля (Android 13+)

Базы geosite и geoip лежат внутри приложения, а не качаются при подключении — правила
работают офлайн и с первой секунды.

### DNS и обход блокировок

Раздельные резолверы для туннеля, прямых соединений и подключения к серверу.
Поддержка `udp://`, `tcp://`, `tls://`, `quic://`, `https://`, `h3://`, плюс FakeDNS.

Фрагментация ClientHello с выбором цели и задержки, мусорные пакеты перед рукопожатием,
подмена отпечатка uTLS, REALITY.

### Локальный доступ

SOCKS5 и HTTP-вход на выбранных портах, с паролем или без. Если разрешить подключения из
локальной сети, приложение покажет готовый адрес — другое устройство сможет ходить в
интернет через ваш туннель.

### Прочее

- Material 3 с динамическими цветами, тёмная и светлая темы
- Импорт по ссылке, из буфера обмена и **QR-кода**
- Логи по серверам с пояснениями к типовым отказам
- Виджеты четырёх размеров, плитка в шторке, автозапуск при загрузке
- Настройки и подписки переживают переустановку через резервное копирование Android
- Языки: **English**, **Русский**, **Español**, **简体中文**

---

## Как пользоваться

1. Установите APK для своего ABI и выдайте разрешение на VPN.
2. Вкладка **Серверы** → кнопка **+**:
   - **Подписка** — ссылка на подписку;
   - **Ссылка** — одиночный сервер вида `vless://…`;
   - **QR-код** — сканирование камерой.
3. Нажмите на сервер, чтобы выбрать его, и включите VPN на главном экране.
4. Если появилось «трафик не идёт» — сервер вас не принял, выберите другой.

Кнопка со спидометром меряет задержку: у группы — сразу для всех серверов, у строки — для
одного.

---

## Сборка

### Требуется

| | версия |
| --- | --- |
| JDK | 17 |
| Android SDK | compileSdk 35 |
| Android NDK | только для пересборки ядра |
| Go + gomobile | только для пересборки ядра |

### Android Studio

1. **File → Open** и выберите **корень репозитория** (папку `SYBbox`), а не подпапку `app`.
2. Дождитесь Gradle Sync — зависимости подтянутся сами.
3. Готовое ядро уже лежит в `app/libs/sybbox_core.aar`, Go пересобирать не нужно.
4. Запуск: конфигурация **app**, вариант сборки **debug**.

Путь к SDK берётся из `local.properties` — Android Studio создаст его при первом открытии.

### Командная строка

```bash
./gradlew assembleDebug       # отладочная сборка
./gradlew assembleRelease     # релизные APK, по одному на ABI
./gradlew test                # юнит-тесты
```

APK появятся в `app/build/outputs/apk/release/`.

### Ядро (Go)

Нужно только если меняете само ядро или набор протоколов:

```bash
cd libcore && ./build.sh
```

Собирает `sybbox_core.aar` под arm64, arm и amd64 и кладёт в `app/libs/`.

### Проверка конфигураций

Конфигурации проверяются не только разбором, но и **сборкой настоящим ядром** — часть
ошибок возникает при построении транспортов, а не при чтении JSON:

```bash
./gradlew :app:testDebugUnitTest --tests '*ConfigMatrixDumpTest*'
tools/validate-configs.sh
```

Прогоняет каждый протокол и транспорт во всех сочетаниях с шифрованием.

### Версия

Берётся из git-тега, править ничего в файлах не нужно:

```bash
git tag -a v3.0.0 -m "SYBbox 3.0.0"
./gradlew assembleRelease
```

Тег `v3.0.0` даст `versionName 3.0.0` и имя файла `SYBbox-3.0.0-arm64-v8a.apk`.

---

## Структура проекта

```
SYBbox/
├── app/                        Android-приложение
│   ├── libs/sybbox_core.aar    собранное ядро Xray
│   └── src/
│       ├── main/assets/        geosite.dat и geoip.dat
│       ├── main/java/com/sybbox/
│       │   ├── core/           сборка конфигурации и мост к ядру
│       │   ├── data/           подписки, база, настройки
│       │   ├── domain/         модели и репозитории
│       │   ├── service/        VPN-сервис, виджеты, плитка, автозапуск
│       │   ├── ui/             экраны на Compose
│       │   └── di/             Hilt
│       ├── main/res/           ресурсы и переводы
│       └── test/               юнит-тесты
├── libcore/                    ядро на Go
│   ├── core/                   мост gomobile ↔ Android
│   ├── tun/                    TUN-стек на gvisor
│   ├── cmd/validate/           проверка конфигураций
│   └── build.sh                сборка AAR
└── tools/
    └── validate-configs.sh     запуск конфигураций настоящим ядром
```

### Что где искать

| Задача | Файл |
| --- | --- |
| Как строится конфигурация для ядра | `core/XrayConfigBuilder.kt` |
| Мост к Go-ядру | `core/XrayPlatform.kt` |
| Создание туннеля, маршруты, per-app | `service/SybBoxVpnService.kt` |
| Разбор подписок и определение формата | `data/parser/SubscriptionParser.kt` |
| Разбор конкретного протокола | `data/parser/VlessParser.kt` и соседние |
| Распаковка geosite и geoip | `core/GeoAssets.kt` |
| Измерение задержки | `core/PingTool.kt` |
| Пояснения к ошибкам в логе | `core/Diagnostics.kt` |
| Обход DPI | `core/DpiBypass.kt` |
| Виджеты | `service/SybBoxWidget.kt` |
| Экраны | `ui/home`, `ui/servers`, `ui/settings`, `ui/routing`, `ui/logs` |
| Отступы, скругления, общие компоненты | `ui/theme/Spacing.kt`, `ui/components/` |

### Как устроено ядро

У Xray нет своего TUN, поэтому пакеты из системного VPN-интерфейса разбирает собственный
стек на gvisor внутри `libcore/tun`, а готовые соединения отдаёт прямо в диспетчер Xray.
Промежуточного локального SOCKS между ними нет — это на один проход копирования меньше,
чем в обычной схеме на Android.

### Технологии

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · OkHttp ·
CameraX + ML Kit (QR) · Go + gomobile · Xray-core · gvisor

---

## Лицензия

[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0)

Основано на [Xray-core](https://github.com/XTLS/Xray-core) от Project X.
