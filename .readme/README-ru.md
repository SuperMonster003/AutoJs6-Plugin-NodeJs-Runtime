<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин нативной среды выполнения Node.js 26.13.0 для AutoJs6</p>

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

Плагин AutoJs6 Node.js Runtime предоставляет AutoJs6 встроенную нативную среду выполнения Node.js 26.13.0 для скриптов Node.js и задач среды выполнения плагинов. На Android 17 и новее разрешите доступ к устройствам поблизости перед включением этого плагина в центре плагинов AutoJs6. Разрешением локальной сети также можно управлять на странице настроек плагина. Без разрешения плагин остается выключенным, а автозапуск пропускается без уведомления. Разрешение принадлежит плагину и не зависит от разрешения AutoJs6.

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
- API database/sqlite хоста для Bun и Workers Node.js с параметризованными запросами, транзакциями и сохранением данных.
- Измерения и подписки датчиков для Bun и Workers Node.js с отменой и автоматическим освобождением слушателей.
- Shell хоста с потоковым вводом и выводом, разрешением Shizuku и очисткой процессов для Bun, Node.js и Workers.

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

- Слот среды выполнения: `node26_13`.
- ID плагина: `nodejs`, движок: `nodejs`.
- Действие сервиса среды выполнения: `org.autojs.plugin.nodejs.RUNTIME`.
- Нативные библиотеки среды выполнения: `libnode.so`, `libautojs6-node.so` и `libnodexe.so`.
- Intl: ICU 78 с полными региональными данными (`--with-intl=full-icu` с 1.6.0; с 1.5.1 по 1.5.7 поставлялся small-icu только с английскими данными); `Intl`, региональное форматирование, экранирование свойств Unicode в регулярных выражениях и собственный вывод ошибок Node в stderr совпадают с настольным Node, `NODE_ICU_DATA` не требуется.
- Temporal: начиная с 2.0.0 встроенный в Node.js 26 глобальный объект `Temporal` работает с датами, временем, часовыми поясами и календарями без дополнительных библиотек.
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
- Линия Node.js 24: плагин 1.x (Node.js 24.21.0) поддерживается в ветке `release/1.x` только обновлениями безопасности до 2028-04-30 (окончание поддержки Node.js 24).

******

### История Выпусков

******

# v2.0.0

###### 2026/10/30

* `Подсказка` Начиная с плагина 2.0.0 скрипты выполняются в Node.js 26, где удалены некоторые API, устаревшие в Node.js 24 (например SlowBuffer, опция recursive у fs.rmdir, константы вида fs.F_OK, assert.CallTracker и node:_stream_wrap); использующие их скрипты нужно доработать. Чтобы остаться на Node.js 24, удалите версию 2.x и установите версию 1.x (только обновления безопасности, поддержка до 2028-04-30)
* `Подсказка` Нативные аддоны (.node), собранные напрямую с API V8, нужно пересобрать для Node.js 26; аддоны на Node-API это не затрагивает
* `Новое` Глобальный объект Temporal из Node.js 26 работает с датами, временем, часовыми поясами и календарями без дополнительных библиотек
* `Новое` Модули app, shell и shizuku для Node.js и Workers, совместимые со скриптами AutoJs6, включая запуск, удаление и остановку двойных (клонированных) приложений; команды выполняются через Shizuku, root или обычную оболочку, смотря что доступно первым, и возвращают Promise
* `Новое` Модули системной информации для Node.js и Workers, совместимые со скриптами AutoJs6: autojs, installer, memory, settings, sysprops, power_manager и autojs6:system (setScreenMetrics также масштабирует нажатия и жесты специальных возможностей), а также переключатели и каналы уведомлений, dialogs.build, engines для просмотра и остановки скриптов хоста, plugins.extend и plugins.tools
* `Новое` Модули device, sensors и media для Node.js и Workers, совместимые со скриптами AutoJs6: модель устройства, проверки экрана и производителя, все варианты вибрации и удержания экрана включённым, события датчиков, воспроизведение музыки и сканирование медиафайлов; для чтения идентификаторов устройства нужно объявить новую возможность device.identifiers
* `Новое` colors/Color, files, zip, mime и регистратор времени для Node.js и Workers, совместимые со скриптами AutoJs6; dayjs, pangu.js, cheerio и banana-i18n доступны через autojs6:global, а запросы axios из Workers выполняет главный поток
* `Исправление` Прокси Packages / java / android, устанавливаемые rhino.install(), выдавали ошибку при чтении статических полей, вызове describe(), вызове статических методов и создании объектов через new
* `Улучшение` Node.js 26 больше не поставляет corepack; плагин по-прежнему включает corepack 0.36.0, использование в терминале не меняется
* `Улучшение` Объявления TypeScript для worker_threads описывают параметры, отмену, тайм-ауты и диагностику WorkerPool, типы работают без @types/node
* `Улучшение` Библиотека Node.js собирается с NDK r29 и сохраняет выравнивание страниц 16 KB для всех трёх ABI; скрипт приёмки релизов выполняет полную регрессию на устройстве, пишет отчёт JSON и измеряет холодный запуск
* `Зависимость` Обновление Node.js 24.21.0 -> 26.13.0 (V8 14.6, OpenSSL 3.5.9, ICU 78.3) с библиотеками Android, собранными из исходного кода для всех трёх ABI
* `Зависимость` Обновление npm 11.19.0 -> 11.20.0

# v1.32.0

###### 2026/10/29

* `Новое` Управление задачами ai.agent, события и контекст/результаты зарегистрированных скриптов для Node.js, Bun и Workers с явной авторизацией упакованных приложений
* `Новое` Модули util, species, s13n, fmt/cvt и jsox и глобальные функции данных для Node.js и Workers, совместимые со скриптами AutoJs6, с импортом по именам вида autojs6:util
* `Исправление` Вызов web.cancelFileChooser() сразу после chooseFiles() мог не закрыть окно выбора файлов
* `Улучшение` Результаты и ошибки jsox и преобразования единиц байтов теперь совпадают со скриптами AutoJs6, а jsox.extend/extendAll напрямую расширяют Math, Array и Number текущего потока

# v1.31.0

###### 2026/10/22

* `Новое` Генерация текста ИИ, потоковые ответы, каталог моделей и постоянные сеансы для Node.js, Bun и Workers, с отменой и отдельным разрешением для приложений в 3-Stone AI

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

Параметры сборки берутся из `version.properties`, текущий минимальный SDK равен 24, целевой SDK равен 37.

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
