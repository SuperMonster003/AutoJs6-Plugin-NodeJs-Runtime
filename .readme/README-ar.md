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
- يدعم مصدر CommonJS/ESM, مصادر الوحدات, دليل العمل, جذر sandbox, متغيرات البيئة, ونتائج stdout/stderr.
- يستخدم رابط V8 الأصلي لمداخل ESM و dynamic `import()` مع الحفاظ على التبعيات الدائرية وعمليات التصدير القابلة للتغيير و live binding عبر إعادة التصدير; يحتفظ CommonJS `require(esm)` بحدود التوافق المتزامن.
- يوفر وصولا شبيها بسطح المكتب إلى نظام الملفات ضمن أذونات Android لتطبيق الملحق; وتبقى `/proc` و `/sys` و `/dev` مرفوضة دائما.
- يشغّل ناتج مترجم TypeScript الذي يقدمه المضيف; وتفشل ملفات `.ts`/`.mts`/`.cts` الخام بشكل مغلق افتراضيا, أما محو الأنواع legacy فهو خيار صريح للترحيل فقط.
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
- TypeScript: يقبل ناتج الترجمة فقط افتراضيا; ويعيد TypeScript الخام `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- رابط ESM: `vm.SourceTextModule` / `vm.SyntheticModule` مع live binding أصلي وتبعيات دائرية.
- القدرات: تنفيذ سكربت متزامن, bundle transport, بيئة تشغيل اصلية مدمجة, وسيط قدرات المضيف, host capability live bridge.

******

### سجل الاصدارات

******

# v1.2.0

###### 2026/08/26

* `ميزة` تفعيل وصول شبيه بسطح المكتب إلى نظام الملفات ضمن أذونات Android مع استمرار رفض `/proc` و `/sys` و `/dev`
* `ميزة` إضافة `accessibility.swipe` و `accessibility.gesture` خلف القدرة المستقلة `accessibility.gesture`
* `اصلاح` أصبح TypeScript الخام يفشل بشكل مغلق ما لم يقدم المضيف ناتج المترجم, مع ربط dynamic import في snapshot وتوحيد إطارات المكدس المولدة والمستوردة
* `اصلاح` استُبدل محول ESM الجزئي القائم على snapshot برابط V8 الأصلي, مع إصلاح عدم تحديث عمليات التصدير القابلة للتغيير عبر إعادة التصدير الدائرية
* `تحسين` مواءمة عقد v2 بين المضيف والملحق وبيانات القدرات وحدود مسؤولية بيئة التشغيل plugin-only
* `تحسين` تجميع أمثلة Node.js وتعريفات TypeScript ومعالج المشاريع وقيم runtime الافتراضية وفحوصات التوافق مع المضيف في مستودع الملحق, مع إزالة مفاتيح Gradle والنسخ المكررة من أصول التطوير من المضيف

# v1.1.0

###### 2026/08/18

* `ميزة` إضافة بث stdout/stderr المباشر والإلغاء التعاوني عبر `node::Stop`
* `ميزة` استبدال رفض BUSY بطابور تسلسلي محدود لثلاثة منتظرين وإضافة دورة حياة السكربتات المقيمة طويلة التشغيل
* `ميزة` تفعيل وحدات شبكة Node الأصلية و `worker_threads` و `child_process` افتراضيا, والتحقق من عشرة حزم npm شائعة مكتوبة بجافاسكربت خالص
* `تحسين` إضافة مساحات عمل direct-run وتفاوض متسامح v1..v2 مع provider مصادر الوحدات وأكواد أخطاء موجزة ومكدسات JavaScript

# v1.0.0

###### 2026/07/18

* `ميزة` تمت اضافة خدمة ملحق بيئة تشغيل Node.js مع معرف الملحق `nodejs`, المحرك `nodejs`, وفتحة التشغيل `node24_5`
* `ميزة` تم توفير بيئة تشغيل Node.js 24.5.0 الاصلية عبر `libnode.so` و `libautojs6-node.so`
* `ميزة` تشغيل بيئة Node.js في عملية دائمة مستقلة تعيد استخدام حالة Node/V8 العامة للعملية مع انشاء isolate و Environment جديدين لكل تنفيذ
* `ميزة` تمت اضافة اكتشاف معلومات الملحق عبر `org.autojs.plugin.INFO` واستدعاء بيئة التشغيل عبر `org.autojs.plugin.nodejs.RUNTIME`
* `ميزة` تم دعم مصدر CommonJS/ESM, مصادر الوحدات, دليل العمل, جذر sandbox, متغيرات البيئة, نتائج stdout/stderr, وتسخين بيئة التشغيل
* `ميزة` دعم نقل ارشيف مساحة العمل v2 لكل طلب, مع تعيين صريح للمدخلات والتنفيذ في مساحة عمل خاصة بالملحق واعادة كتابة المخرجات وبيانات tombstone للحذف من دون فحص sandbox المضيف
* `ميزة` قبول تنفيذ نشط واحد بلا طابور مع ضغط عكسي `ERR_AUTOJS6_NODE_PLUGIN_BUSY` والغاء عبر اعادة تشغيل العملية
* `ميزة` تمت اضافة وسيط قدرات المضيف و live bridge مع وحدات تشغيل مثل `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config`, و `autojs6:bridge-permissions`
* `ميزة` تمت اضافة builds APK مقسمة حسب ABI ل `arm64-v8a`, `armeabi-v7a`, `x86_64`, و APK `universal`
* `ميزة` تحزيم `libc++_shared.so` مع مكتبات بيئة Node.js في مخرجات APK المقسمة و `universal`
* `ميزة` تمت اضافة مشاريع `sample/nodejs`, اداة تشخيص Node resolver, وادوات التحقق من خطة بناء بيئة التشغيل
* `ميزة` تمت اضافة بيانات الملحق, تعليمات الاستخدام, README, وموارد CHANGELOG المترجمة للاسبانية/الفرنسية/الروسية/العربية/اليابانية/الكورية/الانجليزية/الصينية المبسطة/الصينية التقليدية لهونغ كونغ/الصينية التقليدية لتايوان
* `اصلاح` قد يؤدي الالغاء عبر اعادة تشغيل العملية الى اعتماد لقطة جزئية لمساحة العمل اثناء انهاء عملية بيئة التشغيل
* `اصلاح` قد تتسرب واصفات ملفات نقل ارشيف مساحة العمل عند فشل التحقق من عقد الطلب او تهيئة مساحة العمل لان نقل الملكية لم يكتمل في جميع مسارات الخروج
* `تحسين` بيانات وتشخيصات عقد R5 ل ABI والقدرات وحالة بيئة التشغيل الدائمة والقبول والالغاء واسناد العملية المستقلة
* `تحسين` اضافة تشخيصات مرحلية تعتمد ساعة رتيبة لبناء مصدر التنفيذ والتمهيد وتنفيذ النص البرمجي وانشاء النتيجة واستردادها والتنظيف لكل تنفيذ, مع التمييز بين حالتي التخطي وغير قابل للتطبيق

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
