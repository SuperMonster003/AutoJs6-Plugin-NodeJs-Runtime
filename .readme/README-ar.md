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
- فُعّل `dgram` (UDP) و`http2` افتراضياً، مع إرجاع خطأ تعطيل واضح عند استخدام `trace_events`
- أُضيف تصحيح أخطاء محلي عبر `inspector` يُفعّل صراحةً في إصدارات Debug، ويستمع على localhost فقط مع الاتصال عبر `adb forward`
- يدعم مصدر CommonJS/ESM, مصادر الوحدات, دليل العمل, جذر sandbox, متغيرات البيئة, ونتائج stdout/stderr.
- يستخدم رابط V8 الأصلي لمداخل ESM و dynamic `import()` مع الحفاظ على التبعيات الدائرية وعمليات التصدير القابلة للتغيير و live binding عبر إعادة التصدير; يحتفظ CommonJS `require(esm)` بحدود التوافق المتزامن.
- يوفر وصولا شبيها بسطح المكتب إلى نظام الملفات ضمن أذونات Android لتطبيق الملحق; وتبقى `/proc` و `/sys` و `/dev` مرفوضة دائما.
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
- Intl: ICU 78 ببيانات الإعدادات المحلية الإنجليزية فقط (`--with-intl=small-icu`); يعمل `Intl` وهروب خصائص Unicode في التعبيرات النمطية ومخرجات أخطاء Node الخاصة على stderr داخل طرفية AutoJs6, ويمكن أن يشير `NODE_ICU_DATA` إلى ملف بيانات ICU كامل.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, و `universal`.
- نظام الملفات: يمكن الوصول إلى مسارات الجهاز التي تسمح بها أذونات Android; وتعد `/proc` و `/sys` و `/dev` حدودا صارمة.
- TypeScript: يقبل ناتج المضيف ويمكنه طلب ترجمة ملفات المشروع المنشأة أثناء التشغيل عبر provider v3; ويعيد الإرسال الخام المباشر `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- رابط ESM: `vm.SourceTextModule` / `vm.SyntheticModule` مع live binding أصلي وتبعيات دائرية.
- القدرات: تنفيذ سكربت متزامن, bundle transport, بيئة تشغيل اصلية مدمجة, وسيط قدرات المضيف, host capability live bridge.
- أصبحت تدفقات الملفات وتدفقات gzip/deflate/Brotli وتنفيذ VM الأساسي ميزات مستقرة; كما أصبح Debug Inspector المفعّل صراحة مستقرا ضمن localhost.
- لم يعد التنفيذ المدمج يطبع تنبيهات ExperimentalWarning; مع الاحتفاظ بأحداث warning والتحذيرات العادية وتحذيرات الإهمال والأخطاء. لم يتغير تصنيف استقرار واجهات Node أو الإعدادات الافتراضية للطرفية.

******

### سجل الاصدارات

******

# v1.5.5

###### 2026/09/17

* `اصلاح` أصبح إصدار فهرس القدرات وملخصه اللذان يبلغ عنهما runtimeInfo مشتقين من runtime kit, مما يصحح القيم القديمة 1.5.0 التي كان الإصدار 1.5.4 لا يزال يبلغ عنها
* `اصلاح` يدعم worker_threads الآن new Worker(code, { eval: true }) كما في Node بدلا من معاملة نص الشيفرة كمسار سكربت
* `تحسين` أصبح مسار ترجمة TypeScript على المضيف قدرة مستقرة في فهرس القدرات مع إزالة بيانات legacy stripping المتقاعدة; يسرد فهرس دورة الحياة أوضاع التنفيذ القابلة للتشغيل فقط مع إزالة سطح packaged_long_running والأسماء المحجوزة node_sandboxed / worker_computation; وتتطابق بيانات النقل والقبول والإلغاء مع وقت التشغيل الفعلي
* `تحسين` أصبحت تشخيصات autojs6:profile تصف وقت التشغيل الفعلي بدلا من العلامات التاريخية partial / reserved / deferred: الوصول إلى الملفات ضمن أذونات Android، ومجموعة process الفرعية، وقنوات worker، ومجمع العمليات، وقرارات WASI / native addon
* `تحسين` مراجعة الأمثلة: أصبحت packaged-esm و packaged-dynamic-import و require-esm و wasm-basic و wasm-plugin و desktop-parity-suite مستقرة؛ وتنفذ حزمتا التكافؤ مقاطعهما فعليا بدلا من طباعة نص الفهرس؛ وعُلّم compile-cache على أنه غير قابل للتطبيق وحُذف مثال package-install الذي كان يعرض بيانات وصفية فقط
* `تحسين` يتبع تحميل الوحدات وصول Android إلى الملفات كما في Node: يقبل require و import و Worker المسارات المطلقة وأهداف الدلائل الأعلى وعناوين file: (يبقى /proc و /sys و /dev مرفوضة)؛ ويظل البحث في node_modules مثبتا على مساحة العمل
* `تحسين` تتبع رسائل Worker ومراقبو fs حدود Node الأصلية: أُزيلت حدود 64 كيلوبايت للرسالة و 32 في الطابور وحصص 16 مراقبا / 64 حدثا في الثانية؛ ولا يحدها سوى ذاكرة Android
* `تحسين` داخل الـ worker ينهي process.exit() خيط الـ worker فقط كما في Node، ويتبع process.getBuiltinModule() قائمة builtin المسموحة للـ worker بدلا من تعطيله كليا
* `تحسين` تحل خيوط الـ worker محددات الحزم المجردة عبر node_modules في مساحة العمل كما في Node (شروط exports و main و index والإشارة الذاتية) بدلا من رفضها
* `تحسين` يمر import() الديناميكي داخل الـ worker عبر محمل ESM الجزئي للـ worker (المسارات المحلية وعناوين file: وحزم مساحة العمل و builtin المسموحة و with { type: "json" }) بدلا من رفضه
* `تحسين` ترقية عينة host-events إلى stable بعد اجتياز القبول اليدوي للمفتاح الفعلي والوصول إلى الإشعارات
* `تحسين` تطبع عينة scheduled-node-task سياسة دورة حياتها ويمكنها الاحتفاظ بالمهمة المجدولة لتشغيل WorkManager فعلي؛ ويحصل وضع التنفيذ scheduled على اختبار انحدار من جانب الإضافة
* `تحسين` ترقية وضع التنفيذ scheduled إلى available في فهرس القدرات بعد تشغيل فعلي لمشغّل WorkManager المجدول في المضيف عبر الإضافة
* `تحسين` تعيد media.play() جلسة تشغيل (pause/resume/seekTo/stop/status) عبر خدمة موسيقى السكربتات في المضيف، وتوفر وحدة media_store الجديدة وصولاً محدوداً إلى MediaStore (capabilities/query/get/insert/update/delete/scanFile/exportFile) خلف قدرات media.playback / media.library / media.library.mutate؛ وتكتسب autojs6:compat.media أسماء بديلة بأسلوب Rhino مثل playMusic؛ وكلاهما يتطلب بنية المضيف المطابقة
* `تحسين` تمت ترقية عينتَي media-playback / media-library إلى stable بعد القبول اليدوي مع موفري الوسائط المدمجين في المضيف؛ ونُشرت لقطة فهرس القدرات 1.5.5 تحت releases/nodejs-capability-catalog وتشير مهمة المحاذاة مع المضيف إليها الآن
* `تحسين` يتخلى غلاف fs عن قيود الخيارات الخاصة به: خيارات fs / fd الموروث / flags للتدفقات، وwatch({ recursive: true })، ومرشحات cp غير المتزامنة (يحتفظ cpSync بـ ERR_INVALID_RETURN_VALUE من Node)، وأنماط glob المطلقة / الدليل الأب / '!' الحرفية مع مصفوفات exclude، وtype / encoding في readableWebStream، ومنشئات Stats / Dirent / Dir تتبع الآن سلوك Node 24 الأصلي؛ ويبلغ filesystemProfile.advancedApis.recursiveWatch عن native
* `تحسين` أصبح readdir / opendir التكراري أصليًا (أُزيل الحد الأقصى 4096 مدخلًا وفحص realpath لكل مدخل؛ ويسرد readdir('/') أسماء proc/sys/dev كما في Node)، وتقبل readlink / chmod / chown / utimes المسارات المطلقة ويتبع chmod الروابط الرمزية، وتقلصت رموز أخطاء سياسة fs إلى ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (الحد الصارم، مشترك مع المحمّل) / ERR_AUTOJS6_FS_SCOPED_PATH بينما تحتفظ إخفاقات fs العادية برمز Node فقط
* `تحسين` أزال وقت التشغيل ميزانياته الخاصة لمصادر الوحدات (16 MiB لكل وحدة، 64 MiB إجمالًا، 8192 وحدة، عدد طلبات provider)، وتحصل نقطة دخول CommonJS على __filename / require.main.filename / process.argv[1] مطلقة كما في Node مع require.main.id يساوي '.'، وأصبح fs.mkdtemp* أصليًا: يعيد البادئة بصيغة المستدعي مع اللاحقة بالترميز المطلوب ولا يرفض بعد الآن مجلدًا أبًا يكون رابطًا رمزيًا
* `تحسين` يسلّم node:sqlite عمليات الملفات في SQL إلى SQLite الأصلي: يُفحص ATTACH ذو اسم الملف الحرفي (بما فيه file: URI) عبر مُخوِّل SQLite مقابل حدود /proc و/sys و/dev بدل مسح نص SQL، وتخضع وجهات VACUUM INTO لفحص الحدود نفسه عبر ATTACH الداخلي في SQLite، ولم تعد PRAGMA الخاصة بالمجلدات تُعترض، وتُفتح سلاسل file: URI وقاعدة البيانات المؤقتة الفارغة، ويتركّب setAuthorizer() مع فحص الحدود (يبقى مرفوضًا فقط ATTACH الذي يأتي اسم ملفه من معامل مربوط أو تعبير)

# v1.5.4

###### 2026/09/17

* `اصلاح` لم يعد التنفيذ المدمج يطبع تنبيهات ExperimentalWarning; مع الاحتفاظ بأحداث warning والتحذيرات العادية وتحذيرات الإهمال والأخطاء. لم يتغير تصنيف استقرار واجهات Node أو الإعدادات الافتراضية للطرفية
* `تحسين` أصبحت تدفقات الملفات وتدفقات gzip/deflate/Brotli وتنفيذ VM الأساسي ميزات مستقرة; كما أصبح Debug Inspector المفعّل صراحة مستقرا ضمن localhost

# v1.5.3

###### 2026/09/16

* `تحسين` نقل إذن الشبكة المحلية على Android 17 إلى مسار التفعيل وإعدادات الملحق دون صفحة إذن في المشغل; يبقى الملحق معطلا دون الإذن ويتم تخطي التشغيل التلقائي بصمت
* `تحسين` استهداف Android 17 (SDK 37) مع تحكم مستقل بإذن الشبكة المحلية للملحق وإرشادات استعادة الوصول

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
