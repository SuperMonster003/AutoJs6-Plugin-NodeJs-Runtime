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

Плагин AutoJs6 Node.js Runtime предоставляет AutoJs6 встроенную нативную среду выполнения Node.js 24.21.0 для скриптов Node.js и задач среды выполнения плагинов. На Android 17 и новее разрешите этому плагину доступ к устройствам поблизости для подключения к локальной сети. Разрешение AutoJs6 не распространяется на плагин. Общедоступный интернет и петлевые соединения не требуют этого разрешения.

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

******

### История Выпусков

******

# v1.5.3

###### 2026/09/16

* `Улучшение` Поддержка Android 17 (SDK 37), отдельное управление разрешением локальной сети плагина и инструкции по восстановлению доступа

# v1.5.2

###### 2026/09/15

* `Улучшение` Поднят compileSdk до 37 (Android 17); targetSdk остаётся 36 до проверки поведения, зависящего от целевого уровня

# v1.5.1

###### 2026/09/15

* `Исправление` Текст фатальных ошибок и предупреждений Node на Android теперь пишется в настоящий stderr помимо logcat, поэтому терминал показывает необработанные исключения вместо молчаливого выхода
* `Улучшение` `libnode.so` пересобран с `--with-intl=small-icu`: доступны `Intl` и экранирование свойств Unicode (`\p{...}`) в регулярных выражениях с английскими данными локали (`NODE_ICU_DATA` принимает полный файл данных ICU), поэтому corepack может запускать pnpm 11 и Yarn Berry в терминале AutoJs6

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
