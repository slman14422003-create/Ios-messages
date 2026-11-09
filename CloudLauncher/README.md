# Cloud Launcher

لانشر أندرويد (Kotlin + Jetpack Compose) بواجهة سوداء، مع قائمة "الأخيرة المخصصة" وحماية التطبيقات عبر الروت.
Package: `com.cloud.launcher` — minSdk 24 / targetSdk 34.

## البناء
- **Android Studio:** افتح المجلد، دعه يزامن Gradle (يحتاج JDK 17)، ثم Run.
- **GitHub Actions:** ارفع المشروع، وسيبني `.github/workflows/build.yml` ملف APK (debug) ويرفعه كـ Artifact.
- ملاحظة: لا يوجد `gradle-wrapper.jar` داخل الملف. Android Studio ينشئه تلقائياً، أو شغّل `gradle wrapper --gradle-version 8.7`.

## خطوات التشغيل
1. ثبّت الـ APK وافتحه. ستظهر نافذة **الإعداد الأولي**.
2. **الروت:** اضغط "منح" ووافق في Magisk.
3. **Usage Access:** اضغط "منح" وفعّل Cloud Launcher في قائمة الوصول إلى بيانات الاستخدام.
4. **خدمة الوصول:** فعّل "Cloud Launcher" في إعدادات إمكانية الوصول (لتتبع التطبيقات المفتوحة).
5. **الإشعارات:** اسمح بها (مطلوبة لخدمة الحماية الدائمة على Android 13+).
6. **لانشر افتراضي:** زر "تعيين" ثم اختر Cloud Launcher واضغط زر Home.
7. (اختياري) System App: انقل الـ APK إلى `/system/priv-app/CloudLauncher/` بموديول Magisk مثل Systemless Debloater/MagiskHidePropsConf أو يدوياً مع ضبط الصلاحيات 644، ثم أعد التشغيل.

## الاستخدام
- اسحب للأعلى في الرئيسية لفتح المكتبة. اضغط مطولاً على أي تطبيق لإضافته للرئيسية أو الحماية أو الأخيرة.
- **الأخيرة:** X لإغلاق التطبيق قسرياً، قفل لحمايته، سحب الصف لإزالته وإغلاقه، "إغلاق الكل" يستثني المحمية.
- **المحمية:** حتى 5 تطبيقات. الخدمة تراقب كل 3 ثوانٍ وتعيد `oom_score_adj=-1000` وتعيد تشغيل التطبيق إذا توقف.

## قائمة التحقق
- [x] Launcher (HOME + DEFAULT) — [x] مكتبة + بحث + ترتيب + قائمة طويلة
- [x] الأخيرة المخصصة (PID/RAM، X، Kill All، Pin، Swipe)
- [x] حماية الروت (oom_adj / oom_score_adj / renice / deviceidle / standby-bucket)
- [x] Foreground Service كل 3 ثوانٍ — [x] AccessibilityService — [x] BootReceiver
- [x] DataStore — [x] حد 5 تطبيقات محمية — [x] Android 7 → 14

## التوقيع (Release)
- المفتاح `cloud-release.jks` (alias: `cloud`) صلاحيته 40 سنة، وبياناته في `keystore.properties`.
- **احتفظ بالمستودع Private** لأن المفتاح وكلمة السر داخله. للأمان الأفضل احذفهما من المستودع واستخدم GitHub Secrets (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
- الأمر: `gradle assembleRelease` والناتج في `app/build/outputs/apk/release/`.
- لا تفقد الملف: بدونه لا يمكن تحديث التطبيق فوق نسخة موقّعة به.
