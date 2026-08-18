# Music Edge — анимированный градиентный визуализатор по краям экрана (Galaxy S24 Ultra)

Нативное Android-приложение на Kotlin. Пока играет разрешённое музыкальное
приложение, по периметру дисплея течёт светящаяся полоса из **шести цветов,
выбранных пользователем**. Когда музыка не играет — ничего не рисуется, не
считается и не опрашивается.

**Текущий статус:** реализованы этапы 1–5 (настройки палитры, Flow, Bass Pulse,
аудиоанализ, выбор приложений). Этап 6 — проверка и профилирование на реальном
S24 Ultra.

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
# поэтому его нужно один раз сгенерировать — или просто открыть проект
# в Android Studio, она сделает это сама при первой синхронизации:
gradle wrapper --gradle-version 8.9 --distribution-type bin

./gradlew :app:assembleDebug     # отладочная сборка
./gradlew :app:assembleRelease   # R8 + shrinkResources
```

Зависимости — только Compose, activity-compose, lifecycle-runtime-compose и
kotlinx-coroutines. Ни DI-фреймворка, ни Room, ни сети, ни аналитики, ни
библиотеки color picker (он написан на Canvas).

## Структура проекта

```
app/src/main/java/com/musicedge/visualizer/
├── audio/     AudioFrame, AudioSource, AudioEngine, VisualizerAudioSource
├── core/      VisualizerController, VisualizerState, VisualizerStatus
├── effects/   VisualizationEffect, GradientEdgeRenderer, GlowProfile, FlowEffect,
│              BassPulseEffect, EffectRegistry
├── media/     MediaEdgeListenerService, PlaybackDetector, MediaSessionObserver,
│              MediaNotificationObserver, MusicAppDetector, PlaybackSnapshot
├── overlay/   OverlayManager, EdgeVisualizerView, EdgeGeometry, EdgeStyle, RenderLoop
├── settings/  AppSettings, SettingsRepository
├── system/    PermissionManager, ScreenStateObserver, BootReceiver
└── ui/        MainActivity, screens/ (Home, MusicApps), components/, theme/, util/
```

## Визуальная модель

Однотонного режима нет: и `FlowEffect`, и `BassPulseEffect` рисуют один и тот же
замкнутый шестицветный градиент, различаются только реакцией на звук.

```
AppSettings.gradientColors (6 цветов, выбирает пользователь)
        ↓
VisualizerController → EdgeStyle
        ↓
VisualizationEffect → GradientEdgeRenderer
        ↓
SweepGradient + Matrix.setLocalMatrix(rotate)
        ↓
GlowProfile: 7 проходов ореола (гауссов спад) + основная линия,
все одним и тем же шейдером
```

Ключевые решения:

- **Один шейдер на все проходы.** Свечение не может «отстать» от линии по цвету:
  и halo, и сама линия берут цвет из одного `SweepGradient`, который вращается
  матрицей. Никакого фиксированного цвета glow.
- **Мягкий ореол без видимой границы.** Свечение рисуется несколькими stroke-проходами
  разной ширины, но альфа каждого подбирается так, чтобы **накопленная** непрозрачность
  шла по гауссову спаду `exp(-4.6·x²)` — на внешнем радиусе остаётся ~1% от пика, то
  есть меньше одного уровня 8-битной альфы. Поэтому «область» свечения не видна, а
  свет есть. Формула в `effects/GlowProfile.kt`, её же использует preview в настройках.
- **Без `BlurMaskFilter`.** Размытие маской уводит отрисовку с аппаратного
  конвейера; форму ореола задаёт профиль альфы, всё остаётся на GPU.
- **Замкнутая палитра.** Первый цвет повторяется последней остановкой
  (`0, 1/6, …, 5/6, 1`), поэтому при движении шва не видно.
- **Прозрачность у каждого цвета.** Цвета хранятся как ARGB, у каждого из шести своя
  альфа (слайдер Opacity в color picker, шахматный фон в свотчах). Альфа цвета
  умножается на общую яркость и на профиль свечения, поэтому полупрозрачный цвет
  остаётся полупрозрачным и в линии, и в её ореоле.
- **Скорость от времени, а не от кадров.** Поворот считается как
  `elapsedSeconds × 24°/с × animationSpeed`, поэтому при 30/60/120 FPS
  визуальная скорость одинакова.
- **Bass Pulse** умножает толщину, glow и яркость на огибающую баса
  (attack ≈ 40 мс, release ≈ 240 мс). Если пользователь выставил Glow = Off,
  бас его не включает; alpha никогда не превышает 255.

## Аудио

```
VisualizerAudioSource (android.media.audiofx.Visualizer, сессия 0)
        ↓ 20–30 обновлений в секунду
AudioEngine (интерполяция между обновлениями)
        ↓ раз в кадр
AudioFrame(amplitude, bass, mid, treble) — всё нормализовано 0..1
        ↓
BassPulseEffect
```

FFT не считается на каждый кадр: анализ идёт на частоте, заданной режимом
производительности, а рендер интерполирует последние значения. Полосы: bass
20–160 Гц, mid 160–2000 Гц, treble 2–8 кГц; нормализация — по медленно
спадающему пику, чтобы тихий трек тоже занимал весь диапазон.

Честное ограничение: с Android 10 платформа ограничивает сторонним приложениям
доступ к чужому аудиопотоку, и на части прошивок `Visualizer(0)` создаётся, но
отдаёт тишину. Приложение это измеряет (`AudioSource.isActive`) и пишет в
интерфейсе, что данных нет, вместо анимации по выдуманным числам. Разбор
вариантов и fallback — в [`docs/audio-capture.md`](docs/audio-capture.md).

## Определение источника музыки

```
PlaybackDetector
    ├── MediaSessionObserver      активные сессии от MediaSessionManager
    └── MediaNotificationObserver токен сессии из media-уведомления (fallback)
```

Обе ветки дают только кандидатов; состояние воспроизведения всегда берётся из
`MediaController`. Слияние — по токену сессии, поэтому один плеер, найденный
двумя путями, отслеживается один раз. Fallback нужен для плееров, которые
публикуют сессию нестандартно: media-уведомление с токеном у них всё равно есть.

Приложение, реально владевшее сессией, запоминается в настройках и появляется в
списке Music Apps, даже если оно не объявляет ни `MediaBrowserService`, ни
категорию `APP_MUSIC`.

## Разрешения

| Разрешение | Зачем |
|---|---|
| `SYSTEM_ALERT_WINDOW` | рисовать полосу поверх других приложений |
| Доступ к уведомлениям | единственный легальный способ вызвать `MediaSessionManager.getActiveSessions()`; плюс fallback по токену сессии |
| `RECORD_AUDIO` | требуется Visualizer API для сессии 0; запрашивается только при выборе Bass Pulse |
| `MODIFY_AUDIO_SETTINGS` | подключение `AudioEffect` |
| `RECEIVE_BOOT_COMPLETED` | пересоздать привязку listener-сервиса после перезагрузки |

Из уведомлений читается ровно одно поле — `Notification.EXTRA_MEDIA_SESSION`.
Ни текст, ни заголовки, ни иконки не читаются. Аудио не записывается и не
сохраняется: результат FFT живёт в памяти на время кадра.

## Энергоэффективность

- нет музыки → нет overlay, нет кадров, нет Visualizer, нет FFT;
- экран выключен → overlay снимается сразу, аудио останавливается;
- кадры даёт `Choreographer` с пропуском vsync под 30/60/120, без `while(true)`;
- аудио стартует только для эффектов, которым оно нужно (`EffectRegistry.requiresAudio`).

## Что проверить на Galaxy S24 Ultra

1. Выдать overlay и notification access, включить переключатель.
2. Spotify → Play: градиент появляется по периметру и **течёт**; шва не видно.
3. Подвигать Animation speed / Glow / Thickness / Brightness во время
   воспроизведения — меняется сразу, overlay не перезапускается.
4. Поменять любой из шести цветов в Gradient colors — линия обновляется сразу.
   Уменьшить Opacity у одного цвета: этот участок градиента должен стать
   полупрозрачным, остальные — нет.
5. Glow на максимум: у свечения не должно быть заметной границы или «полосы» —
   только плавное затухание от линии наружу.
6. Свернуть плеер, открыть другое приложение — визуализатор продолжает работать.
7. Music Apps: включить LANE (или другой плеер), запустить в нём музыку — линия
   должна появиться; выключить его — не должна.
8. Bass Pulse: выдать audio permission. Если полоса пульсирует по басу — Visualizer
   API работает; если появилась плашка «No audio data…» — на этой прошивке
   session 0 отдаёт тишину, включается план из `docs/audio-capture.md`.
9. Performance: 30 / 60 / 120 FPS — визуальная скорость потока не должна меняться.
10. Экран выключить/включить во время воспроизведения — линия возвращается.
11. Касания в зоне линии проходят в приложение под ней.

## Дорожная карта

| Этап | Содержание | Статус |
|---|---|---|
| 1 | Настройки: 6 цветов, скорость, glow | **готов** |
| 2 | FlowEffect: движущийся градиент + движущееся свечение | **готов** |
| 3 | BassPulseEffect поверх Flow | **готов** |
| 4 | VisualizerAudioSource, FFT, bass/mid/treble/amplitude | **готов** |
| 5 | Экран выбора приложений + fallback по media-уведомлениям | **готов** |
| 6 | Проверка и профилирование на реальном устройстве | требует устройства |
| — | Wave / Spectrum / Breathing / палитра из обложки | далее |

## Приватность

Нет интернет-разрешения, нет аналитики, нет аккаунтов, нет облака. Настройки —
локальный `SharedPreferences`. Аудиоданные не сохраняются.
