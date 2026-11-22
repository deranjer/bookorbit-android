# Independent Android client

This is an AGPL-3.0 fork of `deranjer/bookorbit-android`, not the official
BookOrbit application. Keep upstream attribution and compatible engine licenses.
Do not import production databases, secrets or books into development fixtures.

## Актуальный план после тестирования пользователем

Обновлено 2026-11-23. Этот раздел задаёт текущие приоритеты; разделы ниже —
история уже сделанных инкрементов, а не заявление о готовности всего приложения.

### Исходное состояние и замечания

- Текущий собранный и автоматически проверенный срез — **0.9.7**, ветка
  `feat/independent-android-client`: 447 JVM-тестов на вариант, 21 JS-проверка,
  5 SQL-проверок и 2 проверки локализации; lint без ошибок. Это не полная проверка
  на физическом устройстве.
  Ранее пользователю передана 0.9.1 (`617f904`); её APK сохранён отдельно.
- Пользователь тестирует APK. Производственный сервер и его данные агент не меняет.
- **Ошибка сценария выбора:** у пользователя одна аудиокнига состоит из 75
  аудиофайлов. 75 чекбоксов неприемлемы: выбирать нужно издание, а не каждый трек.
- **Замечание к дизайну:** интерфейс выглядит устаревшим и слишком шаблонным.
  Нужен современный целостный дизайн, а не только перекрашивание стандартных кнопок.
- **Баг читалки:** в тёмной теме видны границы/прямоугольники страниц, фон страницы
  отличается от окружающего фона. Пока это подтверждённое пользователем проявление,
  но причина и исправление ещё не проверены.
- **Баг навигации:** повторное нажатие уже выбранной вкладки не возвращает на её
  начальный экран, если внутри открыт экран книги или другой вложенный экран.
- **Ошибка приоритизации разработки:** read-along и AI были обещаны, но пока не
  реализованы. Не заменять их бесконечными второстепенными доработками офлайна.

### Порядок выполнения

1. Исправить выбор изданий, тёмную тему и возврат в корень вкладки (P0).
2. Реализовать и проверить первый настоящий read-along (P1).
3. Провести аудит совместимости AI и реализовать доступные серверные функции (P2).
4. Выполнять полный редизайн отдельными проверяемыми экранами, не ставя весь
   редизайн условием начала read-along/AI. Обновление зависимостей также не должно
   бесконечно откладывать пользовательские функции.

### P0. Издания вместо технических файлов

- [x] В загрузках показывать «Текстовое издание», «Аудиокнига» и выбор обоих.
- [x] Все 75 треков одной аудиокниги — **одна карточка и одно действие**.
      Внутри сохраняются точные fileId и корректный порядок аудио, а не один
      произвольно выбранный трек. Список треков уместен в плеере, не в выборе загрузки.
- [ ] Для аудио показывать рассказчика/чтеца, длительность, общий размер и число
      треков. Не выдумывать неизвестные длительности или размеры.
- [x] Частично скачанное аудио показывать как одно частично доступное издание;
      подтверждение скачивает все недостающие треки, не требует выбирать их вручную.
- [x] Существующие скачанные издания сохранять при добавлении других. Просмотр
      библиотеки, синхронизация и открытие окна выбора ничего не скачивают сами.
- [x] В чтении и вкладке книги использовать «Издания», без списка из 75 MP3.
      Разные текстовые издания по-прежнему открываются по точному fileId, с раздельными
      позициями. Формат/расширение файла само по себе не доказывает тождество изданий.
- [x] Не выдумывать разделение нескольких аудиоозвучек по имени/размеру файла:
      текущая клиентская модель имеет одни audioMetadata на серверную книгу.
      Если сервер предоставляет явные идентификаторы отдельных аудиоизданий,
      сначала добавить их в контракт и только потом разделять озвучки.
- [ ] Регрессии: текст + 75 треков дают два выбора; выбор аудио включает все 75 ID;
      74 скачанных трека дают один недостающий; одинаковые имена изданий не смешивают
      ID; существующие загрузки и позиции не теряются.

**Срез 0.9.3:** `BookEditions`, группировка аудио, вкладка изданий и bottom sheets
собраны и покрыты тестами на 75 треков. Детальное состояние остальных работ и
границы проверки описаны в конце плана. Проверка на физическом устройстве ещё
не выполнена; эмуляторные результаты — ниже.

### P0. Единый фон тёмной читалки

- [ ] Воспроизвести баг в App/native EPUB-читалке: пагинация, прокрутка, смена главы,
      поворот экрана, несколько колонок и переключение светлой/тёмной темы на лету.
- [ ] Сопоставить фон Compose, WebView, внешнего HTML, контейнера Foliate,
      paginator, HTML/body внутри книги и межстраничных полей.
- [ ] Проверить влияние встроенных CSS книги, рамок/теней и автоматического
      затемнения Android WebView. Это гипотезы, а не уже установленная причина.
- [ ] Применять выбранный фон ко всей поверхности читалки, включая пустые поля и
      зазоры. Не оставлять светлые/отличающиеся прямоугольники вокруг страницы.
- [ ] Не ломать иллюстрации, SVG, комиксы, выделения и читаемость ссылок глобальной
      инверсией либо непрозрачным фоном каждого вложенного элемента.
- [x] Добавить регрессии для CSS/палитры и визуальную проверку на реальном
      WebView. Одна успешная Kotlin-сборка не подтверждает исправление внешнего вида.

### P0. Повторное нажатие вкладки возвращает в её корень

- [ ] Нажатие **уже выбранной** вкладки всегда открывает её стартовый экран:
      Home → старт Home, Library → старт Library, Search → старт Search и т. д.,
      а не обязательно Home для всех вкладок.
- [ ] Если внутри вкладки открыта книга, автор/серия или другой вложенный экран,
      повторное нажатие снимает этот вложенный back stack до корня вкладки.
      Само нажатие не должно игнорироваться из-за того, что вкладка уже выделена.
- [ ] Если пользователь уже в корне, действие идемпотентно: не создаёт дубликат
      корневого экрана и не добавляет новые записи в историю Back.
- [ ] Проверить обычную нижнюю панель, navigation rail, двухпанельный режим и
      настраиваемый состав вкладок. При возврате в корень закрывать вложенную
      карточку/панель, но не останавливать воспроизведение и не терять мини-плеер.
- [ ] Отдельно сохранить корректное обычное переключение между разными вкладками;
      требование принудительного возврата относится именно к повторному нажатию
      текущей вкладки. Не подменять его новым поведением всех переходов.
- [ ] Регрессии: Home → книга → Home; Library → автор → книга → Library;
      Search → результат/книга → Search; повторные нажатия в корне; системный Back
      после возврата не открывает только что сброшенную вложенную книгу.

### P1. Read-along

- [x] Создать синтетический EPUB 3 с SMIL, встроенным аудио и несколькими текстовыми
      фрагментами/главами. Отдельный EPUB + произвольные MP3 без разметки не считать
      автоматически синхронизированным read-along.
- [x] Проверить унаследованный движок: в Foliate уже есть `MediaOverlay`,
      `getMediaOverlay()` и `startMediaOverlay()`. Это инфраструктура библиотеки,
      **не готовая функция Android-клиента**.
- [x] Подключить определение поддержки и команды управления в JS/native bridge.
- [x] Первый рабочий срез: явный запуск, пауза/продолжение, скорость, подсветка
      текущего фрагмента и следование страницы за текстом, без второго параллельного
      аудиоплеера. Ошибку аудио показывать отдельно от ошибки чтения книги.
- [ ] Проверить переходы по фрагментам и главам, ручное перелистывание, конец книги
      и секции без overlay; устранить обнаруженные дефекты движка перед включением.
- [x] Доказать работу в авиарежиме для явно скачанного EPUB со встроенным аудио.
      Проверены Android WebView/переходы состояния/подсветка на тихой фикстуре;
      слышимый звук на физическом устройстве ещё не проверен.
- [ ] Сохранять точную текстовую позицию и аудиокоординату отдельно. Возобновление,
      статистика и переключение устройств не должны создавать два чтения одной
      активности или дублировать события, как в ранее исправленных случаях BookBridge.
- [ ] Фоновое воспроизведение, MediaSession, Bluetooth/Android Auto и восстановление
      после остановки процесса — отдельный этап интеграции с Media3. Наличие HTML
      Audio в WebView не означает, что эти возможности уже реализованы.
- [ ] Отдельно исследовать серверный read-along для внешних аудиофайлов и текстов:
      использовать реальное выравнивание/контракт сервера, не угадывать соответствие
      текста и звука по процентам.

### P2. AI и совместимость с сервером

- [ ] Сопоставить реальные функции и DTO BookOrbit 3.9.0 с возможностями Android:
      Story Guides, справка по персонажам/местам/терминам, пересказы и спойлерные
      ограничения, помощь при чтении/потоковые ответы, распознавание фото — наличие
      каждого серверного маршрута и разрешений сначала подтвердить.
- [ ] Отдельно проверить JWT, права библиотек, настройки AI-провайдеров, streaming,
      фоновые задания и требования app attestation. Не путать запрет независимого
      приложения с отсутствием настроенного провайдера или пользовательских прав.
- [ ] Добавить явную индикацию поддерживаемых функций/причины недоступности.
      Не показывать работающими кнопки, которые заведомо получают отказ сервера.
- [x] Если нужна серверная совместимость, подготовить явно согласуемое opt-in
      решение для самостоятельного клиента, сохранив аутентификацию и авторизацию.
      Не выдавать себя за официальное приложение и не отключать молча защиту.
      Производственный сервер не патчить без отдельного разрешения.
- [ ] Реализовывать подтверждённые серверные AI-функции по одной, с контрактными
      тестами на локальном сервере и корректной отменой/ошибками/потоковым ответом.
- [ ] Учитывать ограничения Story Guides в 3.9.0: английские текстовые книги, не
      PDF/комиксы/аудиокниги. Apple Intelligence не объявлять доступной на Android;
      для вопросов общего вида нужен отдельный подтверждённый backend/провайдер.
- [ ] Не отправлять пользовательские книги внешнему AI-провайдеру без явного
      действия пользователя и понятного объяснения обработки данных.

### Дизайн и остальные обязательные работы

- [ ] Целостная визуальная система: типографика, сетка/отступы, палитра светлой и
      тёмной темы, формы карточек, иерархия действий, контрастность и состояния.
- [ ] Переработать библиотеку/поиск, карточку книги, выбор изданий, плеер, загрузки
      и настройки; обложка и описание важнее технических метаданных файлов.
- [ ] Использовать понятные названия изданий, чтеца, язык и формат там, где данные
      действительно доступны. Избавиться от технических ID/расширений как основных
      названий. Проверить 75-трековую книгу без длинного технического списка.
- [ ] Проверять внешний вид скриншотами/на устройстве, длинными русскими названиями,
      увеличенным шрифтом, узким экраном, планшетом и доступностью. Сборка и lint
      не являются визуальной приёмкой. Не называть частичную смену цветов редизайном.
- [ ] Полная русская локализация отдельным завершённым пакетом, без отключения
      MissingTranslation для сокрытия неполного перевода.
- [ ] Обновить Kotlin/AGP/Gradle/Compose/Media3/Room/Hilt и прочие зависимости
      согласованными этапами, с отдельными сборками и регрессиями после каждого.
- [ ] Решить офлайн-чтение CBR/CB7 либо явно оставлять ограничение; сейчас оно
      требует серверного рендера страниц, в отличие от локального CBZ.
- [ ] Реальное устройство: OIDC, обновление APK/миграции Room, SAF и отзыв прав,
      большие загрузки/докачка, процесс/сеть, фоновое аудио и Bluetooth/Auto.
      Чеклист: `docs/DEVICE_TESTING.md`. Модель телефона/версия Android пока не записаны.

### Правила завершения инкремента

1. Сначала регрессии на конкретный пользовательский сценарий, затем сборки двух
   вариантов и lint; отдельно JS/CSS, SQLite и локальные HTTP-контракты по необходимости.
2. Не смешивать в отчёте «унаследовано», «написано», «автоматически проверено» и
   «проверено на телефоне». Отмечать незавершённые ветви и реальные ограничения.
3. Не менять исходники во время финального проверочного прогона. После любых
   изменений кода повторять затронутые проверки, чтобы результаты соответствовали APK.
4. Сохранять APK предыдущего тестового инкремента, увеличивать versionCode,
   проверять подпись для обновления поверх установленного приложения.
5. Не менять TrueNAS, BookBridge, пользовательские книги и историю в ходе разработки;
   локальные тесты используют только синтетические EPUB/аудио/комиксы.

## First working increment

1. Reproducible Android build, separate installation identity and OIDC redirect.
2. Browse libraries, search, series/authors, and book details with formatted descriptions.
3. Explicit offline download choices: ebook, audiobook, or both. Opening or syncing
   the library must never start a permanent download.
4. Streaming/background audio, native reader, and retained offline content.
5. Contract checks against an isolated BookOrbit 3.9.0 instance and JVM regressions.

## Following increments

- Preserve exact edition/file identity from the book page into the reader and download queue.
- Versioned offline writes: acknowledging an older write must not clear a newer one.
- Read-along engine spike on synthetic EPUB/SMIL fixtures, online and offline.
  A text locator and a media position are separate coordinates of one activity.
- Server capabilities and clear attestation errors, followed by an opt-in companion
  server patch trusting our release signing identity. Never impersonate the official app.
- Story Guides, spoiler-bounded entries and recaps, streamed reading help and photo scans.
  BookOrbit 3.9.0 currently limits Story Guide generation to English text books.
- No claim of Apple Intelligence support on Android. General question answering
  needs a separately designed Android/server implementation.

## Validation boundaries

An inherited feature is not automatically a verified feature. Track build, unit,
HTTP contract and physical-device validation separately. Native codecs, process
death, long downloads, background playback and offline conflicts need device tests.

Production TrueNAS, BookBridge and the user's reading history are outside this project.

## First increment status (2026-11-22)

- Upstream baseline: 335 JVM tests passed; fdroid debug APK built in Docker.
- Independent package `io.github.monkecode.bookorbitclient` (debug suffix `.debug`)
  and callback `bookorbitclient://oauth2-callback`; local Compose allowlist matches.
- Explicit per-file offline choices, persisted independently of the book metadata;
  retries retain the exact selected IDs and legacy plans remain readable.
- Audio manifest mapping uses authoritative `fileId`; no ambiguous order-based
  substitution. Legacy manifests support unique format/size matches only.
- Local HTTP smoke passed on 3.9.0: password setup/login, list, descriptions,
  paired EPUB/two MP3s, file progress, audio manifest file IDs, Range responses
  and byte comparison against original fixtures.
- The initial 0.9.0 increment required removing an offline download to change its
  selection; the additive 0.9.1 follow-up below removes that limitation.
- Toolchain/dependency upgrade is intentionally separate from the baseline and file
  selection change. Current successful builds still use inherited versions.
- No emulator/device, OIDC provider, long-running audio or process-death validation yet.

## Identity and progress follow-up

- Book detail and the Files tab open an explicit file ID. Multi-edition books get
  a chooser, with offline files identified and listed first. The selected ID reaches
  native EPUB/PDF/comic engines and the server reader; a missing file fails closed.
- Reader and audio writes have unique persisted write IDs. Acknowledgements clear
  only the exact snapshot sent, and network pushes are serialized within each
  repository to prevent local requests overtaking one another.
- PDF/comic page numbers survive queued retries. Database migrations add nullable
  page coordinates and defaulted write IDs without deleting existing rows.
- Reader cache publishes files atomically only after successful transfer/length
  checks; the new cache namespace is separated by server data scope and account.
- Regression coverage includes delayed acknowledgements, same-millisecond writes,
  secondary edition selection, mismatched reading engine, failed partial cache
  writes and offline page-number retries. Device/Room migration execution remains
  a separate validation step.
- CBR/CB7 still use server-rendered pages; downloading their archive does not make
  that inherited reader work offline. CBZ uses the local Foliate path.

Validation of this increment: both fdroid/full debug APKs assembled; 359 JVM tests
passed in each flavor; fdroid debug lint passed (127 warnings, no errors); local
3.9.0 smoke verified independent progress for two EPUB editions, exact manifest
IDs for two MP3 tracks, and byte-range serving for all four files.

## Additive offline downloads (0.9.1)

- Completed downloads offer **Add files**. Already-published files are locked in
  the picker; confirmation only adds explicitly selected content, never removes it.
- The persisted journal, cover, file IDs and local paths survive an extension and
  a failed extension. Each completed file is published immediately, not only after
  the whole book finishes. Readers/player can still use the existing local files.
- Published audiobooks remain in Android Auto's offline catalog during extensions.
- Scheduling awaits WorkManager's acknowledgement. Scheduling errors leave a
  retryable failed plan and an inline UI message, rather than crashing the detail
  screen. Short DB/WorkManager changes finish off the UI thread even if that screen
  closes immediately after confirmation; media transfer itself remains cancellable.
- Each work generation has a token. Progress/checkpoint/failure/completion writes
  are conditional updates, so an obsolete worker cannot overwrite a newer plan or
  recreate a deleted row. Plan replacement uses a Room transaction and snapshot CAS.
- Stable storage IDs retain partial bytes for retries while separate WorkManager
  names prevent a finishing older worker swallowing a new request. New installations
  of a deleted book use a fresh storage ID. Filenames include exact file IDs so two
  editions called `book.epub` cannot overwrite one another.
- SQL tests execute the production migration and guarded update statements against
  SQLite, including stale workers, deleted rows and retained published content.
  This is not a substitute for Android Room/WorkManager device testing.
- The 0.9.0 APKs are archived locally in `.local/apks/43b7b9d/`. The 0.9.1 build
  uses versionCode 901 and the same app identity/debug signing key for an update.
- No production services, media or reading history were used for this increment.

Validation: fdroid/full debug APKs built; 377 JVM tests passed per flavor; both
lint tasks passed with no errors (127 warnings each); all 5 SQLite migration/guard
tests passed. APK signatures verified and matched the archived 0.9.0 certificate.
Android device validation of additions, SAF and interruption remains outstanding.

## Editions, reader fixes and initial AI/read-along (0.9.3)

- One audiobook now yields one edition card regardless of track count. Selecting
  it includes every missing track; exact file IDs remain in the persisted plan.
- The Editions tab replaces the technical track list; reading choices retain exact
  text-edition identity. New bottom sheets, editorial typography, warmer light/
  dark palettes, cover presentation and card shapes start the visual redesign.
  This is a first design pass, not a visually accepted complete redesign.
- Active-tab reselection pops its nested stack to the tab root, clears the detail
  pane and resets the Library subsection. The selected tab stays highlighted on
  nested screens; ordinary switching still uses saved tab state.
- Reader CSS applies one opaque surface to the outer canvas, margins and chapter
  roots, rather than painting opaque rectangles on every descendant. Publisher
  root borders/shadows are cleared in themed mode. Visual verification of the
  user's exact EPUB and WebView remains pending.
- Embedded EPUB/SMIL read-along is wired into the native reader: explicit play,
  pause/resume, previous/next fragment, speed and following text highlights.
  It pauses on backgrounding; a separate Media3 audiobook is paused before launch.
  Real vendored-engine tests cover next-asset transitions, late audio after stop,
  end-of-book and nonfatal errors. A synthetic EPUB/SMIL fixture is recognized by
  the local server and can be explicitly downloaded as one text edition.
- Read-along is **foreground only** in this increment. Exact audio-clock restore,
  cross-device overlay position, native MediaSession/background playback and
  arbitrary external-audio alignment are not implemented. Text CFI/progress uses
  the existing reading flow; no parallel audiobook session/statistics are started.
- The Story Guide panel implements exact-edition metadata, content access checks,
  confirmed build/update/retry/cancel on compatible deployments, status polling,
  and explicit completed-chapter spoiler bounds for names/profiles/recaps.
  Stock production content is still attestation-restricted; the UI says so and
  avoids charging for generation whose output it cannot read. Detailed audit and
  outstanding features: `docs/AI_COMPATIBILITY.md`.
- No attestation policy, production server or AI provider configuration was changed.
  No paid model request or user-book upload was made during implementation/tests.

Validation on the current toolchain: both debug APKs assembled, 394 JVM tests
passed per flavor, both lint tasks passed; 9 Node tests for appearance/SMIL and
5 SQLite tests passed. Local HTTP smoke verified embedded-overlay recognition,
ordinary Story Guide metadata access and production attestation denial. Real
device playback, visual navigation/dark-theme acceptance and actual AI generation
with an authorized provider remain unverified.

## Toolchain migration and emulator validation (0.9.4)

- Migrated to AGP 9.5.2 built-in Kotlin, Gradle 9.9.2 (distribution checksum
  pinned), Kotlin/Compose compiler 2.4.21, KSP 2.3.14, Hilt 2.60.1,
  Compose BOM 2026.11.01, Media3 1.11.1, Room 2.8.5, Play Publisher 4.1.2.
- Compose requires compile SDK 37.2. Target SDK stays 36; minimum stays 26.
  Docker includes the required platform/build tools; host SDK/JDK not required.
- Adapted exposed dropdown APIs and fixed four new configuration-awareness lint
  errors using Compose string resources, without suppressing the checks.
- Both flavor APKs and lint tasks pass; 394 JVM tests per flavor, 9 JS tests and
  5 SQLite checks pass. A transient Maven DNS failure was retried, not bypassed.
- An isolated Android 36 Pixel 6 emulator validated in-place upgrade with retained
  login/download, edition grouping, adding all 75 tracks, retained offline text,
  Home/Library root reselection, Library subsection reset, dark/light reader,
  foreground SMIL playback/highlighting, pause/rate/fragment controls, background
  pause and the production Story Guide restriction message.
- Evidence and limits: `docs/DEVICE_TESTING.md`. Silent fixtures and no emulator
  audio output do not validate audible narration. Physical devices, complete
  visual redesign/Russian localization, full read-along/background alignment and
  expanded AI work remain open; this milestone does not complete the entire plan.

## AI clients, exact local narration restore and Russian resources (0.9.5)

- Implemented selected-excerpt Reading Help with streaming SSE answers, explicit
  provider/cost confirmation, exact edition identity, UI-language answers and
  cancellation on closing/backgrounding. Only the previewed selection is sent.
- Implemented Search photo recognition: Android photo picker, bounded on-device
  resizing/orientation handling, JPEG without EXIF/GPS, explicit upload confirmation,
  cover/shelf modes and authoritative library matches. No library edits are made.
- Both use nonbillable invalid-DTO capability probes and respect stock production
  attestation denial. Provider budgets, permissions and configuration errors remain
  separate. Added explicit `APP_ATTEST_REQUIRED` mapping after emulator testing.
- Potentially billable POSTs use a separate HTTP client with redirects/connection
  retries disabled and one-shot bodies. Tests verify no replay on 503 Retry-After,
  disconnect after request, or redirect to a different origin. No provider was called.
- Local read-along checkpoints include exact SMIL fragment, spine section, asset,
  fractional asset seconds and playback rate, scoped by server/account/file ID and
  actual EPUB SHA-256. Loading a checkpoint never autoplays or moves the text page.
  There are no additional sessions/events or guessed percentage-to-clock mappings.
- Fixed pause-during-load autoplay, backward transitions across unnarrated sections,
  and selecting the previous chapter's final fragment across several audio assets.
  Added a two-narrated-chapter fixture with an unnarrated interlude.
- Added Russian counterparts for every current string/plural resource, with all
  Russian plural forms and checked format arguments. Android app-language selection
  advertises English/Russian. Search layout/cards/error handling are redesigned;
  large-font navigation/hint issues were found and corrected on the emulator.
- Prepared, but did not deploy, a strict opt-in server package/certificate policy
  in `tools/server-compat/`. Default trust and hardware/verified-boot requirements
  are unchanged. Three parser tests pass and the patch applies cleanly to v3.9.0;
  full server build/attestation tests and native enrollment/signing remain next work.

Validation: both debug APKs/lints pass, 420 JVM tests per flavor, 19 reader JS,
5 SQLite, 2 localization and 3 policy-parser tests pass. Production-mode HTTP smoke
passes, retaining mobile AI 403 protections. Emulator confirms Russian Search and
AI restriction screens, exact local clock restore after Force stop, explicit offline
read-along, and readable navigation at 1.4× system font. Full visual/physical-device
acceptance, audible narration and real provider generation remain unverified.

### Streaming clarification requested by the user

Audiobook playback already uses HTTP streaming/Range with Media3 buffering, not an
automatic whole-book offline download. Through 0.9.5 the native EPUB/PDF reader instead
fetches a complete temporary file before opening; calling this true chapter streaming
would be incorrect. The online server Web reader is a separate mode.

The EPUB on-demand mode is implemented in 0.9.6 below. PDF has different seekable-file
requirements and is not streamed merely because EPUB becomes streamed.

## Native online EPUB (0.9.6)

- Settings → Reading → Reader interface → **App · online EPUB** enables on-demand
  chapters/resources through `/epub/{bookId}/info` and `/file/…`, always with the
  exact `fileId`. Existing **App · cached file** remains the default.
- Explicit offline editions take priority in either native mode. PDF/other formats
  still use complete files; the server Web reader remains a distinct mode.
- WebView receives metadata and a scoped resource lease, never JWT. Native HTTP
  authorizes only allowlisted resources of that book/file, blocks redirects and
  cross-account/server reuse, and cancels requests when the lease closes. No whole
  ZIP is written to disk; unsupported streaming fails without a hidden full download.
- Literal-percent asset names account for the pinned server's double-decoding
  behavior. HTTP/Node/JVM checks and emulator rendering cover `art 100%.svg`.
- Embedded read-along works in the foreground with streamed EPUB resources, but
  currently fetches the current audio asset as a whole Blob. This is **not** native
  Media3 range streaming for embedded narration; a large single asset can use
  substantial memory/bandwidth. Ordinary audiobook playback remains separate.
- Stream checkpoints use the captured server content revision; local checkpoints
  use full EPUB SHA-256. Each fingerprint has its own key; 0.9.5 legacy positions
  remain readable. The server's `fileHash` is KOReader partial MD5, not a full-byte
  cryptographic hash. Server API has no atomic archive revision precondition, so an
  archive changed during an open session is not guaranteed byte-consistent.

Validation: both APKs, both lints and **428 JVM tests per flavor** pass; **21 reader
Node, 5 SQLite, 2 localization and 3 policy-parser tests** pass. Local production-mode
HTTP smoke checks exact-edition asset bytes and literal-percent SVG. Emulator checks
chapters/TOC, foreground read-along across an unnarrated interlude, downloaded EPUB
priority without network, unchanged complete-file cache, and a blue SVG with no
publisher white backgrounds/red root borders (`52`–`59`). No physical-device,
audible-output or paid provider claim is made. Next: native key enrollment/signing,
companion-server validation, native background read-along and remaining plan items.

## Explicit Android attestation client (0.9.7)

- Added optional confirmed device-key registration in Settings. Requires HTTPS and
  hardware-backed Android Keystore P-256; software keys or development receipts
  never activate signing. Attestation certificate disclosure is explained before
  upload. No provider inputs are sent during enrollment.
- Implemented exact challenge encoding, SEC1 public-point key ID, leaf-first DER
  certificate enrollment and network-boundary ECDSA assertions with exact body
  hash, encoded path/query, millisecond clock and fresh nonce.
- Registration is scoped to the exact server/account. Failed replacement preserves
  the previous key; unsuccessful new keys are cleaned up. Local disable deletes
  local registration/key without changing the server. Metadata is excluded from
  backup/transfer because private keys are device-bound.
- AI POSTs remain single-attempt, with authenticators, redirects and connection
  retries disabled. `APP_ATTEST_KEY_UNKNOWN` never reenrolls or replays a paid POST;
  server restarts require an explicit new registration. Enrollment does not alter
  the stock official-app-only policy or enable a software/emulator fallback.
- The companion patch was applied only to a disposable upstream v3.9.0 checkout.
  Its production build passes; **102 attestation/contract tests** pass, including
  verification of a Kotlin-produced synthetic public signature vector by the
  upstream verifier. Targeted ESLint and Prettier checks pass. This is not a full
  deployed-server/hardware/provider end-to-end test.

Validation: both APKs/lints pass, **447 JVM tests per flavor**, 21 reader Node,
5 SQLite, 2 localization and 3 policy-parser checks pass. The Russian emulator UI
shows explicit certificate consent and refuses registration against the HTTP-only
isolated test server (`60`–`63`), retaining its login/settings. Successful real
hardware enrollment, unlocked-device behavior and paid AI output are unverified.
No production installation was patched or configured. Native background read-along,
external-audio alignment, cross-device narration coordinates and remaining design/
physical-device acceptance work remain open; this does not complete the plan.
