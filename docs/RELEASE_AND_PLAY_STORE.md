# إعداد إصدارات صحتي والتحديثات

## ثوابت لا يجوز تغييرها بعد أول إصدار عام

- Android applicationId: `com.mkassis37.sehati`
- استخدم ملف توقيع Release نفسه لكل إصدارات APK المباشرة.
- لا تحذف ملف keystore ولا تنشئ مفتاحًا جديدًا عند فقدانه؛ احتفظ بنسختين مشفرتين في مكانين آمنين.
- ارفع `versionCode` في كل إصدار جديد، وحدث `versionName` بصورة مفهومة.
- لا ترفع ملف keystore أو كلمات المرور إلى Git أو Issues أو محادثات عامة.

## إنشاء مفتاح التوقيع مرة واحدة

أنشئ المفتاح على جهازك الشخصي الموثوق، وليس داخل المستودع. استخدم كلمة مرور قوية وفريدة، واحتفظ بها في مدير كلمات مرور. مثال الأمر (سيطلب keytool كلمات المرور تفاعليًا):

```bash
keytool -genkeypair -v -keystore sehati-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias sehati
```

حوّل الملف إلى Base64 على جهازك:

- macOS/Linux: `base64 -w 0 sehati-release.jks`
- PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("sehati-release.jks"))`

لا ترسل قيمة Base64 أو كلمات المرور في المحادثة. أضف القيم التالية من إعدادات المستودع في GitHub:
Settings → Secrets and variables → Actions → New repository secret

| Secret | القيمة |
|---|---|
| ANDROID_KEYSTORE_BASE64 | محتوى Base64 لملف JKS |
| ANDROID_KEYSTORE_PASSWORD | كلمة مرور ملف JKS |
| ANDROID_KEY_ALIAS | `sehati` إذا استخدمت الاسم أعلاه |
| ANDROID_KEY_PASSWORD | كلمة مرور المفتاح |

بعد ضبط الأسرار، يؤدي دفع tag مثل `v0.4.0` إلى تشغيل Android Release workflow، الذي يبني APK موقّعًا وAAB موقّعًا، ويتحقق من توقيع APK، ثم ينشئ GitHub Release مع ملفات SHA-256.

## التحديث من GitHub حاليًا

الـ Release يوفر APK مباشرًا. يحتاج التطبيق نفسه إلى ميزة فحص GitHub Releases، مقارنة versionCode، تنزيل APK، ثم تشغيل مثبت Android باستخدام URI آمن وطلب موافقة المستخدم. يجب إضافة هذه الميزة واختبارها قبل الادعاء بأن التحديث داخل التطبيق يعمل. لا يمكن تجاوز موافقة Android لتثبيت APK يدويًا.

## Google Play لاحقًا

- استخدم AAB الناتج من Release workflow لرفعه إلى Play Console.
- لا تغيّر applicationId.
- خطط مبكرًا لتوقيع Play App Signing: الانتقال من APK مثبت مباشرة إلى تطبيق Play يتطلب توافق شهادة التوقيع. لا تسجّل مفتاحًا مختلفًا في Play App Signing إذا كان المطلوب ترقية النسخة الجانبية الحالية دون إزالة التطبيق، إلا بعد التحقق من مسار ترحيل الشهادة المدعوم.
- أكمل متطلبات Play Console وسياسات الخصوصية وإفصاحات البيانات الصحية قبل النشر.

## توافق Android 16 وGoogle Play

- المشروع يستهدف `compileSdk = 36` و`targetSdk = 36`، ويستخدم Android Gradle Plugin 8.9.1 وGradle 8.11.1.
- اختبارات CI تعمل على محاكي API 36.
- يجب فحص APK/AAB الناتج من إصدار Release للتأكد من توافق مكتبات native مع صفحات ذاكرة 16 KB، خصوصًا OpenCV وSQLCipher وTesseract، قبل النشر.
- تغيير target API يفرض مراجعة edge-to-edge وشريط الحالة والتنقل الخلفي التنبؤي على Android 16.

## الوضع الحالي

إضافة workflow وحدها لا تنشئ مفتاح توقيع ولا تفعّل التحديث داخل التطبيق. يجب أولًا أن ينشئ مالك التطبيق المفتاح ويحفظ أسراره في GitHub. لا تصدر أول نسخة Release قبل اختبار استمرارية التوقيع والتحديث فوق نسخة مثبتة مع التأكد من بقاء البيانات المحلية.
