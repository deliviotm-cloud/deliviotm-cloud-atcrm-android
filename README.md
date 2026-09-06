# AT CRM Android

Нативное Android-приложение **AT CRM** (`tm.deliviotm.atcrm`).  
API по умолчанию: `https://crm.deliviotm.com/api`.

Текущая версия в репозитории: **1.6.41** (`versionCode` 201).

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

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Firebase Cloud Messaging в клиент не входит, пока нет `google-services.json` (этот файл в Git не кладём).

## Подключить GitHub (один раз)

Облачный агент Cursor сейчас привязан к публичному `deliviotm-cloud/register` и **не может** создать новый репозиторий сам.

1. На GitHub создайте **приватный** репозиторий `deliviotm-cloud/atcrm-android` (пустой, без README).
2. В Cursor: GitHub App → добавьте этот репозиторий в установку (иначе агент не сможет пушить).
3. Затем:

```bash
cd /path/to/atcrm-android
git remote add origin git@github.com:deliviotm-cloud/atcrm-android.git
git push -u origin main
git push origin v1.6.41
```

Новые чаты Cursor открывайте **из `atcrm-android`**, не из `register`. В `register` Android-код не кладём: репозиторий публичный.

## Ветки

- `main` — стабильная сборка, которую можно ставить.
- Правки агента: `cursor/<краткое-имя>-e295`.
