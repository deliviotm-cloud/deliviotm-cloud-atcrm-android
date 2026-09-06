# AT CRM

Нативные клиенты **AT CRM**: Android (`app/`) и iOS (`ios/`).  
Пакет / bundle: `tm.deliviotm.atcrm`.  
API по умолчанию: `https://crm.deliviotm.com/api`.

Текущая версия в репозитории: **1.6.44** (`versionCode` / build 204).

## Ссылка для агентов (Android и iOS)

Один Git на обе платформы. iOS-агент клонирует **этот** репозиторий и открывает Xcode-проект здесь же.

```
https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android.git
```

- Репо: https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android
- iOS / Xcode: [`IOS.md`](IOS.md) · проект [`ios/ATCRM.xcodeproj`](ios/ATCRM.xcodeproj)
- Манифест: [`.cursor/ios-source.json`](.cursor/ios-source.json)

Новые чаты Cursor открывайте **из этого репозитория**. В GitHub App репо должен быть подключен (он приватный).

## Зачем Git

Раньше исходники жили только в `/tmp` облачного агента и пропадали после сброса машины.  
Этот репозиторий — постоянная копия кода. Сборки APK можно хранить отдельно, но **источник правды — Git**.

## Сборка

Нужен Android SDK (API 35) и JDK 17.

```bash
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/ATCRM-<versionName>.apk` (например `ATCRM-1.6.42.apk`).

Firebase Cloud Messaging в клиент не входит, пока нет `google-services.json` (этот файл в Git не кладём).

## iOS / Xcode

```bash
git clone https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android.git
cd deliviotm-cloud-atcrm-android
git pull
open ios/ATCRM.xcodeproj
```

После правок в Xcode агент коммитит и пушит в этот же remote. Отдельный iOS-репозиторий не нужен.

## GitHub

Репозиторий уже есть и приватный: `deliviotm-cloud/deliviotm-cloud-atcrm-android`.  
В `register` клиентский код не кладём.

## Ветки

- `main` — стабильная сборка, которую можно ставить.
- Правки агента: `cursor/<краткое-имя>-e295`.
