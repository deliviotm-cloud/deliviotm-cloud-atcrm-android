# AT CRM iOS — Git для агента Xcode

Источник правды тот же, что у Android: **этот репозиторий**.  
Новый чат Cursor для iOS открывайте **из этого GitHub-репо**, не из `register`.

## Ссылка, которую клонирует iOS-агент

```
https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android.git
```

- Сайт репо: https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android
- SSH: `git@github.com:deliviotm-cloud/deliviotm-cloud-atcrm-android.git`
- Машинный манифест: [`.cursor/ios-source.json`](.cursor/ios-source.json)
- Проект Xcode: [`ios/ATCRM.xcodeproj`](ios/ATCRM.xcodeproj)

Репозиторий **приватный**. В Cursor → GitHub App этот репо должен быть в установке, иначе агент не склонирует и не запушит.

## Как забирать обновления и выкатывать в Xcode

```bash
git clone https://github.com/deliviotm-cloud/deliviotm-cloud-atcrm-android.git
cd deliviotm-cloud-atcrm-android
git fetch origin
git checkout cursor/ios-source-cf04   # пока main без этих правок — брать эту ветку
git pull origin cursor/ios-source-cf04
open ios/ATCRM.xcodeproj
```

После правок в Xcode агент сам коммитит и пушит (как Android-агент):

```bash
git add ios
git commit -m "Describe the iOS change."
git push -u origin HEAD
```

Версия iOS должна совпадать с Android в `.cursor/ios-source.json`: сейчас **1.6.43** (build **203**).

API: `https://crm.deliviotm.com/api`  
Ориентир UI: мобильный сайт `https://crm.deliviotm.com` и Android-код в `app/`.
