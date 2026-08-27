<div align="center">

# SYBbox

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

### Протоколы

**VLESS** · **VMess** · **Trojan** · **Shadowsocks** · **Hysteria2** · **WireGuard**

Защита соединения — TLS и REALITY, с подменой отпечатка TLS (uTLS) и XTLS Vision.

### Транспорты

TCP · WebSocket · gRPC · HTTP Upgrade · mKCP · XHTTP

### Подписки

Формат определяется сам, вставлять можно что угодно из перечисленного:

- список ссылок — `vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`
- то же самое в base64
- sing-box JSON и v2ray JSON
- Clash YAML

Одна битая запись не роняет всю подписку — остальные серверы всё равно импортируются.
Автообновление по расписанию, имя подписки читается из заголовка `profile-title`.

### Проверка соединения

После подключения приложение **само загружает страницу через туннель** и сообщает, если
трафик не идёт.

Это важнее, чем кажется: обычный TCP-хендшейк до сервера проходит, даже если тот потом
откажется пропускать трафик. Отсюда классическое «пинг есть, а интернета нет». При
включённом автопереключении приложение само перейдёт к следующему рабочему серверу.

Задержка меряется по физической сети, берётся лучшая из нескольких попыток — чтобы первое
измерение не завышалось из-за пробуждения радиомодуля.

### Маршрутизация

- Режимы: всё через прокси, сбалансированный, только напрямую, свой
- Обход для доменов и адресов РФ и КНР
- Блокировка рекламы и трекеров
- Свои правила: домен, суффикс, ключевое слово, IP, порт, процесс, geosite, geoip
- По приложениям: **только выбранные** или **все, кроме выбранных**

### DNS

Раздельные резолверы для туннеля, прямых соединений и подключения к серверу.
Поддержка `udp://`, `tcp://`, `tls://`, `quic://`, `https://`, `h3://`. FakeIP.

### Обход блокировок

Фрагментация ClientHello с настраиваемой задержкой, фрагментация TLS-записей,
подмена отпечатка uTLS, REALITY.

### Прочее

- Material 3 с динамическими цветами, тёмная и светлая темы
- Импорт по ссылке, из буфера обмена и **QR-кода**
- Логи в реальном времени с пояснениями к типовым отказам
- Плитка в шторке быстрых настроек, автозапуск при загрузке
- Языки: **English**, **Русский**, **Español**, **简体中文**

---

## Как пользоваться

1. Установите APK для своего ABI и выдайте разрешение на VPN.
2. Вкладка **Серверы** → кнопка **+**:
   - **Подписка** — вставьте ссылку на подписку;
   - **Ссылка** — одиночный сервер вида `vless://…`;
   - **QR-код** — сканирование камерой.
3. Нажмите на сервер, чтобы выбрать его, и включите VPN на главном экране.
4. Если появилось «трафик не идёт» — сервер вас не принял, выберите другой из списка.

Кнопка со спидометром меряет задержку: у группы — сразу для всех серверов, у строки — для
одного.

---

## Сборка

### Требуется

| | версия |
| --- | --- |
| JDK | 17 |
| Android SDK | compileSdk 35 |
| Android NDK | только для сборки ядра |
| Go + gomobile | только для сборки ядра |

### Открыть в Android Studio

1. **File → Open** и выберите **корень репозитория** (папку `SYBbox`), а не подпапку `app`.
2. Дождитесь Gradle Sync — зависимости подтянутся сами.
3. Готовое ядро уже лежит в `app/libs/sybbox_core.aar`, пересобирать Go не нужно.
4. Запуск: конфигурация **app**, вариант сборки **debug**.

Путь к SDK берётся из `local.properties` — Android Studio создаст его при первом открытии.

### Из командной строки

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
git push origin v3.0.0
./gradlew assembleRelease
```

Тег `v3.0.0` даст `versionName 3.0.0` и имя файла `SYBbox-3.0.0-arm64-v8a.apk`.

---

## Структура проекта

```
SYBbox/
├── app/                        Android-приложение
│   ├── libs/sybbox_core.aar    собранное ядро Xray
│   ├── src/main/assets/        geosite.dat и geoip.dat
│   └── src/
│       ├── main/java/com/sybbox/
│       │   ├── core/           сборка конфигурации и мост к ядру
│       │   ├── data/           подписки, база, настройки
│       │   ├── domain/         модели и репозитории
│       │   ├── service/        VPN-сервис, плитка, автозапуск
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
| Как строится конфигурация для ядра | `core/ConfigBuilder.kt` |
| Разбор подписок и определение формата | `data/parser/SubscriptionParser.kt` |
| Разбор конкретного протокола | `data/parser/VlessParser.kt` и соседние |
| Запуск и остановка VPN | `service/SybBoxVpnService.kt` |
| Мост к Go-ядру, создание TUN | `core/SingBoxPlatform.kt` |
| Измерение задержки | `core/PingTool.kt` |
| Пояснения к ошибкам в логе | `core/Diagnostics.kt` |
| Обход DPI | `core/DpiBypass.kt` |
| Экраны | `ui/home`, `ui/servers`, `ui/settings`, `ui/routing`, `ui/logs` |
| Отступы и общие компоненты | `ui/theme/Spacing.kt`, `ui/components/` |

### Технологии

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · OkHttp ·
CameraX + ML Kit (QR) · Go + gomobile

---

## Лицензия

[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0)

Основано на [Xray-core](https://github.com/XTLS/Xray-core) от Project X.
