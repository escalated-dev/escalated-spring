<p align="center"><sub>
  <b>العربية</b> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.ko.md">한국어</a> ·
  <a href="README.nl.md">Nederlands</a> ·
  <a href="README.pl.md">Polski</a> ·
  <a href="README.pt-BR.md">Português (BR)</a> ·
  <a href="README.ru.md">Русский</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.zh-CN.md">简体中文</a>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  أضف مكتب دعم متكاملًا إلى تطبيق Spring Boot الخاص بك.<br>
  تذاكر، واتفاقيات مستوى الخدمة (SLA)، وقواعد تصعيد، وقاعدة معرفة، وأداة دعم، كتبعية واحدة تعمل على قاعدة بياناتك الخاصة.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>الموقع</b></a> ·
  <a href="https://escalated.dev/docs"><b>التوثيق</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>سجل التغييرات</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="صندوق الوارد للوكلاء في Escalated: عروض محفوظة مع أعدادها، وعدّاد تنازلي لـ SLA وأولوية على كل تذكرة" width="100%">
</picture>

## ما الذي يقدمه

<table>
  <tr>
    <td width="40%">
      <h3>صندوق وارد يمكن لوكلائك العمل فيه طوال اليوم</h3>
      المحادثة والملاحظات الداخلية والردود الجاهزة ومؤقتات SLA في شاشة واحدة. تصل التذاكر أيضًا عبر البريد الإلكتروني من خلال Postmark أو Mailgun أو AWS SES.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="تذكرة مفتوحة في مساحة عمل الوكيل مع محرر الرد">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="صفحة التقارير في لوحة الإدارة مع أوقات الاستجابة وCSAT وحالات خرق SLA">
      </picture>
    </td>
    <td width="40%">
      <h3>اتفاقيات SLA وقواعد تصعيد واضحة أمامك</h3>
      أهداف للاستجابة والحل مع مراعاة ساعات العمل والعطلات، وقواعد تصعّد التذكرة أو تعيد إسنادها عند الخرق، وتقارير SLA.
    </td>
  </tr>
</table>

## الميزات

<table>
<tr>
<td width="33%" valign="top">

**التذاكر وصندوق الوارد**
- الحالات والأولويات والإسناد
- وحدات الماكرو والردود الجاهزة
- محادثات جانبية داخل التذاكر
- دمج التذاكر وربطها وتقسيمها
- تأجيل مع إعادة تنشيط تلقائية
- عروض محفوظة لكل وكيل
- حقول مخصصة بأنواع متعددة

</td>
<td width="33%" valign="top">

**SLA والتوجيه**
- سياسات SLA مع ساعات العمل والعطلات
- التصعيد عند الخرق أو التقادم أو عدم الرد
- إعادة الإسناد أو تغيير الأولوية أو نقل القسم
- قواعد الإغلاق التلقائي والإسناد التلقائي
- توجيه حسب المهارات
- حدود لعبء العمل لكل وكيل

</td>
<td width="33%" valign="top">

**البريد الإلكتروني**
- بريد وارد من Postmark وMailgun وAWS SES
- تحديد التذكرة عبر Reply-To موقّع
- قبول الردود من مقدم الطلب فقط
- رسائل HTML بهوية علامتك التجارية عبر Thymeleaf
- تسلسل الرسائل حسب Message-ID

</td>
</tr>
<tr>
<td width="33%" valign="top">

**العملاء**
- واجهة API عامة للأداة
- وصول الضيوف إلى التذاكر عبر رمز
- استطلاعات CSAT مع وصول عبر رمز
- قاعدة معرفة مع بحث وتقييمات

</td>
<td width="33%" valign="top">

**الإدارة والأمان**
- الأدوار والصلاحيات
- مصادقة ثنائية TOTP للموظفين
- تسجيل الدخول عبر SAML 2 وOAuth 2 / OIDC وJWT
- سجل تدقيق لكل إجراء
- رموز API مُجزّأة (hashed)

</td>
<td width="33%" valign="top">

**التوسيع والتكامل**
- Webhooks موقّعة بـ HMAC مع إعادة المحاولة
- استيراد التذاكر بالجملة
- إضافات Java أصلية وإضافات SDK
- WebSocket عبر STOMP/SockJS (اختياري)
- واجهة Vue 3 + Inertia مشتركة (اختيارية)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">كل الميزات بالتفصيل</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## الميزات

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.escalated:escalated-spring:0.2.13")
}
```

ثم أضف `implementation("dev.escalated:escalated-spring:0.2.13")` مع `mavenLocal()` إلى تطبيقك واضبط `escalated.enabled=true`. يتطلب Java 17+ وSpring Boot 4.1. يشرح [دليل البدء](../getting-started.md) الإعدادات وقاعدة البيانات وتسجيل الدخول، وتُفعّل [صفحات Inertia](../ui.md) الشاشات المشتركة عبر `escalated.ui.enabled=true`.

## لوسوم والمرفقات وسجل النشاط

- [كل الميزات](../features.md) · [البدء](../getting-started.md) · [صفحات Inertia](../ui.md)
- [التخصيص والترجمة والتحديثات الفورية](../customization.md) · [البنية](../architecture.md) · [نقاط نهاية API](../api-endpoints.md)
- [التحكم في الوصول](../access-control.md) · [الوصول إلى التذاكر وتوجيهها](../ticket-access-and-routing.md) · [الحقول المخصصة](../custom-fields.md)
- [البريد الوارد](../inbound-email.md) · [وصول الضيوف](../guest-access.md) · [تضمين الأداة](../embedding-the-widget.md)
- [تضمين لوحة الإدارة](../embedding-admin.md) · [لوحة الوكيل](../agent-panel.md) · [الإضافات](../plugins.md)
- [النشرات البريدية](../newsletters.md) · [التطوير](../development.md)

يتوفر Escalated أيضًا لـ [Laravel](https://github.com/escalated-dev/escalated-laravel)، [Rails](https://github.com/escalated-dev/escalated-rails)، [Django](https://github.com/escalated-dev/escalated-django)، [NestJS](https://github.com/escalated-dev/escalated-nestjs)، [AdonisJS](https://github.com/escalated-dev/escalated-adonis)، [Symfony](https://github.com/escalated-dev/escalated-symfony)، [Phoenix](https://github.com/escalated-dev/escalated-phoenix)، [Go](https://github.com/escalated-dev/escalated-go)، [.NET](https://github.com/escalated-dev/escalated-dotnet)، [WordPress](https://github.com/escalated-dev/escalated-wordpress) و[Filament](https://github.com/escalated-dev/escalated-filament). توجد الواجهة في [escalated](https://github.com/escalated-dev/escalated).

## 

MIT. Copyright (c) 2026 Escalated.dev. راجع [LICENSE](../../LICENSE).
