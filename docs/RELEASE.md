# Выпуск APK/AAB для RuStore и Google Play

## Важно о конфликтах при обновлении

Android разрешает обновить установленный пакет только если совпадают **applicationId** и сертификат подписи, а `versionCode` новой сборки больше. GitHub-hosted runners генерируют временные debug-ключи: их APK между разными запусками могут иметь разные подписи и не обновляться поверх друг друга.

**Перед удалением старой версии:** Настройки → «Экспортировать графики в JSON», сохранить файл вне приложения. После установки новой версии: Настройки → «Восстановить графики из JSON». Если подписи отличаются, обновление поверх существующей версии не получится независимо от версии Android.

## Релизный ключ (генерирует владелец, не передавать ни в чат, ни в репозиторий)

На компьютере с JDK:

```powershell
keytool -genkeypair -v -keystore workshift-release.jks -alias workshift -keyalg RSA -keysize 4096 -validity 10000
```

Сохранить `workshift-release.jks` в надёжном месте и сделать резервную копию **за пределами репозитория**. В GitHub → Settings → Secrets and variables → Actions → New repository secret добавить:

- `WORKSHIFT_KEYSTORE_BASE64` — Base64 всего `.jks`. В PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path ".\\workshift-release.jks")))`. Не публиковать результат.
- `WORKSHIFT_KEYSTORE_PASSWORD` — пароль хранилища.
- `WORKSHIFT_KEY_ALIAS` — `workshift` (или выбранный alias).
- `WORKSHIFT_KEY_PASSWORD` — пароль ключа.

После этого GitHub Actions → **Build signed release for stores** → Run workflow.
Результат в Artifacts: `app-release.apk` и `app-release.aab`. Релизная сборка проверяет сертификат командой `apksigner verify` и запускает unit-тесты.

**Не публиковать debug-APK в магазине.** Для следующих обновлений использовать **тот же ключ** и увеличить `versionCode` в `app/build.gradle.kts`. Не заменять ключ без плана миграции. При использовании Google Play App Signing учитывайте отличие ключа подписи приложений от upload key, иначе кросс-магазинные обновления могут быть несовместимыми.

### Package name

Текущий `applicationId = "com.example.workshift"` оставлен без изменения для сохранения возможности локальных обновлений существующих установок. Для публичного выпуска лучше заранее утвердить уникальный идентификатор приложения и больше его не менять. Изменение идентификатора делает приложение **другим пакетом** и не переносит его локальные данные само собой — используйте экспорт/импорт.

## Проверки перед публикацией

1. Пройти сценарии в `docs/QA.md`, отдельно протестировать Android 10/13/14/15 и Xiaomi/HyperOS.
2. Проверить работу звука, выбранной мелодии, вибрации, блокировки экрана и отмены будильника.
3. Объяснить пользователю специальные разрешения: `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`.
4. Указать предназначение foreground service в формах магазина; для Play Console могут потребоваться обоснования/демонстрация.
5. Подготовить политику конфиденциальности, страницу приложения, скриншоты, возрастную категорию. Сейчас графики сохраняются локально, регистрация не требуется.
6. Загрузить **подписанный релиз**, а не Actions debug artifact.

Официальные руководства:
- https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication
- https://www.rustore.ru/help/en/developers/publishing-and-verifying-apps/app-publication/apk-signature
- https://developer.android.com/studio/publish/app-signing
