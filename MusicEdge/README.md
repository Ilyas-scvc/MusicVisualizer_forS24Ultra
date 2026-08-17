# Music Edge — визуализатор по краям экрана (Galaxy S24 Ultra)

Нативное Android-приложение на Kotlin: пока играет музыка, по периметру дисплея
светится тонкая линия. Когда музыка не играет — приложение не рисует, не считает
и не опрашивает систему.

**Текущий статус: Этап 1 (MVP).** Определение медиасессии, overlay по периметру,
статическая цветная линия, плавное появление/затухание, экран настроек.
FFT и остальные эффекты — следующие этапы (см. «Дорожная карта»).

## Требования

| Параметр | Значение |
|---|---|
| Целевое устройство | Samsung Galaxy S24 Ultra (One UI, Android 14+) |
| `minSdk` | 34 |
| `compileSdk` / `targetSdk` | 35 |
| JDK | 17 |
| Gradle | 8.9 |
| AGP / Kotlin | 8.7.3 / 2.1.0 |
| ABI | arm64-v8a |

Сборка:

```bash
# Gradle wrapper в репозиторий не закоммичен (двоичный gradle-wrapper.jar),
# поэтому его нужно один раз сгенерировать - или просто открыть проект
# в Android Studio, она сделает это сама при первой синхронизации:
gradle wrapper --gradle-version 8.9 --distribution-type bin

./gradlew :app:assembleDebug     # отладочная сборка
./gradlew :app:assembleRelease   # R8 + shrinkResources
```

Зависимости — только Compose, activity-compose, lifecycle-runtime-compose и
kotlinx-coroutines. Ни DI-фреймворка, ни Room, ни сети, ни аналитики.

## Структура проекта

```
app/src/main/java/com/musicedge/visualizer/
├── audio/         AudioFrame, AudioSource, AudioEngine   — шов для FFT (этап 2)
├── core/          VisualizerController, VisualizerState, VisualizerStatus
├── effects/       VisualizationEffect, StaticEffect, EffectRegistry
├── media/         MediaEdgeListenerService, MediaSessionObserver, MusicAppDetector
├── overlay/       OverlayManager, EdgeVisualizerView, EdgeGeometry, RenderLoop, EdgeStyle
├── settings/      AppSettings, SettingsRepository
├── system/        PermissionManager, ScreenStateObserver, BootReceiver
└── ui/            MainActivity, screens/HomeScreen, components/, theme/
```

## Разрешения

| Разрешение | Зачем |
|---|---|
| `SYSTEM_ALERT_WINDOW` | нарисовать линию поверх других приложений |
| Доступ к уведомлениям (`BIND_NOTIFICATION_LISTENER_SERVICE`) | единственный легальный способ для стороннего приложения вызвать `MediaSessionManager.getActiveSessions()` |
| `RECEIVE_BOOT_COMPLETED` | попросить систему пересоздать привязку listener-сервиса после перезагрузки |

Содержимое уведомлений не читается: `onNotificationPosted` и `onNotificationRemoved`
намеренно пустые. Разрешение используется только как токен доступа к медиасессиям.

`RECORD_AUDIO` и `MODIFY_AUDIO_SETTINGS` **не** объявлены — они появятся только на
этапе 2 вместе с реальным анализом звука.

## Архитектура MVP

```
NotificationListenerService (MediaEdgeListenerService)
        │  системная привязка = живой процесс без foreground-уведомления
        ▼
VisualizerController ── state machine
        ├── MediaSessionObserver   какое приложение реально играет
        ├── ScreenStateObserver    ACTION_SCREEN_ON / OFF
        ├── SettingsRepository     StateFlow<AppSettings>
        ├── AudioEngine            пустой шов, наполняется на этапе 2
        └── OverlayManager ──► EdgeVisualizerView ──► VisualizationEffect
                                      └── RenderLoop (Choreographer)
```

Состояния:

```
DISABLED → IDLE → WAITING_FOR_MEDIA → ACTIVE → PAUSED_FADE → WAITING_FOR_MEDIA
                                          └──► SCREEN_OFF ──► ACTIVE
```

- `WAITING_FOR_MEDIA` — ничего не рисуется, кадры не запрашиваются, работают только
  событийные колбэки медиасессий;
- `PAUSED_FADE` — затухание 700 мс, после него overlay удаляется из WindowManager;
- `SCREEN_OFF` — overlay снимается сразу, без затухания: показывать нечего.

Дополнительно: статическая линия после появления **останавливает** цикл кадров —
`StaticEffect.needsContinuousFrames()` возвращает `false`, окно просто хранит уже
отрисованный кадр. Постоянных 60 FPS в MVP нет вообще.

## Проверка на реальном устройстве

1. Установить, открыть, выдать оба разрешения, включить переключатель.
2. Spotify → Play → в течение долей секунды появляется линия по периметру.
3. Свернуть Spotify, открыть Telegram — линия остаётся (источник определяется по
   медиасессии, а не по приложению на экране).
4. Pause → линия плавно гаснет ~700 мс и overlay снимается.
5. Открыть YouTube (нет в whitelist) → линия не появляется.
6. Выключить экран во время воспроизведения → включить: линия возвращается.
7. Касания в зоне линии должны проходить в приложение под ней.

## Известные ограничения Android / One UI

- **Overlay-разрешение** выдаётся только вручную в системных настройках; на One UI
  оно называется «Показ поверх других приложений».
- **`FLAG_NOT_TOUCHABLE`** делает окно полностью прозрачным для касаний — обратная
  сторона в том, что окно не может обрабатывать жесты вообще (для этого приложения
  это желаемое поведение).
- **Полноэкранные приложения и защищённый контент** (например, воспроизведение с
  DRM в полноэкранном режиме) могут перекрывать overlay — это поведение системы,
  обойти его без системных прав нельзя.
- **Android Visualizer API** (этап 2) на Android 10+ не даёт стороннему приложению
  общий выходной микс. Подробный разбор и план fallback — в
  [`docs/audio-capture.md`](docs/audio-capture.md). Это будет проверено на реальном
  S24 Ultra до того, как в UI появятся эффекты, зависящие от звука.

## Дорожная карта

| Этап | Содержание | Статус |
|---|---|---|
| 1 | Детект медиасессии, overlay, статическая линия, fade in/out | **готов** |
| 2 | AudioEngine + реальный источник звука, Bass Pulse | следующий |
| 3 | Smoothing, 30/60/120 FPS, Rainbow / Gradient / Wave / Spectrum | |
| 4 | Экран Music Apps (whitelist уже работает, нужен UI выбора) | |
| 5 | Цвета из обложки альбома | |
| 6 | Оптимизация батареи и профилирование | |

## Приватность

Нет интернет-разрешения, нет аналитики, нет аккаунтов, нет облака. Настройки лежат
в локальном `SharedPreferences`. Аудиоданные (когда появятся) существуют только в
оперативной памяти на время отрисовки кадра и никуда не записываются.
