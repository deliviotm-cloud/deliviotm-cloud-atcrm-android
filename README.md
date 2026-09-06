# AT CRM Android

Нативное Android-приложение **AT CRM** (`tm.deliviotm.atcrm`).  
API по умолчанию: `https://crm.deliviotm.com/api`.

Текущая версия в Git: **1.6.41** (`versionCode` 201), тег `v1.6.41`.

Репозиторий: [deliviotm-cloud/deliviotm-cloud-atcrm-android](https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android) (приватный).

## Git — источник правды

Исходники живут в этом репозитории, не на диске облачной VM. После любой правки агент **сам** делает commit и push. Не оставлять изменения только локально и не просить пользователя пушить вручную.

```bash
git checkout -b cursor/<краткое-имя>-cf04
# …правки…
git add -A
git commit -m "…"
git push -u origin HEAD
```

Дальше — PR в `main`. Стабильная сборка, которую можно ставить, только на `main`.

Новые чаты Cursor открывайте из **этого** репозитория.

## Сборка

Нужен Android SDK (API 35) и JDK 17.

```bash
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` и `google-services.json` в Git не кладём. Firebase Cloud Messaging в клиент не входит, пока нет `google-services.json`.
