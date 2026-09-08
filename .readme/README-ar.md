<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>ملحق تشغيل Node.js 24.5.0 الاصلي ل AutoJs6</p>

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

يوفر ملحق AutoJs6 Node.js Runtime بيئة تشغيل Node.js 24.5.0 اصلية مدمجة ل AutoJs6 من اجل سكربتات Node.js ومهام تشغيل الملحقات.

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

- فتحة بيئة التشغيل: `node24_5`.
- معرف الملحق: `nodejs`, المحرك: `nodejs`.
- اجراء خدمة بيئة التشغيل: `org.autojs.plugin.nodejs.RUNTIME`.
- مكتبات بيئة التشغيل الاصلية: `libnode.so` و `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`, و `universal`.
- نظام الملفات: يمكن الوصول إلى مسارات الجهاز التي تسمح بها أذونات Android; وتعد `/proc` و `/sys` و `/dev` حدودا صارمة.
- TypeScript: يقبل ناتج المضيف ويمكنه طلب ترجمة ملفات المشروع المنشأة أثناء التشغيل عبر provider v3; ويعيد الإرسال الخام المباشر `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- رابط ESM: `vm.SourceTextModule` / `vm.SyntheticModule` مع live binding أصلي وتبعيات دائرية.
- القدرات: تنفيذ سكربت متزامن, bundle transport, بيئة تشغيل اصلية مدمجة, وسيط قدرات المضيف, host capability live bridge.

******

### سجل الاصدارات

******

# v1.3.0

###### لم يُنشر بعد

* `ميزة` اشتراكات جسر Node.js تدفع أحداث المستشعرات وWebSocket والواجهة والنوافذ العائمة ومراقب الإدخال عبر callbacks الحالية، مع on/once/off وطوابير محدودة وتوافق drainEvents
* `ميزة` خيار idleExitMs لإنهاء عملية Node.js الخاملة وإعادة الاتصال للبرنامج النصي التالي, مع تشخيص idleForMs والإبقاء على العملية مقيمة افتراضيا
* `ميزة` تستخدم fetch و Request و Response و Headers و FormData و WebSocket العامة واجهات Node الأصلية; تحتفظ وحدتا autojs6:fetch و autojs6:websocket بمكدس شبكة المضيف
* `ميزة` إضافة node:sqlite الأصلي مع فحص مسارات الملفات وعمليات CRUD والمعاملات والنسخ الاحتياطي؛ تقارير node:test الأصلية ومثال قابل للتشغيل، وإضافة zod وcheerio وdate-fns وmqtt وws إلى مجموعة npm للاختبار دون اتصال
* `ميزة` إدخال وحدة التحكم عبر process.stdin / readline ورسائل JSON لكل تشغيل عبر autojs6:host مع توافق postMessage v3 مع المضيفين السابقين
* `اصلاح` إصلاح تراكم سجلات طلبات الجسر واستجاباته في البرامج النصية المقيمة، مع إزالة الطلبات المكتملة والاحتفاظ بآخر 32 استجابة فقط ضمن حد لحجم بيانات التشخيص
* `اصلاح` إصلاح نتائج النجاح المبكرة التي كانت تفقد الأخطاء غير المتزامنة الأصلية ورموز الخروج المتأخرة; تكتمل النتيجة عند الخروج النهائي من حلقة Node
* `تحسين` تقليل زمن استجابة الجسر باستخدام نقل JNI/Binder افتراضي وردود عبر حلقة أحداث Node، مع الإبقاء على النقل بالملفات وحدود الاستدعاءات المعلقة
* `تحسين` استعادة صادرات Node.js الأصلية لوحدات stream و crypto و timers و util و node:test وغيرها مع الحفاظ على حدود نظام الملفات وسياسة مجلدات المضيف
* `تحسين` تستخدم وحدات worker الأصلية توازي المعالج افتراضيًا حتى ثماني وحدات، وترث إعدادات الشبكة والملفات وتدعم حدود الموارد لكل طلب؛ أزيلت المهلة الافتراضية لمهام التجمع وأصبحت أمثلة CPU وWASM تنفذ عملًا فعليًا
* `تحسين` يظل WASI الأصلي معطلا للحفاظ على حدود نظام الملفات مع إزالة مثالي WASI المعطلين; يظل WebAssembly العادي وعمال WASM متاحين

# v1.2.0

###### 2026/08/29

* `ميزة` تمت إضافة `capabilities()` إلى واجهة `mediainfo` وإتاحة الاختيار الصريح للقطة الملحق v1/v2 في المدخل القابل للاستدعاء و`read()`; عند إغفال المخطط يستمر إرجاع لقطة Node v1 الحالية المملوكة للمضيف
* `ميزة` أضيف module-source provider v3 لترجمة ملفات `.ts/.mts/.cts` التي تنشأ أثناء التشغيل عند الطلب عبر PFD محدود وSHA-256, مع ميزانية ترجمة مستقلة قدرها 30 s, ورفض ثابت لهروب المسار والروابط الرمزية والغموض, وتشخيصات TypeScript وخرائط Source Map من المضيف
* `ميزة` تفعيل وصول شبيه بسطح المكتب إلى نظام الملفات ضمن أذونات Android مع استمرار رفض `/proc` و `/sys` و `/dev`
* `ميزة` إضافة `accessibility.swipe` و `accessibility.gesture` خلف القدرة المستقلة `accessibility.gesture`
* `ميزة` أُضيفت مهلة للبرامج النصية تشمل الانتظار والتنفيذ وتُرجع `ERR_AUTOJS6_SCRIPT_TIMEOUT` عند تجاوزها؛ وتستمر البرامج دون مهلة محددة في العمل بلا حد زمني
* `ميزة` فُعّل `dgram` (UDP) و`http2` افتراضياً، مع إرجاع خطأ تعطيل واضح عند استخدام `trace_events`
* `ميزة` أُضيف تصحيح أخطاء محلي عبر `inspector` يُفعّل صراحةً في إصدارات Debug، ويستمع على localhost فقط مع الاتصال عبر `adb forward`
* `ميزة` أُضيف مدخل تفعيل بلا واجهة محمي بإذن إضافات المضيف، واكتملت أوصاف مركز الإضافات وعُطّل النسخ الاحتياطي لبيانات التطبيق
* `اصلاح` أصبح TypeScript الخام يفشل بشكل مغلق ما لم يقدم المضيف ناتج المترجم, مع ربط dynamic import في snapshot وتوحيد إطارات المكدس المولدة والمستوردة
* `اصلاح` استُبدل محول ESM الجزئي القائم على snapshot برابط V8 الأصلي, مع إصلاح عدم تحديث عمليات التصدير القابلة للتغيير عبر إعادة التصدير الدائرية
* `اصلاح` إصلاح استيرادات ESM لواجهات توافق AutoJs6 وفحص لاحقات TypeScript غير الموجودة مع الحفاظ على أولوية حزم npm المثبتة
* `تحسين` أزيل fallback legacy لمحو TypeScript القائم على regex ومفتاح الطلب الخاص به; وأصبحت ملفات `.ts/.mts/.cts` الخام تتطلب دائما ناتج مترجم المضيف
* `تحسين` مواءمة عقد v2 بين المضيف والملحق وبيانات القدرات وحدود مسؤولية بيئة التشغيل plugin-only
* `تحسين` تجميع أمثلة Node.js وتعريفات TypeScript ومعالج المشاريع وقيم runtime الافتراضية وفحوصات التوافق مع المضيف في مستودع الملحق, مع إزالة مفاتيح Gradle والنسخ المكررة من أصول التطوير من المضيف
* `تحسين` وُسعت تغطية npm المتحقق منها إلى 15 حزمة بإضافة axios وexpress وحزم ESM-only وهي nanoid وp-limit وyocto-queue
* `تحسين` أصبح حقل الطلب `executionMode` المصدر المعتمد لوضع دورة الحياة، وأُعلن إهمال الحقل `runtimeAdapter` الذي لا يؤثر في التنفيذ
* `تحسين` أُزيلت عشرة أمثلة قديمة كانت تطبع حالات ثابتة فقط، ووُضح في تعريفات الأنواع عدم توفر مزودي المضيف لالتقاط الشاشة وتحليل الصور والتسجيل

# v1.1.0

###### 2026/08/18

* `ميزة` إضافة بث stdout/stderr المباشر والإلغاء التعاوني عبر `node::Stop`
* `ميزة` استبدال رفض BUSY بطابور تسلسلي محدود لثلاثة منتظرين وإضافة دورة حياة السكربتات المقيمة طويلة التشغيل
* `ميزة` تفعيل وحدات شبكة Node الأصلية و `worker_threads` و `child_process` افتراضيا, والتحقق من عشرة حزم npm شائعة مكتوبة بجافاسكربت خالص
* `تحسين` إضافة مساحات عمل direct-run وتفاوض متسامح v1..v2 مع provider مصادر الوحدات وأكواد أخطاء موجزة ومكدسات JavaScript

##### لمزيد من سجل الاصدارات

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-ar.md)

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
