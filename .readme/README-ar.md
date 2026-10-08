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
- أُضيف تصحيح أخطاء محلي عبر `inspector` يُفعّل صراحةً في إصدارات Debug، ويستمع على localhost فقط مع الاتصال عبر `adb forward`
- يدعم مصدر CommonJS/ESM, مصادر الوحدات, دليل العمل, جذر sandbox, متغيرات البيئة, ونتائج stdout/stderr.
- يشغّل الوحدات عبر محمّلات CommonJS / ESM الأصلية في Node.js (‎`require` و `import()` و `import.meta` و `require(esm)` وexports / imports للحزم كلها أصلية)؛ ولا يسجّل وقت التشغيل سوى `module.registerHooks` لوحدات وقت التشغيل `autojs6:*` ومصادر الوحدات التي يقدّمها المضيف وبوابة الوحدات المضمنة على مستوى الطلب (منذ 1.6.0).
- يستخدم وحدة fs الأصلية في Node.js؛ وتحدد أذونات Android لتطبيق المكوّن الإضافي وحدها إمكانية الوصول إلى الملفات، دون رفض خاص بوقت التشغيل لـ `/proc` أو `/sys` أو `/dev` (منذ 1.6.0).
- يشغّل ناتج مترجم TypeScript الذي يقدمه المضيف ويدعم عبر provider v3 ترجمة ملفات `.ts`/`.mts`/`.cts` المنشأة أثناء التشغيل عند الطلب; ويفشل الإرسال الخام المباشر دائما بشكل مغلق بعد إزالة fallback لمحو الأنواع ومفتاح التوافق.
- يوفر وسيط قدرات المضيف و live bridge مع وحدات تشغيل مثل `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, و `autojs6:bridge-permissions`.
- يتضمن مشاريع `sample/nodejs` وقائمة قدرات واجهة برمجة تطبيقات المضيف `docs/HOST-API.md`.
- بيانات الملحق, تعليمات الاستخدام, README, و CHANGELOG مترجمة للاسبانية/الفرنسية/الروسية/العربية/اليابانية/الكورية/الانجليزية/الصينية المبسطة/الصينية التقليدية لهونغ كونغ/الصينية التقليدية لتايوان.
- يوفر مشغّل الطرفية متعدد الأوامر `libnodexe.so` وأرشيف npm / corepack المعلنين عبر meta-data في manifest باسم `NODE_CLI_*`, لتتمكن طرفية AutoJs6 (المضيف 6.8.0+) من تشغيل `node` و`npm` و`npx` و`corepack` و`yarn` و`pnpm` داخل shell بمعرّف المستخدم الخاص بها.

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
- يمكن للسكربت الرئيسي و Worker تحميل إضافات Android الأصلية عبر Node; تستخدم process.dlopen وامتداد .node السلوك الأصلي دون قوائم توقيعات أو بصمات خاصة بالإضافة; يوفر native-addon ملفات Node-API لثلاث واجهات ABI بمحاذاة 16 KB وبناء NDK/CMake دون اتصال; تعيد الملفات التالفة أو ABI غير المتوافقة أخطاء Node مثل ERR_DLOPEN_FAILED, ولا يتم تضمين node-gyp أو NDK على الجهاز
- يدعم Inspector إصدارات Debug وRelease بعد إذن صريح من المضيف; يحتفظ inspector وinspector/promises بوحدات Node الأصلية ويدعمان Session في النصوص الرئيسية وWorker, مع الاستماع على 127.0.0.1 فقط ودون انتظار حاجب; دون إذن تعيد url() القيمة undefined ويظل التفعيل وإنشاء Session معطلين

******

### سجل الاصدارات

******

# v1.6.0

###### 2026/10/08

* `تحسين` fs و fs/promises و node:sqlite هي وحدات Node.js الأصلية: أُزيل رفض /proc و /sys و /dev الخاص بوقت التشغيل وطبقة التحقق من المسارات، وإمكانية الوصول إلى الملفات تحددها أذونات Android وحدها، وتعمل واجهات الملفات غير المتزامنة في مجمع خيوط libuv بدل حجب حلقة الأحداث، وتحمل أخطاء المسارات رموز Node.js الأصلية (لم تعد عمليات الملفات وقواعد البيانات تُصدر ERR_AUTOJS6_FS_NUL_BYTE و ERR_AUTOJS6_FS_PATH_ESCAPE و ERR_AUTOJS6_FS_SCOPED_PATH و ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED)
* `تحسين` process.cwd و process.chdir بسلوك Node.js الأصلي؛ يدخل التنفيذ دليل العمل عبر chdir حقيقي وتُحل المسارات النسبية بواسطة Node.js نفسه
* `تحسين` ATTACH في node:sqlite (بما في ذلك أسماء الملفات عبر معامل مربوط أو تعبير) و VACUUM INTO و PRAGMA الأدلة و setAuthorizer هي سلوك SQLite الأصلي؛ ويتبع enableLoadExtension و allowExtension فحوصات الحالة الخاصة بـ Node.js
* `تحسين` تُبنى مكتبة وقت تشغيل Node.js ببيانات ICU الكاملة (--with-intl=full-icu)، فتتطابق Intl وتنسيقات اللغات المحلية مع Node.js المكتبي ولم تعد NODE_ICU_DATA مطلوبة
* `تحسين` تقبل crypto.createPrivateKey وواجهات التوقيع مفاتيح OpenSSL STORE بصيغة عنوان file: URL (أُزيل تصحيح التعطيل الخاص بـ Android)
* `تحسين` تُعاد جميع وحدات Node.js المضمنة (cluster و domain و repl و trace_events و wasi و constants و node:sea وغيرها) كما يصدّرها Node مباشرة من قائمة module.builtinModules الخاصة به؛ وتسلك require و require.resolve و import و process.getBuiltinModule و module.isBuiltin سلوك سطح المكتب، ولا تحتفظ ببوابة على مستوى الطلب سوى worker_threads و child_process و inspector ووحدات الشبكة الأصلية؛ وتُفتح node:wasi مع إزالة حدود /proc (في libnode الحالية المبنية دون تتبع، تطلق trace_events الخطأ ERR_TRACE_EVENTS_UNAVAILABLE تماماً كما يفعل Node)
* `تحسين` يحتفظ process فقط بإدارة دورة حياة exit; تستخدم dlopen و abort و kill و umask و binding و report و features واستدعاءات هوية POSIX تنفيذ Node الأصلي; تكون os أصلية وتتبع homedir و tmpdir قيم HOME/TMPDIR وتعيد userInfo مستخدم تطبيق Android
* `تحسين` أصبحت الوحدة المضمنة module كائن Node.js الأصلي مع تجاوز createRequire فقط، فتتوفر registerHooks و enableCompileCache و findPackageJSON وغيرها؛ وأُزيلت التطبيقات المقيدة لـ http و https و net و tls و dns التي لم يكن الوصول إليها ممكناً إلا عند تعطيل الشبكة الأصلية، ويعيد هذا الوضع الآن ERR_AUTOJS6_BUILTIN_DISABLED مباشرة
* `تحسين` أصبحت supportedAbis في معلومات الإضافة (INFO) وتشخيصات وقت التشغيل تُقرأ من البيانات الوصفية org.autojs.plugin.contract.SUPPORTED_ABIS المكتوبة في البيان أثناء البناء (إضافة nativeAlignment من AutoJs6-Gradle-Platform-Versions 1.9.0 فما بعد), أي كل ABI بُنيت الإضافة لأجلها وليس التقسيم المثبّت فقط; وعند غياب البيانات الوصفية تعود إلى ABI المعبّأة في ملفات APK المثبّتة, وحُذفت ثوابت ABI المكتوبة يدويًا
* `تحسين` أصبح تحميل الوحدات عبر محمّل CommonJS / ESM الأصلي في Node.js: require و require.resolve و require.cache و require.extensions و Module._resolveFilename و module.register و import() و import.meta و require(esm) كلها أصلية, ويعمل المدخل كوحدة Node الرئيسية (require.main === module, و __filename / process.argv[1] مساران حقيقيان); لا يسجّل وقت التشغيل سوى module.registerHooks التي تقدّم وحدات وقت التشغيل autojs6:* وطبقات التوافق (مخطط autojs6:; تظل حزمة npm المثبّتة مقدّمة على الطبقة بالاسم نفسه) وبوابة الوحدات المضمنة على مستوى الطلب ومصادر الوحدات المرفقة بالطلب / المجسّدة من المزوّد / من لقطة TypeScript والمصدر المضمّن للمدخل; أُزيل رابط vm.SourceTextModule والمحلّل الخاص وإعادة كتابة import() الديناميكي وحراس eval / Function, ولم يعد لمفتاحي الطلب esmEnabled / dynamicImportEnabled أي أثر
* `تحسين` تدعم خيوط Worker الأصلية التداخل وواجهات MessagePort و stdio و argv و execArgv وتوارث التحميل المسبق; أزيلت حدود الإضافة لعدد الخيوط ووقت بدء التشغيل, وأصبحت resourceLimits قيما افتراضية قابلة للتجاوز, ولا يفرض WorkerPool حدا افتراضيا للطابور أو مدة المهام; تتبع جلسات Inspector طلب التنفيذ ودعم البناء الحالي
* `تحسين` تتوفر autojs6:bridge.callAutoJs داخل Worker والخيوط المتداخلة عبر MessagePort الأب مع الحفاظ على أذونات وسيط المضيف وأخطائه ومهلاته وإلغاء الطلبات; يوضح مثال worker-host-bridge قراءة معلومات الجهاز الفعلية وتتوفر تعريفات TypeScript لواجهة الجسر
* `تحسين` لا تزال نقاط الدخول والوحدات المستوردة .ts / .mts / .cts تتطلب JavaScript ينتجه Compiler المضيف; تم الحفاظ على ربط اللقطات والترجمة عند الطلب عبر provider v3. تحل أخطاء Node الأصلية محل رموز أخطاء AutoJs6 السابقة الخاصة بـ ESM والاستيراد الديناميكي.
* `تحسين` يمكن للسكربت الرئيسي و Worker تحميل إضافات Android الأصلية عبر Node; تستخدم process.dlopen وامتداد .node السلوك الأصلي دون قوائم توقيعات أو بصمات خاصة بالإضافة; يوفر native-addon ملفات Node-API لثلاث واجهات ABI بمحاذاة 16 KB وبناء NDK/CMake دون اتصال; تعيد الملفات التالفة أو ABI غير المتوافقة أخطاء Node مثل ERR_DLOPEN_FAILED, ولا يتم تضمين node-gyp أو NDK على الجهاز
* `تحسين` يدعم Inspector إصدارات Debug وRelease بعد إذن صريح من المضيف; يحتفظ inspector وinspector/promises بوحدات Node الأصلية ويدعمان Session في النصوص الرئيسية وWorker, مع الاستماع على 127.0.0.1 فقط ودون انتظار حاجب; دون إذن تعيد url() القيمة undefined ويظل التفعيل وإنشاء Session معطلين

# v1.5.7

###### 2026/10/05

* `تحسين` تستخدم أيقونات مركز الملحقات الأحجام والمواضع والصور الفاتحة والداكنة والخلفيات الدائرية المعدلة في Icon Studio مع الاحتفاظ بالمصادر والمعلمات لإعادة إنتاجها
* `تحسين` تستخدم أيقونات معلومات التطبيق في Android صور Icon Studio وخلفياته الفاتحة والداكنة مع الحفاظ على الصور الشفافة في مركز الملحقات وخيارات المشغل الحالية

# v1.5.6

###### 2026/09/19

* `ميزة` عند حدوث خطأ فادح في Node.js داخل فتحة وقت التشغيل (نفاد ذاكرة كومة JavaScript، فشل فحص داخلي) يُترك الآن تقرير التشخيص الخاص بـ Node في ذاكرة التخزين المؤقت للإضافة (crash/<executionId>.json، يُكتب قبل الإيقاف؛ لا يُثبَّت أي معالج إشارات): تحمل نتيجة الفشل الحدث وأرقام الكومة ومكدس JS والمسار في slotExit.crash وفي رسالة الخطأ (مثلاً CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json))، ويعرض getRuntimeInfo أحدث تقرير باسم lastCrash حتى بعد إعادة تشغيل الموزّع؛ كما يحدّ خيار الطلب الجديد maxOldGenerationSizeMb كومة JS لتنفيذ واحد (الجيل القديم في V8، بالميغابايت)، وهو أيضاً ما يستخدمه اختبار الانحدار لإعادة إنتاج هذا الانهيار
* `اصلاح` تم إصلاح توقف فتحة وقت التشغيل بسبب نفاد ذاكرة كومة V8 (يُبلغ عنه كـ CRASH_NATIVE / SIGABRT) عند require لوحدة CommonJS كبيرة: كان إخفاء التعليقات والسلاسل في المحمّل يضيف حرفاً واحداً في كل مرة، فاحتاجت وحدة بحجم 22 MiB إلى نحو 700 MB من الكومة؛ أصبح الآن يُخرج مقاطع متصلة بالنتيجة نفسها (نحو 44 MB)، كما صار فحص import / export في CommonJS يقفز بين المرشحين بدلاً من فحص كل حرف
* `اصلاح` تحذيرات قراءة SDK XML v4 مع AGP 9.1 وتشغيل فحص محاذاة مكتبات APK الأصلية خطأ عند تجميع اختبارات JVM, باستخدام إضافات البناء المشتركة 1.8.3

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
