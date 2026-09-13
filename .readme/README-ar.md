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

يوفر ملحق AutoJs6 Node.js Runtime بيئة تشغيل Node.js 24.21.0 اصلية مدمجة ل AutoJs6 من اجل سكربتات Node.js ومهام تشغيل الملحقات.

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

- فتحة بيئة التشغيل: `node24_21`.
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

# v1.4.2

###### 2026/09/13

* `اصلاح` عرض واجهات ABI الأصلية الموجودة فعليا في ملف APK المثبت فقط
* `اصلاح` استخدام تواريخ بناء باللغة الإنجليزية في بيانات المكون بغض النظر عن لغة جهاز البناء
* `اصلاح` اتساق معلومات الإصدار بين الحزمة وملفات الإصدار
* `اصلاح` توافق ملفات مساحة العمل مع Android 7 مع الحفاظ على عزل واصفات الملفات

# v1.4.1

###### 2026/09/13

* `تحسين` التحقق أثناء البناء من محاذاة صفحات 16 KB للمكتبات الأصلية ذات 64 بت, مع فحص عقد manifest وتقارير JSON
* `تحسين` توحيد تنشيط المضيف وبيانات الإضافة والوثائق المترجمة وتجميع إصدارات APK الموقعة وفق قواعد الإضافات المشتركة

# v1.4.0

###### 2026/09/10

* `ميزة` تدعم استعلامات MediaInfo فهرس streamNumber من الصفر وعدد المسارات عبر countGet وinfoKind للوحدات والأوصاف والأسماء المقروءة; يحافظ Rhino وNode على TEXT للمسار الأول افتراضيا مع التحقق من قدرات الإضافة
* `اصلاح` تدعم مسارات ملفات MediaInfo والصور المسارات المطلقة والمجلدات الأصلية وأسماء الملفات الصالحة; تستخدم مخرجات التسجيل قواعد وصول Android نفسها مع المضيف المحدث
* `اصلاح` توضح أخطاء صلاحيات الجسر الآن تصريحات node.permissions المفقودة بدلا من طلب ملف pro_compat_opt_in التشخيصي الذي لا يمنح الصلاحيات
* `اصلاح` لم يعد مدقق المشاريع يرفض مسارات fs المطلقة أو المجلدات الأصلية بوصفها FS_OUTSIDE_SCOPE; يحدد Android الوصول الفعلي للملفات
* `تحسين` مشاريع مستقلة لأحداث Android وتسجيل صوتي لمدة ثلاث ثوان, مع تعريفات المشروع وخطوات اختبار يدوي لالتقاط الشاشة و OCR والأزرار الفعلية و MediaInfo
* `تحسين` اجتاز البحث في لقطات الشاشة وOCR وتسجيل AAC لمدة ثلاث ثوان التحقق اليدوي على جهاز فعلي; أصبحت الأمثلة ومقاطع توافق Pro التي تستخدم الاستدعاءات نفسها مستقرة
* `تحسين` يوضح مثال اختبار الأحداث كيفية تعطيل اختصار الإيقاف بزر رفع الصوت مؤقتا; يقرأ المضيف المحدث node.timeoutMs من project.json للسماح بانتظار يتجاوز خمس ثوان

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
