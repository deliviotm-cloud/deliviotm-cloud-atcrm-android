# AT CRM — инструкции агентам

Один Git на Android и iOS.

- Clone: `https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android.git`
- Web: https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android
- API: `https://crm.deliviotm.com/api`
- Манифест для iOS/Xcode: `.cursor/ios-source.json`
- Как работать с Xcode: `IOS.md`

## Android-агент

Правки в `app/`. Сборка: `./gradlew assembleDebug` → `ATCRM-<version>.apk`.  
После правок: commit + push в ветку `cursor/<имя>-cf04`.

## iOS-агент

Правки в `ios/`. Открывать `ios/ATCRM.xcodeproj` в Xcode.  
Перед работой: `git fetch` и `git pull` текущей ветки.  
После работы в Xcode: commit + push сюда же — не создавать отдельный репозиторий, пока пользователь сам его не заведёт.
