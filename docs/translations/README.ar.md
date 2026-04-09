<p align="center">
  <b>العربية</b> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <a href="README.ja.md">日本語</a> •
  <a href="README.ko.md">한국어</a> •
  <a href="README.nl.md">Nederlands</a> •
  <a href="README.pl.md">Polski</a> •
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

نظام مكتب مساعدة قابل للتضمين لتطبيقات Spring Boot. أضف مكتب دعم كامل الميزات إلى أي تطبيق Java بتبعية واحدة.

## الميزات

1. **Ticket CRUD** -- إدارة كاملة لدورة الحياة مع الحالات والأولويات والتعيينات
2. **SLA Policies** -- اتفاقيات مستوى خدمة قابلة للتكوين مع دعم ساعات العمل وتقويمات العطلات
3. **Automations** -- قواعد مبنية على الوقت لإغلاق التذاكر المحلولة تلقائياً والتعيين التلقائي
4. **Escalation Rules** -- تصعيد تلقائي عند انتهاك SLA مع إعادة التعيين والإشعارات
5. **Macros & Canned Responses** -- إجراءات محددة مسبقاً وقوالب استجابة للوكلاء
6. **Custom Fields** -- بيانات تذاكر قابلة للتوسيع مع أنواع حقول متعددة
7. **Knowledge Base** -- مقالات وفئات مع البحث وعدد المشاهدات والتعليقات
8. **Webhooks** -- تسليم Webhook موقّع بـ HMAC مع منطق إعادة المحاولة
9. **API Tokens** -- مصادقة بالرمز المميز المجزأ بـ SHA-256 للوصول إلى API
10. **Roles & Permissions** -- تحكم دقيق في الوصول قائم على الأدوار
11. **Audit Logging** -- سجل تدقيق كامل لجميع الإجراءات
12. **Import System** -- استيراد جماعي للتذاكر من بيانات منظمة
13. **Side Conversations** -- محادثات خاصة مترابطة داخل التذاكر
14. **Ticket Merging & Linking** -- دمج التذاكر المكررة وربط التذاكر ذات الصلة
15. **Ticket Splitting** -- تقسيم التذاكر المعقدة إلى مشكلات منفصلة
16. **Ticket Snooze** -- تأجيل التذاكر مع إيقاظ تلقائي عبر `@Scheduled`
17. **Email Threading** -- قوالب بريد إلكتروني HTML ذات علامة تجارية عبر Thymeleaf مع ترابط Message-ID صحيح
18. **Saved Views** -- عروض تذاكر مخصصة مفلترة/مرتبة لكل وكيل
19. **Widget API** -- نقاط نهاية REST عامة لتضمين أداة الدعم
20. **Real-time Broadcasting** -- WebSocket عبر STOMP/SockJS (اختياري)
21. **Capacity Management** -- تتبع وفرض حدود أعباء عمل الوكلاء
22. **Skill-based Routing** -- توجيه التذاكر إلى وكلاء ذوي مهارات مطابقة
23. **CSAT Ratings** -- استبيانات رضا العملاء مع وصول قائم على الرمز المميز
24. **2FA (TOTP)** -- دعم كلمة مرور لمرة واحدة مبنية على الوقت لحسابات الوكلاء
25. **Guest Access** -- وصول إلى التذاكر قائم على الرمز المميز بدون مصادقة

## المتطلبات

- Java 17+
- Spring Boot 3.2+
- قاعدة بيانات علائقية (PostgreSQL أو MySQL أو H2 للتطوير)

## التثبيت

أضف التبعية إلى ملف `build.gradle.kts` الخاص بك:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

أو `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## الإعدادات

أضف إلى ملف `application.properties` أو `application.yml` الخاص بك:

```properties
# Enable/disable the helpdesk
escalated.enabled=true

# Route prefix (default: escalated)
escalated.route-prefix=escalated

# Feature toggles
escalated.knowledge-base.enabled=true
escalated.broadcasting.enabled=false
escalated.two-factor.enabled=true
escalated.widget.enabled=true
escalated.guest-access.enabled=true

# SLA checking interval
escalated.sla.check-interval-seconds=60

# Snooze wake-up interval
escalated.snooze.check-interval-seconds=60

# Webhook settings
escalated.webhook.max-retries=3

# Database (example for PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=user
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

## إعداد قاعدة البيانات

ترحيلات Flyway مضمنة وتعمل تلقائياً. يقوم الترحيل بإنشاء جميع الجداول مع البادئة `escalated_` ويزرع الأدوار والأذونات الافتراضية.

## نقاط نهاية API

### Admin (`/escalated/api/admin/`)
| الطريقة | المسار | الوصف |
|--------|------|-------------|
| GET | `/tickets` | عرض التذاكر (مع ترقيم وتصفية) |
| POST | `/tickets` | إنشاء تذكرة |
| GET | `/tickets/{id}` | الحصول على تذكرة |
| PUT | `/tickets/{id}` | تحديث تذكرة |
| POST | `/tickets/{id}/assign` | تعيين تذكرة |
| POST | `/tickets/{id}/status` | تغيير الحالة |
| POST | `/tickets/{id}/snooze` | تأجيل تذكرة |
| POST | `/tickets/{id}/merge` | دمج التذاكر |
| POST | `/tickets/{id}/split` | تقسيم تذكرة |
| DELETE | `/tickets/{id}` | حذف تذكرة |
| GET/POST | `/departments` | إدارة الأقسام |
| GET/POST | `/agents` | إدارة الوكلاء |
| GET/POST | `/webhooks` | إدارة Webhooks |
| GET/POST | `/roles` | إدارة الأدوار |
| GET/POST | `/custom-fields` | إدارة الحقول المخصصة |
| GET/POST | `/settings` | إدارة الإعدادات |
| GET | `/audit-logs` | عرض سجلات التدقيق |
| POST | `/import/tickets` | استيراد التذاكر |
| GET/POST | `/kb/categories` | إدارة فئات قاعدة المعرفة |
| GET/POST | `/kb/articles` | إدارة مقالات قاعدة المعرفة |

### Agent (`/escalated/api/agent/`)
| الطريقة | المسار | الوصف |
|--------|------|-------------|
| GET | `/tickets` | عرض التذاكر المعينة/المفلترة |
| GET | `/tickets/{id}` | عرض التذكرة |
| POST | `/tickets/{id}/replies` | إضافة رد |
| POST | `/tickets/{id}/macro/{macroId}` | تطبيق ماكرو |
| POST | `/tickets/{id}/side-conversations` | إنشاء محادثة جانبية |
| POST | `/tickets/{id}/links` | ربط التذاكر |
| GET/POST | `/saved-views` | إدارة العروض المحفوظة |
| GET/POST | `/canned-responses` | إدارة الردود الجاهزة |

### Customer (`/escalated/api/customer/`)
| الطريقة | المسار | الوصف |
|--------|------|-------------|
| GET | `/tickets?email=` | عرض تذاكر العميل |
| POST | `/tickets` | إنشاء تذكرة |
| POST | `/tickets/{id}/replies` | إضافة رد |

### Widget (`/escalated/api/widget/`)
| الطريقة | المسار | الوصف |
|--------|------|-------------|
| POST | `/tickets` | إنشاء تذكرة (عامة) |
| GET | `/tickets/{token}` | عرض التذكرة برمز الضيف |
| POST | `/tickets/{token}/replies` | الرد عبر رمز الضيف |
| GET | `/kb/search?query=` | البحث في قاعدة المعرفة |
| POST | `/csat/{token}` | إرسال تقييم الرضا |

### Guest (`/escalated/api/guest/`)
| الطريقة | المسار | الوصف |
|--------|------|-------------|
| GET | `/tickets/{token}` | عرض التذكرة |
| GET | `/tickets/{token}/replies` | عرض الردود |
| POST | `/tickets/{token}/replies` | إضافة رد |

## البنية

```
dev.escalated/
  config/              التكوين التلقائي، الخصائص، إعدادات WebSocket
  models/              كيانات JPA مع العلاقات الكاملة
  repositories/        مستودعات Spring Data JPA
  services/            منطق الأعمال (معاملاتي)
  controllers/
    admin/             واجهة REST للمسؤول
    agent/             واجهة REST للوكيل
    customer/          واجهة REST للعميل
    widget/            واجهة الأداة العامة
  events/              أحداث تطبيق Spring + مستمع webhook
  security/            مرشح مصادقة رمز API، إعدادات الأمان، 2FA
  scheduling/          مهام @Scheduled (تأجيل، SLA، أتمتة)
```

## المصادقة

تستخدم نقاط نهاية API مصادقة رمز Bearer. أنشئ الرموز عبر واجهة المسؤول:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

يتضمن الرد الرمز بنص عادي (يُعرض مرة واحدة فقط). استخدمه في الطلبات اللاحقة:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (الوقت الحقيقي)

فعّل بـ `escalated.broadcasting.enabled=true`. اتصل بـ `/escalated/ws` عبر SockJS/STOMP.

## التطوير

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## الترخيص

رخصة MIT. انظر [LICENSE](LICENSE) للتفاصيل.
