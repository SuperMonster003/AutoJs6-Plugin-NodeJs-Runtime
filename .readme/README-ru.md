<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин нативной среды выполнения Node.js 24.21.0 для AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Языки

******

Текущий README.md поддерживает следующие языки:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- Русский [ru] # текущий
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### Введение

******

Плагин AutoJs6 Node.js Runtime предоставляет AutoJs6 встроенную нативную среду выполнения Node.js 24.21.0 для скриптов Node.js и задач среды выполнения плагинов. На Android 17 и новее разрешите доступ к устройствам поблизости перед включением этого плагина в центре плагинов AutoJs6. Разрешением локальной сети также можно управлять на странице настроек плагина. Без разрешения плагин остается выключенным, а автозапуск пропускается без уведомления. Разрешение принадлежит плагину и не зависит от разрешения AutoJs6.

******

### Возможности

******

- Предоставляет сервис плагина `nodejs` с ID плагина `nodejs` и движком `nodejs`.
- Открывает синхронное выполнение скриптов и предварительный прогрев среды выполнения для хоста через `org.autojs.plugin.nodejs.RUNTIME`.
- Добавлен тайм-аут скрипта, охватывающий очередь и выполнение, с ошибкой `ERR_AUTOJS6_SCRIPT_TIMEOUT`; без заданного тайм-аута скрипты по-прежнему могут работать неограниченно
- По умолчанию включены `dgram` (UDP) и `http2`; для `trace_events` возвращается явная ошибка отключения
- Добавлена локальная отладка `inspector`, явно включаемая в сборках Debug, с прослушиванием только localhost и подключением через `adb forward`
- Поддерживает исходный код CommonJS/ESM, исходники модулей, рабочий каталог, корень песочницы, переменные окружения и результаты stdout/stderr.
- Использует нативный linker V8 для входов ESM и dynamic `import()`, сохраняя циклические зависимости, изменяемые экспорты и live binding при реэкспорте; CommonJS `require(esm)` сохраняет границу синхронной совместимости.
- Предоставляет доступ к файловой системе как в настольном Node.js в пределах разрешений Android приложения плагина; `/proc`, `/sys` и `/dev` всегда запрещены средой выполнения.
- Выполняет результат TypeScript от хоста и может запросить provider-v3 compilation для созданных во время выполнения проектных `.ts`/`.mts`/`.cts`; прямая raw-отправка всегда отклоняется, поскольку fallback стирания и переключатель совместимости удалены.
- Предоставляет брокер возможностей хоста и live bridge с модулями среды выполнения, такими как `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` и `autojs6:bridge-permissions`.
- Включает проекты `sample/nodejs` и перечень возможностей API хоста `docs/HOST-API.md`.
- Метаданные плагина, инструкции по использованию, README и CHANGELOG локализованы на испанский, французский, русский, арабский, японский, корейский, английский, упрощенный китайский, традиционный китайский Гонконга и традиционный китайский Тайваня.
- Поставляет многовходовый терминальный лаунчер `libnodexe.so` и архив npm / corepack, объявленные через manifest meta-data `NODE_CLI_*`, чтобы терминал AutoJs6 (хост 6.8.0+) мог запускать `node`, `npm`, `npx`, `corepack`, `yarn` и `pnpm` в shell под собственным uid.

******

### Использование

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

Установите и включите плагин в центре плагинов AutoJs6, затем запускайте скрипты Node.js директивой `"nodejs";`. Дополнительные примеры находятся в `sample/nodejs`.

******

### Быстрый старт

******

- **Установка** — Скачайте APK для вашего ABI со страницы [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) (при сомнениях выбирайте `universal`) и установите его, либо соберите локально командой `.\gradlew.bat :app:assembleDebug` и установите из `app/build/outputs/apk/debug/`. Затем включите этот плагин в центре плагинов AutoJs6. На Android 11+ предоставьте плагину доступ ко всем файлам в системных настройках, если скриптам нужно общее хранилище; без разрешения ожидается `EACCES`.
- **Запуск** — Создайте в редакторе AutoJs6 скрипт, первая строка которого — `"nodejs";`, а остальное пишите как для настольного Node.js (поддерживаются CommonJS/ESM, чистые JS-пакеты npm и встроенные сетевые модули). Запустите: вывод передаётся в реальном времени, скрипт можно остановить в любой момент. Исходные `.ts`/`.mts`/`.cts` сначала должны быть скомпилированы хостом в JavaScript; плагин не включает `tsc`.
- **Если что-то сломалось** — При сбое скрипта в консоль выводится стек JS и однострочный код ошибки (например `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`); подробности смотрите в логе процесса плагина: `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin`. Доступность API хоста описана в `docs/HOST-API.md`.

******

### Профиль Среды Выполнения

******

- Слот среды выполнения: `node24_21`.
- ID плагина: `nodejs`, движок: `nodejs`.
- Действие сервиса среды выполнения: `org.autojs.plugin.nodejs.RUNTIME`.
- Нативные библиотеки среды выполнения: `libnode.so`, `libautojs6-node.so` и `libnodexe.so`.
- Intl: ICU 78 только с английскими данными локали (`--with-intl=small-icu`); `Intl`, экранирование свойств Unicode в регулярных выражениях и собственный вывод ошибок Node в stderr работают в терминале AutoJs6, а `NODE_ICU_DATA` может указывать на полный файл данных ICU.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` и `universal`.
- Файловая система: доступны пути устройства, разрешённые Android; `/proc`, `/sys` и `/dev` являются жёсткими границами.
- TypeScript: принимает результат хоста и может запросить provider-v3 compilation файлов, созданных во время выполнения; прямой raw TypeScript возвращает `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, нативные live binding и циклические зависимости.
- Возможности: синхронное выполнение скриптов, bundle transport, нативная встроенная среда выполнения, брокер возможностей хоста, host capability live bridge.
- Потоки файлов, gzip/deflate/Brotli и базовое выполнение VM переведены в стабильные возможности; явно включенный Debug Inspector стабилен в пределах localhost.
- Встроенное выполнение больше не выводит ExperimentalWarning; события warning, обычные предупреждения, предупреждения об устаревании и ошибки сохранены. Стабильность API Node и настройки терминала по умолчанию не изменены.

******

### История Выпусков

******

# v1.5.5

###### 2026/09/17

* `Исправление` Версия и дайджест каталога возможностей, сообщаемые runtimeInfo, теперь берутся из runtime kit, что исправляет устаревшие значения 1.5.0, которые все еще сообщала версия 1.5.4
* `Исправление` worker_threads поддерживает new Worker(code, { eval: true }) как в Node и больше не считает строку кода путём к скрипту
* `Улучшение` Маршрут компиляции TypeScript на стороне хоста переведен в stable в каталоге возможностей, устаревшие метаданные legacy stripping удалены; каталог жизненного цикла перечисляет только запускаемые режимы выполнения, убраны поверхность packaged_long_running и зарезервированные имена node_sandboxed / worker_computation; метаданные транспорта, допуска и отмены соответствуют реальному runtime
* `Улучшение` Диагностика autojs6:profile описывает реальный runtime вместо исторических пометок partial / reserved / deferred: доступ к файлам в границах разрешений Android, подмножество process, каналы worker, пул процессов и решения по WASI / native addon
* `Улучшение` Ревизия примеров: packaged-esm, packaged-dynamic-import, require-esm, wasm-basic, wasm-plugin и desktop-parity-suite переведены в stable; обе parity-сборки реально выполняют свои сниппеты вместо печати текста каталога; compile-cache помечен как неприменимый, пример package-install (только метаданные) удалён
* `Улучшение` Загрузка модулей следует доступу к файлам Android, как в Node: require, import и Worker принимают абсолютные пути, цели в родительских каталогах и URL file: (/proc, /sys и /dev по-прежнему запрещены); поиск node_modules остаётся привязанным к рабочей области
* `Улучшение` Сообщения Worker и наблюдатели fs следуют нативным ограничениям Node: сняты лимиты 64 КБ на сообщение и 32 в очереди, а также квоты 16 наблюдателей / 64 события в секунду; ограничивает только память Android
* `Улучшение` Внутри worker process.exit() завершает только поток worker, как в Node, а process.getBuiltinModule() следует списку разрешённых builtin worker вместо полного отключения
* `Улучшение` Worker разрешают голые имена пакетов через node_modules рабочей области, как в Node (условия exports, main, index, самоссылка), вместо отказа
* `Улучшение` Динамический import() внутри worker проходит через частичный ESM-загрузчик worker (локальные пути, URL file:, пакеты рабочей области, разрешённые builtin, with { type: "json" }) вместо отказа
* `Улучшение` Пример host-events переведён в stable после ручной приёмки физической клавиши и доступа к уведомлениям
* `Улучшение` Пример scheduled-node-task печатает политику жизненного цикла и может сохранять запланированную задачу для реального запуска WorkManager; режим scheduled получил регрессию на стороне плагина
* `Улучшение` Режим выполнения scheduled переведён в available в каталоге возможностей после реального запуска планировщика WorkManager хоста через плагин
* `Улучшение` media.play() возвращает сеанс воспроизведения (pause/resume/seekTo/stop/status) через музыкальный сервис хоста, а новый модуль media_store даёт ограниченный доступ к MediaStore (capabilities/query/get/insert/update/delete/scanFile/exportFile) под возможностями media.playback / media.library / media.library.mutate; autojs6:compat.media получает псевдонимы в стиле Rhino playMusic; для обоих нужна соответствующая сборка хоста
* `Улучшение` Примеры media-playback / media-library переведены в stable после ручной приёмки с объединёнными медиа-провайдерами хоста; снимок каталога возможностей 1.5.5 опубликован в releases/nodejs-capability-catalog, и задача сверки с хостом теперь указывает на него
* `Улучшение` Обёртка fs больше не навязывает собственные ограничения опций: опции fs / унаследованный fd / flags у потоков, watch({ recursive: true }), асинхронные фильтры cp (cpSync сохраняет ERR_INVALID_RETURN_VALUE из Node), абсолютные / родительские / буквальные '!' glob-шаблоны с массивами exclude, type / encoding у readableWebStream и конструкторы Stats / Dirent / Dir теперь следуют нативному Node 24; filesystemProfile.advancedApis.recursiveWatch сообщает native
* `Улучшение` Рекурсивные readdir / opendir теперь нативные (сняты лимит в 4096 записей и проверка realpath для каждой записи; readdir('/') перечисляет имена proc/sys/dev как в Node), readlink / chmod / chown / utimes принимают абсолютные пути, chmod следует по символическим ссылкам, а коды ошибок политики fs сведены к ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (жёсткая граница, общий с загрузчиком) / ERR_AUTOJS6_FS_SCOPED_PATH; обычные сбои fs сохраняют только код Node
* `Улучшение` Среда выполнения больше не имеет собственных бюджетов исходников модулей (16 МиБ на модуль, 64 МиБ всего, 8192 модуля, число запросов к provider), точка входа CommonJS получает абсолютные __filename / require.main.filename / process.argv[1] как в Node и require.main.id равен '.', а fs.mkdtemp* стал нативным: возвращает префикс в написании вызывающего кода плюс суффикс в запрошенной кодировке и больше не отклоняет родительский каталог-символическую ссылку
* `Улучшение` node:sqlite передаёт файловые операции SQL нативному SQLite: ATTACH с литеральным именем файла (включая file: URI) проверяется авторизатором SQLite на границу /proc, /sys, /dev вместо сканирования текста SQL, цели VACUUM INTO проходят ту же проверку границы через внутренний ATTACH SQLite, каталожные PRAGMA больше не перехватываются, строки file: URI и пустая временная база открываются, а setAuthorizer() сочетается с проверкой границы (отклоняется только ATTACH с именем файла из параметра или выражения)
* `Улучшение` Квоты моста передаются хосту: вызовы сверх окна maxPendingBridgeCalls из autojs6:bridge-limits теперь ждут в порядке очереди вместо отказа с ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT, среда выполнения больше не ограничивает сама число дескрипторов изображений, одновременных контролируемых запросов fetch и контролируемых соединений WebSocket (брокер хоста продолжает применять свою политику), а require('fetch').policy.maxConcurrentRequests / require('websocket').policy.maxConnections упразднены
* `Улучшение` Мостовые фасады fetch / WebSocket / axios передают свои жёсткие пределы хосту: тайм-ауты, размер ответа, число перенаправлений, размеры сообщений и очереди и HTTP-метод передаются провайдеру хоста без обрезки (действует его собственная политика), среда выполнения следует не более чем 20 перенаправлениям, как Node, а упразднённые поля hard*/размеров по умолчанию в policy заменены на limitsEnforcedBy
* `Улучшение` opendir возвращает собственный ленивый fs.Dir Node (bufferSize, encoding и recursive передаются нативному opendir, ENOENT / ENOTDIR / ERR_DIR_CLOSED — ошибки Node; только dir.path и parentPath сохраняют написание вызывающего), а защита среды выполнения от удаления корня доступа к файловой системе упразднена: rm / rmdir корня решают Node и Android, как для любого другого пути (коды политики fs ограничены FS_NUL_BYTE и FS_PATH_ESCAPE)
* `Улучшение` Транспорт provider хоста принимает лимиты на источник / суммарные / по числу запросов, которые хост объявляет через getNativeDiagnostics() (встроенные 16 MiB / 64 MiB / 139264 — лишь запасной вариант), тела ответов мостового fetch приходят через файловый дескриптор (bodyTransport "pfd") и ограничены только политикой maxResponseBytes хоста, а не размером транзакции Binder, а ответ хоста, превышающий размер транзакции Binder, сразу сообщается как ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (ветка хоста node-m20-2-binder-body-pfd) вместо тайм-аута моста

# v1.5.4

###### 2026/09/17

* `Исправление` Встроенное выполнение больше не выводит ExperimentalWarning; события warning, обычные предупреждения, предупреждения об устаревании и ошибки сохранены. Стабильность API Node и настройки терминала по умолчанию не изменены
* `Улучшение` Потоки файлов, gzip/deflate/Brotli и базовое выполнение VM переведены в стабильные возможности; явно включенный Debug Inspector стабилен в пределах localhost

# v1.5.3

###### 2026/09/16

* `Улучшение` Разрешение локальной сети Android 17 перенесено в процесс включения и настройки плагина, без страницы разрешений в лаунчере; без разрешения плагин выключен, автозапуск пропускается без уведомления
* `Улучшение` Поддержка Android 17 (SDK 37), отдельное управление разрешением локальной сети плагина и инструкции по восстановлению доступа

##### Больше истории выпусков

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-ru.md)

******

### Сборка

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release сборка:

```powershell
.\gradlew.bat :app:assembleRelease
```

Параметры сборки берутся из `version.properties`, текущий минимальный SDK равен 24, целевой SDK равен 36.

******

### Структура Ресурсов

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` содержит локализованные описания плагина; `plugin_instruction.md` содержит инструкции по использованию для хоста. README и CHANGELOG генерируются из JSON источников через `.python/generate_markdown.py`.

******

### Ссылки

******

- Документация AutoJs6: https://docs.autojs6.com
- Официальный проект Node.js: https://github.com/nodejs/node
- План сборки среды выполнения Node.js: tools/nodejs/runtime-build/README.md
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
