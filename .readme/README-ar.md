<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>ملحق تشغيل Node.js 24.21.0 الاصلي ل AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### اللغات

******

يدعم README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- العربية [ar] # الحالي

******

### مقدمة

******

يوفر ملحق AutoJs6 Node.js Runtime بيئة تشغيل Node.js 24.21.0 اصلية مدمجة ل AutoJs6 من اجل سكربتات Node.js ومهام تشغيل الملحقات. على Android 17 أو أحدث, اسمح بالأجهزة القريبة قبل تفعيل هذا الملحق في مركز ملحقات AutoJs6. يمكنك أيضا إدارة إذن الشبكة المحلية من صفحة إعدادات الملحق. دون الإذن يبقى الملحق معطلا ويتم تخطي التشغيل التلقائي بصمت. يخص الإذن هذا الملحق وهو مستقل عن إذن AutoJs6.

******

### الميزات

******

- يوفر خدمة الملحق `nodejs` مع معرف الملحق `nodejs` والمحرك `nodejs`.
- يعرض تنفيذ السكربتات المتزامن وتسخين بيئة التشغيل للمضيف عبر `org.autojs.plugin.nodejs.RUNTIME`.
- أُضيفت مهلة للبرامج النصية تشمل الانتظار والتنفيذ وتُرجع `ERR_AUTOJS6_SCRIPT_TIMEOUT` عند تجاوزها؛ وتستمر البرامج دون مهلة محددة في العمل بلا حد زمني
- يعيد جميع وحدات Node.js المضمنة كما يصدّرها Node مباشرة من قائمة `module.builtinModules` الخاصة به (`dgram` و `http2` و `trace_events` و `wasi` و `cluster` و `repl` وغيرها)؛ ولا تحتفظ ببوابة على مستوى الطلب سوى `worker_threads` و `child_process` و `inspector` ووحدات الشبكة الأصلية (منذ 1.6.0).
- تصحيح Inspector محلي في Debug وRelease بطلب صريح من المضيف باستخدام localhost وتحويل ADB
- يدعم مصدر CommonJS/ESM, مصادر الوحدات, دليل العمل, جذر sandbox, متغيرات البيئة, ونتائج stdout/stderr.
- يشغّل الوحدات عبر محمّلات CommonJS / ESM الأصلية في Node.js (‎`require` و `import()` و `import.meta` و `require(esm)` وexports / imports للحزم كلها أصلية)؛ ولا يسجّل وقت التشغيل سوى `module.registerHooks` لوحدات وقت التشغيل `autojs6:*` ومصادر الوحدات التي يقدّمها المضيف وبوابة الوحدات المضمنة على مستوى الطلب (منذ 1.6.0).
- يستخدم وحدة fs الأصلية في Node.js؛ وتحدد أذونات Android لتطبيق المكوّن الإضافي وحدها إمكانية الوصول إلى الملفات، دون رفض خاص بوقت التشغيل لـ `/proc` أو `/sys` أو `/dev` (منذ 1.6.0).
- يشغّل ناتج مترجم TypeScript الذي يقدمه المضيف ويدعم عبر provider v3 ترجمة ملفات `.ts`/`.mts`/`.cts` المنشأة أثناء التشغيل عند الطلب; ويفشل الإرسال الخام المباشر دائما بشكل مغلق بعد إزالة fallback لمحو الأنواع ومفتاح التوافق.
- يوفر وسيط قدرات المضيف و live bridge مع وحدات تشغيل مثل `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, و `autojs6:bridge-permissions`.
- يتضمن مشاريع `sample/nodejs` وقائمة قدرات واجهة برمجة تطبيقات المضيف `docs/HOST-API.md`.
- بيانات الملحق, تعليمات الاستخدام, README, و CHANGELOG مترجمة للاسبانية/الفرنسية/الروسية/العربية/اليابانية/الكورية/الانجليزية/الصينية المبسطة/الصينية التقليدية لهونغ كونغ/الصينية التقليدية لتايوان.
- يوفر مشغّل الطرفية متعدد الأوامر `libnodexe.so` وأرشيف npm / corepack المعلنين عبر meta-data في manifest باسم `NODE_CLI_*`, لتتمكن طرفية AutoJs6 (المضيف 6.8.0+) من تشغيل `node` و`npm` و`npx` و`corepack` و`yarn` و`pnpm` داخل shell بمعرّف المستخدم الخاص بها.
- تتيح إعدادات الإضافة 1-8 عمليات تشغيل متزامنة (الافتراضي 2) وحدا لقائمة الانتظار (بلا حد افتراضيا); تنتظر البرامج الزائدة بترتيب FIFO وتحافظ التغييرات على المهام المقبولة ويحرر idleExitMs العمليات الخاملة
- تسريع بدء البرامج النصية بتجنب تهيئة Web API غير الضرورية وتفعيل ذاكرة تجميع وحدات Node المحمولة افتراضيا لمساحات العمل وWorker; يعاد استخدامها بين النسخ ويبطل Node المصادر المعدلة
- واجهات database/sqlite للمضيف في Bun وWorkers من Node.js مع استعلامات بمعاملات ومعاملات ذرية وتخزين دائم.
- أخذ عينات المستشعرات والاشتراك بها في Bun وWorkers من Node.js مع الإلغاء والتحرير التلقائي للمستمعين.
- Shell المضيف مع تدفق الإدخال والإخراج وتفويض Shizuku وتنظيف العمليات في Bun وNode.js وWorkers.

******

### الاستخدام

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

ثبت الملحق وفعله في مركز ملحقات AutoJs6, ثم ابدأ سكربتات Node.js بالتوجيه `"nodejs";`. تتوفر امثلة اضافية في `sample/nodejs`.

******

### البدء السريع

******

- **التثبيت** — نزّل ملف APK المطابق لبنية جهازك من [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) (اختر `universal` عند الشك) وثبّته, أو ابنِ محليًا بالأمر `.\gradlew.bat :app:assembleDebug` ثم ثبّت من `app/build/outputs/apk/debug/`. بعد ذلك فعّل هذه الإضافة في مركز إضافات AutoJs6. على Android 11 فأحدث, امنح الملحق إذن الوصول إلى كل الملفات من إعدادات النظام عند الحاجة إلى التخزين المشترك; وظهور `EACCES` دون الإذن متوقع.
- **التشغيل** — أنشئ سكربتًا في محرر AutoJs6 سطره الأول `"nodejs";` واكتب الباقي كما في Node.js لسطح المكتب (يدعم CommonJS/ESM وحزم npm بجافاسكربت خالص ووحدات الشبكة المدمجة). عند التشغيل يظهر الإخراج مباشرة ويمكن إيقاف السكربت في أي وقت. يجب أن يترجم المضيف ملفات `.ts`/`.mts`/`.cts` الخام إلى JavaScript أولا; فالملحق لا يتضمن `tsc`.
- **أين تنظر عند الخطأ** — عند فشل السكربت تعرض وحدة التحكم مكدس JS مع رمز خطأ من سطر واحد (مثل `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`); لمزيد من التفاصيل راجع سجل عملية الإضافة عبر `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin`. تُوثَّق قدرات API المضيف في `docs/HOST-API.md`.

******

### ملف بيئة التشغيل

******

- فتحة بيئة التشغيل: `node24_21`.
- معرف الملحق: `nodejs`, المحرك: `nodejs`.
- اجراء خدمة بيئة التشغيل: `org.autojs.plugin.nodejs.RUNTIME`.
- مكتبات بيئة التشغيل الاصلية: `libnode.so`, `libautojs6-node.so` و `libnodexe.so`.
- Intl: ICU 78 مع بيانات اللغات المحلية الكاملة (`--with-intl=full-icu` منذ 1.6.0؛ من 1.5.1 إلى 1.5.7 كان small-icu بالإنجليزية فقط)؛ تتطابق `Intl` وتنسيقات اللغات المحلية وهروب خصائص Unicode في التعبيرات النمطية ومخرجات أخطاء Node على stderr مع Node المكتبي، دون الحاجة إلى `NODE_ICU_DATA`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, و `universal`.
- نظام الملفات: وحدة fs الأصلية في Node.js؛ يمكن الوصول إلى أي مسار على الجهاز تسمح به أذونات Android ولا يحتفظ وقت التشغيل بأي رفض خاص به.
- TypeScript: يقبل مخرجات المضيف ويطابق اللقطات مسبقة التجميع ويمكنه طلب تجميع provider v3؛ وتُحمَّل وحدات TypeScript الخام على القرص عبر إزالة الأنواع الأصلية في Node، بينما يظل مدخل TypeScript الخام المرسل مباشرة يعيد `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM: محمّل Node.js الأصلي مع `module.registerHooks`؛ الروابط الحية والاعتماديات الدائرية و `import.meta` وعناوين `data:` و `require(esm)` كما على سطح المكتب.
- القدرات: تنفيذ سكربت متزامن, bundle transport, بيئة تشغيل اصلية مدمجة, وسيط قدرات المضيف, host capability live bridge.
- أصبحت تدفقات الملفات وتدفقات gzip/deflate/Brotli وتنفيذ VM الأساسي ميزات مستقرة; كما أصبح Inspector (Debug/Release) المفعّل صراحة مستقرا ضمن localhost.
- لم يعد التنفيذ المدمج يطبع تنبيهات ExperimentalWarning; مع الاحتفاظ بأحداث warning والتحذيرات العادية وتحذيرات الإهمال والأخطاء. لم يتغير تصنيف استقرار واجهات Node أو الإعدادات الافتراضية للطرفية.
- تدعم خيوط Worker الأصلية التداخل وواجهات MessagePort و stdio و argv و execArgv وتوارث التحميل المسبق; أزيلت حدود الإضافة لعدد الخيوط ووقت بدء التشغيل, وأصبحت resourceLimits قيما افتراضية قابلة للتجاوز, ولا يفرض WorkerPool حدا افتراضيا للطابور أو مدة المهام; تتبع جلسات Inspector طلب التنفيذ ودعم البناء الحالي
- يمكن للسكربت الرئيسي و Worker تحميل إضافات Android الأصلية عبر Node; تستخدم process.dlopen وامتداد .node السلوك الأصلي دون قوائم توقيعات أو بصمات خاصة بالإضافة; يوفر native-addon ملفات Node-API لثلاث واجهات ABI بمحاذاة 16 KB وبناء NDK/CMake دون اتصال; تعيد الملفات التالفة أو ABI غير المتوافقة أخطاء Node مثل ERR_DLOPEN_FAILED, ولا يتم تضمين NDK أو Python على الجهاز
- يدعم Inspector إصدارات Debug وRelease بعد إذن صريح من المضيف; يحتفظ inspector وinspector/promises بوحدات Node الأصلية ويدعمان Session في النصوص الرئيسية وWorker, مع الاستماع على 127.0.0.1 فقط ودون انتظار حاجب; دون إذن تعيد url() القيمة undefined ويظل التفعيل وإنشاء Session معطلين
- تقبل install/update/remove في package_manager مواصفات حزم npm وتشغل npm المرفق في مساحة العمل; تستخدم npm.cli/exec/runScript/pack سطر الأوامر الفعلي وتتبع إعدادات registry والوكيل ودورة الحياة npm وملف .npmrc للمشروع, مع بث المخرجات والاحتفاظ برموز أخطاء npm; تجهز ملفات CLI عند أول استخدام وتحتفظ طلبات الكائنات بعقد التخزين المحلي للمضيف

******

### سجل الاصدارات

******

# v1.26.0

###### 2026/10/21

* `ميزة` Node.js/Worker: قراءة كتب EPUB وتصدير الموارد والبحث وجلسات القارئ مع الأحداث

# v1.25.0

###### 2026/10/20

* `ميزة` واجهات speech للتعرف على الكلام وإدارة النماذج في Node.js وBun وWorkers مع WAV/PCM والإملاء وكلمات التنبيه وتثبيت النماذج واستيرادها والتوليف والتنظيف عند الإلغاء

# v1.24.0

###### 2026/10/20

* `ميزة` واجهات تحويل النص إلى كلام في Node.js وBun وWorkers مع استعلامات المحركات والأصوات والنطق والصمت وملفات الصوت وأحداث المهام

##### لمزيد من سجل الاصدارات

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-ar.md)

******

### البناء

******

```powershell
.\gradlew.bat :app:assembleDebug
```

بناء Release:

```powershell
.\gradlew.bat :app:assembleRelease
```

تاتي معاملات البناء من `version.properties`, الحد الادنى الحالي ل SDK هو 24 والهدف SDK هو 36.

******

### بنية الموارد

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

يحتوي `strings.xml` على اوصاف الملحق المترجمة; يحتوي `plugin_instruction.md` على تعليمات الاستخدام التي يعرضها المضيف. يتم توليد README و CHANGELOG من مصادر JSON بواسطة `.python/generate_markdown.py`.

******

### روابط

******

- وثائق AutoJs6: https://docs.autojs6.com
- مشروع Node.js الرسمي: https://github.com/nodejs/node
- خطة بناء بيئة تشغيل Node.js: tools/nodejs/runtime-build/README.md
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
