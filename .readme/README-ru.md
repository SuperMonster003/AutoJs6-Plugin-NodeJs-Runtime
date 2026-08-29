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
- Поддерживает исходный код CommonJS/ESM, исходники модулей, рабочий каталог, корень песочницы, переменные окружения и результаты stdout/stderr.
- Использует нативный linker V8 для входов ESM и dynamic `import()`, сохраняя циклические зависимости, изменяемые экспорты и live binding при реэкспорте; CommonJS `require(esm)` сохраняет границу синхронной совместимости.
- Предоставляет доступ к файловой системе как в настольном Node.js в пределах разрешений Android приложения плагина; `/proc`, `/sys` и `/dev` всегда запрещены средой выполнения.
- Выполняет результат TypeScript от хоста и может запросить provider-v3 compilation для созданных во время выполнения проектных `.ts`/`.mts`/`.cts`; прямая raw-отправка по-прежнему отклоняется, а legacy-стирание доступно только как явный режим миграции.
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

# v1.2.0

###### 2026/08/29

* `Новое` Добавлен module-source provider v3 для компиляции по требованию созданных во время выполнения файлов `.ts/.mts/.cts` через PFD с ограничением по байтам и SHA-256, с отдельным бюджетом компиляции 30 s, стабильным отказом для выхода за путь, символических ссылок и неоднозначности, а также диагностикой TypeScript и отображением стека Source Map от хоста
* `Новое` Включён доступ к файловой системе как в настольном Node.js в пределах разрешений Android, при этом `/proc`, `/sys` и `/dev` остаются запрещены
* `Новое` Добавлены `accessibility.swipe` и `accessibility.gesture` под отдельной возможностью `accessibility.gesture`
* `Исправление` Исходный TypeScript без результата компиляции от хоста теперь отклоняется, добавлено сопоставление динамических import в snapshot и нормализация сгенерированных/импортированных кадров стека
* `Исправление` Snapshot-адаптер частичного ESM заменён нативным linker V8; исправлено отсутствие обновления изменяемых экспортов через циклические реэкспорты
* `Улучшение` Согласованы контракт v2 хоста/плагина, манифесты возможностей и граница ответственности plugin-only среды выполнения
* `Улучшение` Примеры Node.js, декларации TypeScript, мастер проектов, настройки runtime по умолчанию и проверки согласованности с хостом перенесены в репозиторий плагина; из хоста удалены переключатели Gradle и дубликаты ресурсов разработки

# v1.1.0

###### 2026/08/18

* `Новое` Добавлены потоковая передача stdout/stderr в реальном времени и кооперативная отмена через `node::Stop`
* `Новое` Немедленный отказ BUSY заменён ограниченной последовательной очередью на три ожидания, добавлен жизненный цикл постоянных долгих скриптов
* `Новое` По умолчанию включены нативные сетевые модули Node, `worker_threads` и `child_process`, проверены десять популярных чистых JavaScript-пакетов npm
* `Улучшение` Добавлены рабочие области direct-run и толерантное согласование provider исходников v1..v2 с краткими кодами ошибок и стеками JavaScript

# v1.0.0

###### 2026/07/18

* `Новое` Добавлен сервис плагина среды выполнения Node.js с ID плагина `nodejs`, движком `nodejs` и слотом среды выполнения `node24_5`
* `Новое` Предоставлена нативная среда выполнения Node.js 24.5.0 через `libnode.so` и `libautojs6-node.so`
* `Новое` Среда выполнения Node.js работает в отдельном постоянном процессе, повторно используя глобальное для процесса состояние Node/V8 и создавая новые isolate и Environment для каждого запуска
* `Новое` Добавлено обнаружение информации о плагине через `org.autojs.plugin.INFO` и вызов среды выполнения через `org.autojs.plugin.nodejs.RUNTIME`
* `Новое` Поддержаны исходный код CommonJS/ESM, исходники модулей, рабочий каталог, корень песочницы, переменные окружения, результаты stdout/stderr и предварительный прогрев среды выполнения
* `Новое` Поддержан транспорт архива рабочей области v2 на уровне запроса с явным сопоставлением входных файлов, выполнением в приватной рабочей области плагина, обратной записью выходных файлов и манифестами tombstone для удалений без сканирования песочницы хоста
* `Новое` Допуск одного активного запуска без очереди с обратным давлением `ERR_AUTOJS6_NODE_PLUGIN_BUSY` и отменой через перезапуск процесса
* `Новое` Добавлены брокер возможностей хоста и live bridge с модулями среды выполнения, такими как `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` и `autojs6:bridge-permissions`
* `Новое` Добавлены APK сборки с разделением по ABI для `arm64-v8a`, `armeabi-v7a`, `x86_64` и APK `universal`
* `Новое` `libc++_shared.so` упаковывается вместе с библиотеками среды выполнения Node.js в раздельные и `universal` APK
* `Новое` Добавлены проекты `sample/nodejs`, инструмент диагностики Node resolver и инструменты проверки плана сборки среды выполнения
* `Новое` Добавлены локализованные метаданные плагина, инструкции по использованию, README и ресурсы CHANGELOG для испанского, французского, русского, арабского, японского, корейского, английского, упрощенного китайского, традиционного китайского Гонконга и традиционного китайского Тайваня
* `Исправление` Отмена через перезапуск процесса могла зафиксировать частичный снимок рабочей области во время завершения процесса среды выполнения
* `Исправление` Дескрипторы файлов транспорта архива рабочей области могли утекать при сбое проверки контракта запроса или материализации рабочей области, поскольку передача владения завершалась не на всех путях выхода
* `Улучшение` Метаданные и диагностика контракта R5 для ABI/возможностей, состояния постоянной среды выполнения, допуска, отмены и атрибуции отдельного процесса
* `Улучшение` Добавлена поэтапная диагностика на монотонных часах для построения исходника выполнения, начальной загрузки, выполнения скрипта, создания и получения результата и очистки после каждого выполнения с разделением состояний пропущено и неприменимо

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
