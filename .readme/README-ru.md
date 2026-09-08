<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин нативной среды выполнения Node.js 24.5.0 для AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
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

Плагин AutoJs6 Node.js Runtime предоставляет AutoJs6 встроенную нативную среду выполнения Node.js 24.5.0 для скриптов Node.js и задач среды выполнения плагинов.

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

- Слот среды выполнения: `node24_5`.
- ID плагина: `nodejs`, движок: `nodejs`.
- Действие сервиса среды выполнения: `org.autojs.plugin.nodejs.RUNTIME`.
- Нативные библиотеки среды выполнения: `libnode.so` и `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` и `universal`.
- Файловая система: доступны пути устройства, разрешённые Android; `/proc`, `/sys` и `/dev` являются жёсткими границами.
- TypeScript: принимает результат хоста и может запросить provider-v3 compilation файлов, созданных во время выполнения; прямой raw TypeScript возвращает `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM linker: `vm.SourceTextModule` / `vm.SyntheticModule`, нативные live binding и циклические зависимости.
- Возможности: синхронное выполнение скриптов, bundle transport, нативная встроенная среда выполнения, брокер возможностей хоста, host capability live bridge.

******

### История Выпусков

******

# v1.3.0

###### Не выпущено

* `Новое` Подписки моста Node.js передают события датчиков, WebSocket, интерфейса, оверлеев и ввода через существующие обратные вызовы с on/once/off, ограниченными очередями и совместимостью с drainEvents
* `Исправление` Исправлено накопление запросов и ответов моста в постоянно работающих скриптах: завершённые запросы удаляются, а диагностика хранит только последние 32 ответа с ограничением размера
* `Улучшение` Снижена задержка моста благодаря транспорту JNI/Binder по умолчанию и ответам через цикл событий Node; сохранены файловый транспорт и ограничения ожидающих вызовов

# v1.2.0

###### 2026/08/29

* `Новое` В фасад `mediainfo` добавлен `capabilities()`, а в его вызываемый интерфейс и `read()` — явный выбор снимка плагина v1/v2; при отсутствии схемы по-прежнему возвращается существующий снимок Node v1, принадлежащий хосту
* `Новое` Добавлен module-source provider v3 для компиляции по требованию созданных во время выполнения файлов `.ts/.mts/.cts` через PFD с ограничением по байтам и SHA-256, с отдельным бюджетом компиляции 30 s, стабильным отказом для выхода за путь, символических ссылок и неоднозначности, а также диагностикой TypeScript и отображением стека Source Map от хоста
* `Новое` Включён доступ к файловой системе как в настольном Node.js в пределах разрешений Android, при этом `/proc`, `/sys` и `/dev` остаются запрещены
* `Новое` Добавлены `accessibility.swipe` и `accessibility.gesture` под отдельной возможностью `accessibility.gesture`
* `Новое` Добавлен тайм-аут скрипта, охватывающий очередь и выполнение, с ошибкой `ERR_AUTOJS6_SCRIPT_TIMEOUT`; без заданного тайм-аута скрипты по-прежнему могут работать неограниченно
* `Новое` По умолчанию включены `dgram` (UDP) и `http2`; для `trace_events` возвращается явная ошибка отключения
* `Новое` Добавлена локальная отладка `inspector`, явно включаемая в сборках Debug, с прослушиванием только localhost и подключением через `adb forward`
* `Новое` Добавлена активация без интерфейса, защищённая разрешением плагина хоста, дополнены описания в центре плагинов и отключено резервное копирование данных приложения
* `Исправление` Исходный TypeScript без результата компиляции от хоста теперь отклоняется, добавлено сопоставление динамических import в snapshot и нормализация сгенерированных/импортированных кадров стека
* `Исправление` Snapshot-адаптер частичного ESM заменён нативным linker V8; исправлено отсутствие обновления изменяемых экспортов через циклические реэкспорты
* `Исправление` Исправлены ESM-импорты фасадов совместимости AutoJs6 и проверка отсутствующих суффиксов TypeScript с сохранением приоритета установленных пакетов npm
* `Улучшение` Удалены legacy fallback стирания TypeScript на основе regex и его переключатель запроса; raw `.ts/.mts/.cts` теперь всегда требуют результат компилятора хоста
* `Улучшение` Согласованы контракт v2 хоста/плагина, манифесты возможностей и граница ответственности plugin-only среды выполнения
* `Улучшение` Примеры Node.js, декларации TypeScript, мастер проектов, настройки runtime по умолчанию и проверки согласованности с хостом перенесены в репозиторий плагина; из хоста удалены переключатели Gradle и дубликаты ресурсов разработки
* `Улучшение` Проверенное покрытие npm расширено до 15 пакетов: добавлены axios, express и пакеты ESM-only nanoid, p-limit, yocto-queue
* `Улучшение` Поле запроса `executionMode` теперь определяет режим жизненного цикла; не влияющее на работу поле `runtimeAdapter` объявлено устаревшим
* `Улучшение` Удалены десять устаревших примеров, выводивших только фиксированные состояния; в типах отмечено отсутствие провайдеров хоста для захвата экрана, анализа изображений и записи звука

# v1.1.0

###### 2026/08/18

* `Новое` Добавлены потоковая передача stdout/stderr в реальном времени и кооперативная отмена через `node::Stop`
* `Новое` Немедленный отказ BUSY заменён ограниченной последовательной очередью на три ожидания, добавлен жизненный цикл постоянных долгих скриптов
* `Новое` По умолчанию включены нативные сетевые модули Node, `worker_threads` и `child_process`, проверены десять популярных чистых JavaScript-пакетов npm
* `Улучшение` Добавлены рабочие области direct-run и толерантное согласование provider исходников v1..v2 с краткими кодами ошибок и стеками JavaScript

##### Больше истории выпусков

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-ru.md)

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
