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
- Возвращает все встроенные модули Node.js так, как их экспортирует Node, напрямую из собственного перечня `module.builtinModules` (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` и остальные); ограничение на уровне запроса сохраняют только `worker_threads`, `child_process`, `inspector` и сетевые модули (с 1.6.0).
- Локальная отладка Inspector в Debug и Release по явному запросу хоста, через localhost и перенаправление ADB
- Поддерживает исходный код CommonJS/ESM, исходники модулей, рабочий каталог, корень песочницы, переменные окружения и результаты stdout/stderr.
- Выполняет модули через собственные загрузчики CommonJS / ESM Node.js (`require`, `import()`, `import.meta`, `require(esm)`, exports / imports пакетов нативны); среда выполнения регистрирует только `module.registerHooks` для модулей среды `autojs6:*`, исходников модулей от хоста и запросного шлюза встроенных модулей (с 1.6.0).
- Использует собственный модуль fs Node.js; доступность файлов определяется только разрешениями Android приложения плагина, без собственного запрета `/proc`, `/sys` или `/dev` в среде выполнения (с 1.6.0).
- Выполняет результат TypeScript от хоста и может запросить provider-v3 compilation для созданных во время выполнения проектных `.ts`/`.mts`/`.cts`; прямая raw-отправка всегда отклоняется, поскольку fallback стирания и переключатель совместимости удалены.
- Предоставляет брокер возможностей хоста и live bridge с модулями среды выполнения, такими как `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` и `autojs6:bridge-permissions`.
- Включает проекты `sample/nodejs` и перечень возможностей API хоста `docs/HOST-API.md`.
- Метаданные плагина, инструкции по использованию, README и CHANGELOG локализованы на испанский, французский, русский, арабский, японский, корейский, английский, упрощенный китайский, традиционный китайский Гонконга и традиционный китайский Тайваня.
- Поставляет многовходовый терминальный лаунчер `libnodexe.so` и архив npm / corepack, объявленные через manifest meta-data `NODE_CLI_*`, чтобы терминал AutoJs6 (хост 6.8.0+) мог запускать `node`, `npm`, `npx`, `corepack`, `yarn` и `pnpm` в shell под собственным uid.
- В настройках плагина доступны 1-8 параллельных запусков (по умолчанию 2) и размер очереди (по умолчанию без ограничений); остальные скрипты ожидают в порядке FIFO, изменения сохраняют принятые задачи, а idleExitMs освобождает простаивающие процессы
- Ускорен запуск скриптов за счет устранения лишней инициализации Web API и включения переносимого кеша компиляции модулей Node для рабочих каталогов и Worker по умолчанию; кеш используется между копиями, а Node сбрасывает записи при изменении исходников

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
- Intl: ICU 78 с полными региональными данными (`--with-intl=full-icu` с 1.6.0; с 1.5.1 по 1.5.7 поставлялся small-icu только с английскими данными); `Intl`, региональное форматирование, экранирование свойств Unicode в регулярных выражениях и собственный вывод ошибок Node в stderr совпадают с настольным Node, `NODE_ICU_DATA` не требуется.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` и `universal`.
- Файловая система: собственный модуль fs Node.js; доступен любой путь устройства, разрешённый правами Android, и среда выполнения не хранит собственных запретов.
- TypeScript: принимает вывод хоста, сопоставляет предкомпилированные снимки и может запрашивать компиляцию provider v3; сырые модули TypeScript на диске загружаются собственным удалением типов Node, а сырая точка входа TypeScript, отправленная напрямую, по-прежнему возвращает `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM: собственный загрузчик Node.js с `module.registerHooks`; живые привязки, циклические зависимости, `import.meta`, URL `data:` и `require(esm)` как на десктопе.
- Возможности: синхронное выполнение скриптов, bundle transport, нативная встроенная среда выполнения, брокер возможностей хоста, host capability live bridge.
- Потоки файлов, gzip/deflate/Brotli и базовое выполнение VM переведены в стабильные возможности; явно включенный Inspector (Debug/Release) стабилен в пределах localhost.
- Встроенное выполнение больше не выводит ExperimentalWarning; события warning, обычные предупреждения, предупреждения об устаревании и ошибки сохранены. Стабильность API Node и настройки терминала по умолчанию не изменены.
- Нативные Worker поддерживают вложенность, MessagePort, stdio, argv, execArgv и наследование предварительной загрузки; удалены ограничения плагина на число Worker и время запуска, resourceLimits задаёт переопределяемые значения по умолчанию, а WorkerPool по умолчанию не ограничивает очередь и время задач; Inspector учитывает запрос и текущую сборку
- Главные скрипты и Worker загружают нативные Android-аддоны через Node; process.dlopen и .node работают нативно без отдельных списков подписей или хешей; пример native-addon содержит три ABI с выравниванием 16 KB и офлайн-сборку NDK/CMake; неверная ABI или повреждённый файл возвращает ошибки Node, например ERR_DLOPEN_FAILED, NDK и Python на устройство не включены
- Inspector работает в сборках Debug и Release с явного разрешения хоста; inspector и inspector/promises сохраняют нативные модули Node, Session доступен в основных скриптах и Worker, прослушивание только на 127.0.0.1 без блокирующего ожидания; без разрешения url() возвращает undefined, активация и создание Session отключены
- install/update/remove в package_manager принимают спецификации npm и запускают встроенный npm в рабочем каталоге; npm.cli/exec/runScript/pack используют настоящую CLI, настройки registry/прокси/скриптов следуют npm и .npmrc проекта, вывод передается потоково с сохранением кодов ошибок npm; файлы CLI готовятся при первом вызове, объектные запросы сохраняют контракт локального хранилища хоста

******

### История Выпусков

******

# v1.6.0

###### 2026/10/09

* `Новое` В настройках плагина доступны 1-8 параллельных запусков (по умолчанию 2) и размер очереди (по умолчанию без ограничений); остальные скрипты ожидают в порядке FIFO, изменения сохраняют принятые задачи, а idleExitMs освобождает простаивающие процессы
* `Исправление` Мастер проектов больше не отклоняет поддерживаемые нативные зависимости, скрипты жизненного цикла npm, package exports и Inspector; проверка объявленных разрешений хоста сохранена
* `Улучшение` fs, fs/promises и node:sqlite являются собственными модулями Node.js: запрет /proc, /sys, /dev и обёртка проверки путей, принадлежавшие среде выполнения, удалены, доступность файлов определяется только разрешениями Android, асинхронные файловые API выполняются в пуле потоков libuv вместо блокировки цикла событий, а ошибки путей несут собственные коды Node.js (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH и ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED больше не выдаются файловыми операциями и операциями с базой данных)
* `Улучшение` process.cwd и process.chdir ведут себя как в Node.js; выполнение входит в рабочий каталог настоящим chdir, а относительные пути разрешает сам Node.js
* `Улучшение` ATTACH в node:sqlite (включая имена файлов через связанный параметр или выражение), VACUUM INTO, PRAGMA каталогов и setAuthorizer являются собственным поведением SQLite; enableLoadExtension и allowExtension следуют проверкам состояния самого Node.js
* `Улучшение` Библиотека среды выполнения Node.js собрана с полными данными ICU (--with-intl=full-icu): Intl и региональное форматирование совпадают с настольным Node.js, NODE_ICU_DATA больше не требуется
* `Улучшение` crypto.createPrivateKey и API подписи принимают ключи OpenSSL STORE в виде URL file: (патч отключения только для Android удалён)
* `Улучшение` Все встроенные модули Node.js (cluster, domain, repl, trace_events, wasi, constants, node:sea и остальные) возвращаются так, как их экспортирует Node, напрямую из собственного перечня module.builtinModules; require, require.resolve, import, process.getBuiltinModule и module.isBuiltin ведут себя как на настольной версии, и только worker_threads, child_process, inspector и сетевые модули сохраняют ограничение на уровне запроса; node:wasi открывается вместе с удалением границы /proc (в текущей libnode, собранной без трассировки, trace_events выбрасывает ERR_TRACE_EVENTS_UNAVAILABLE точно как Node)
* `Улучшение` process сохраняет только обработку жизненного цикла exit; dlopen, abort, kill, umask, binding, report, features и вызовы POSIX используют Node; os нативный, homedir и tmpdir учитывают HOME/TMPDIR, userInfo возвращает пользователя Android-приложения
* `Улучшение` supportedAbis в сведениях о плагине (INFO) и диагностике среды выполнения теперь берётся из meta-data манифеста org.autojs.plugin.contract.SUPPORTED_ABIS, записываемой при сборке (плагин nativeAlignment из AutoJs6-Gradle-Platform-Versions 1.9.0 и новее), то есть означает все ABI, для которых собран плагин, а не установленный сплит; без meta-data используется набор ABI, упакованных в установленных APK, а написанные вручную константы ABI удалены
* `Улучшение` Загрузка модулей выполняется собственным загрузчиком CommonJS / ESM Node.js: require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta и require(esm) нативны, а точка входа выполняется как главный модуль Node (require.main === module, __filename / process.argv[1] — realpath); среда выполнения регистрирует только module.registerHooks, отдающие модули среды autojs6:* и shim-ы совместимости (схема autojs6:; установленный npm-пакет по-прежнему затеняет одноимённый shim), запросный шлюз встроенных модулей, исходники модулей из запроса / провайдера / снимка TypeScript и встроенный исходник точки входа; линкер vm.SourceTextModule, собственный resolver, переписывание динамического import() и защита eval / Function удалены, ключи запроса esmEnabled / dynamicImportEnabled больше ничего не ограничивают
* `Улучшение` Нативные Worker поддерживают вложенность, MessagePort, stdio, argv, execArgv и наследование предварительной загрузки; удалены ограничения плагина на число Worker и время запуска, resourceLimits задаёт переопределяемые значения по умолчанию, а WorkerPool по умолчанию не ограничивает очередь и время задач; Inspector учитывает запрос и текущую сборку
* `Улучшение` autojs6:bridge.callAutoJs доступен во Worker и вложенных Worker через MessagePort родителя с сохранением проверок разрешений, ошибок, тайм-аутов и отмены broker хоста; пример worker-host-bridge читает реальные сведения об устройстве хоста, для API добавлены объявления TypeScript
* `Улучшение` Точки входа и импортируемые модули .ts / .mts / .cts по-прежнему требуют JavaScript, созданный Compiler хоста; сохранены сопоставление снимков и компиляция по запросу через provider v3. Ошибки Node заменяют прежние коды AutoJs6 для ESM и динамического импорта.
* `Улучшение` Главные скрипты и Worker загружают нативные Android-аддоны через Node; process.dlopen и .node работают нативно без отдельных списков подписей или хешей; пример native-addon содержит три ABI с выравниванием 16 KB и офлайн-сборку NDK/CMake; неверная ABI или повреждённый файл возвращает ошибки Node, например ERR_DLOPEN_FAILED, NDK и Python на устройство не включены
* `Улучшение` Inspector работает в сборках Debug и Release с явного разрешения хоста; inspector и inspector/promises сохраняют нативные модули Node, Session доступен в основных скриптах и Worker, прослушивание только на 127.0.0.1 без блокирующего ожидания; без разрешения url() возвращает undefined, активация и создание Session отключены
* `Улучшение` install/update/remove в package_manager принимают спецификации npm и запускают встроенный npm в рабочем каталоге; npm.cli/exec/runScript/pack используют настоящую CLI, настройки registry/прокси/скриптов следуют npm и .npmrc проекта, вывод передается потоково с сохранением кодов ошибок npm; файлы CLI готовятся при первом вызове, объектные запросы сохраняют контракт локального хранилища хоста
* `Улучшение` Ускорен запуск скриптов за счет устранения лишней инициализации Web API и включения переносимого кеша компиляции модулей Node для рабочих каталогов и Worker по умолчанию; кеш используется между копиями, а Node сбрасывает записи при изменении исходников
* `Улучшение` Метаданные описывают реализованные возможности без временных статусов; примеры UI указывают необходимые службы хоста, удалены поля незапланированных магазина плагинов и каналов обновления

# v1.5.7

###### 2026/10/05

* `Улучшение` Значки центра плагинов используют размеры, положение, светлые и тёмные изображения и круглые фоны, настроенные в Icon Studio, сохраняя исходники и параметры для воспроизведения
* `Улучшение` Значки в сведениях о приложении Android используют изображения и светлый и тёмный фон Icon Studio, сохраняя прозрачные изображения центра плагинов и существующие варианты значков лаунчера

# v1.5.6

###### 2026/09/19

* `Новое` Фатальная ошибка Node.js в слоте среды выполнения (нехватка памяти кучи JavaScript, сбой внутренней проверки) теперь оставляет собственный диагностический отчёт Node в кэше плагина (crash/<executionId>.json, записывается перед abort; обработчик сигналов не устанавливается): результат сбоя содержит событие, показатели кучи, стек JS и путь к отчёту в slotExit.crash и в сообщении об ошибке (например CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json)), а getRuntimeInfo отдаёт новейший отчёт как lastCrash даже после перезапуска диспетчера; новая опция запроса maxOldGenerationSizeMb ограничивает кучу JS одного выполнения (старое поколение V8, в МБ), и именно так регрессионный тест воспроизводит этот сбой
* `Исправление` Исправлено падение слота среды выполнения из-за нехватки памяти кучи V8 (сообщалось как CRASH_NATIVE / SIGABRT) при require большого модуля CommonJS: маскирование комментариев и строк в загрузчике добавляло по одному символу, поэтому модулю в 22 MiB требовалось около 700 MB кучи; теперь маска строится из непрерывных фрагментов с тем же результатом (около 44 MB), а сканирование import / export в CommonJS переходит между кандидатами вместо проверки каждого символа
* `Исправление` Предупреждения чтения SDK XML v4 с AGP 9.1 и ошибочный запуск проверки выравнивания нативных библиотек APK при сборке модульных тестов JVM, устраненные общими плагинами сборки 1.8.3

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
