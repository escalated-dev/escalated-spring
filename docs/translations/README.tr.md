<p align="center">
  <a href="README.ar.md">العربية</a> •
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
  <b>Türkçe</b> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Spring Boot uygulamaları için gömülebilir yardım masası sistemi. Tek bir bağımlılıkla herhangi bir Java uygulamasına tam özellikli bir destek masası ekleyin.

## Özellikler

1. **Ticket CRUD** -- Durumlar, öncelikler ve atamalarla tam yaşam döngüsü yönetimi
2. **SLA Policies** -- Çalışma saatleri desteği ve tatil takvimleri ile yapılandırılabilir SLA'lar
3. **Automations** -- Çözülmüş taleplerin otomatik kapatılması ve otomatik atama için zaman tabanlı kurallar
4. **Escalation Rules** -- SLA ihlalinde yeniden atama ve bildirimlerle otomatik yükseltme
5. **Macros & Canned Responses** -- Temsilciler için önceden tanımlanmış eylemler ve yanıt şablonları
6. **Custom Fields** -- Birden fazla alan türüyle genişletilebilir talep verileri
7. **Knowledge Base** -- Arama, görüntüleme sayacı ve geri bildirimli makaleler ve kategoriler
8. **Webhooks** -- Yeniden deneme mantığıyla HMAC imzalı webhook teslimatı
9. **API Tokens** -- API erişimi için SHA-256 hashlenmiş token kimlik doğrulaması
10. **Roles & Permissions** -- Ayrıntılı rol tabanlı erişim kontrolü
11. **Audit Logging** -- Tüm eylemler için eksiksiz denetim izi
12. **Import System** -- Yapılandırılmış verilerden toplu talep içe aktarma
13. **Side Conversations** -- Talepler içinde özel iş parçacıklı konuşmalar
14. **Ticket Merging & Linking** -- Yinelenen talepleri birleştirme ve ilişkileri bağlama
15. **Ticket Splitting** -- Karmaşık talepleri ayrı sorunlara bölme
16. **Ticket Snooze** -- `@Scheduled` ile otomatik uyandırmalı talep erteleme
17. **Email Threading** -- Thymeleaf ile markalı HTML e-posta şablonları ve doğru Message-ID zincirleme
18. **Saved Views** -- Temsilci başına özelleştirilmiş filtrelenmiş/sıralanmış talep görünümleri
19. **Widget API** -- Destek widget'ı gömmek için genel REST uç noktaları
20. **Real-time Broadcasting** -- STOMP/SockJS üzerinden WebSocket (isteğe bağlı)
21. **Capacity Management** -- Temsilci iş yükü sınırlarını izleme ve uygulama
22. **Skill-based Routing** -- Talepleri eşleşen becerilere sahip temsilcilere yönlendirme
23. **CSAT Ratings** -- Token tabanlı erişimle müşteri memnuniyeti anketleri
24. **2FA (TOTP)** -- Temsilci hesapları için zamana dayalı tek kullanımlık şifre desteği
25. **Guest Access** -- Kimlik doğrulaması olmadan token tabanlı talep erişimi

## Gereksinimler

- Java 17+
- Spring Boot 3.2+
- İlişkisel bir veritabanı (PostgreSQL, MySQL veya geliştirme için H2)

## Kurulum

Bağımlılığı `build.gradle.kts` dosyanıza ekleyin:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Veya `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Yapılandırma

`application.properties` veya `application.yml` dosyanıza ekleyin:

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

## Veritabanı Kurulumu

Flyway göçleri dahildir ve otomatik olarak çalışır. Göç, `escalated_` önekiyle tüm tabloları oluşturur ve varsayılan rolleri ve izinleri başlatır.

## API Uç Noktaları

### Admin (`/escalated/api/admin/`)
| Yöntem | Yol | Açıklama |
|--------|------|-------------|
| GET | `/tickets` | Talepleri listele (sayfalanmış, filtrelenebilir) |
| POST | `/tickets` | Talep oluştur |
| GET | `/tickets/{id}` | Talep getir |
| PUT | `/tickets/{id}` | Talep güncelle |
| POST | `/tickets/{id}/assign` | Talep ata |
| POST | `/tickets/{id}/status` | Durum değiştir |
| POST | `/tickets/{id}/snooze` | Talebi ertele |
| POST | `/tickets/{id}/merge` | Talepleri birleştir |
| POST | `/tickets/{id}/split` | Talebi böl |
| DELETE | `/tickets/{id}` | Talebi sil |
| GET/POST | `/departments` | Departmanları yönet |
| GET/POST | `/agents` | Temsilcileri yönet |
| GET/POST | `/webhooks` | Webhook'ları yönet |
| GET/POST | `/roles` | Rolleri yönet |
| GET/POST | `/custom-fields` | Özel alanları yönet |
| GET/POST | `/settings` | Ayarları yönet |
| GET | `/audit-logs` | Denetim günlüklerini görüntüle |
| POST | `/import/tickets` | Talepleri içe aktar |
| GET/POST | `/kb/categories` | KB kategorilerini yönet |
| GET/POST | `/kb/articles` | KB makalelerini yönet |

### Agent (`/escalated/api/agent/`)
| Yöntem | Yol | Açıklama |
|--------|------|-------------|
| GET | `/tickets` | Atanmış/filtrelenmiş talepleri listele |
| GET | `/tickets/{id}` | Talebi görüntüle |
| POST | `/tickets/{id}/replies` | Yanıt ekle |
| POST | `/tickets/{id}/macro/{macroId}` | Makro uygula |
| POST | `/tickets/{id}/side-conversations` | Yan konuşma oluştur |
| POST | `/tickets/{id}/links` | Talepleri bağla |
| GET/POST | `/saved-views` | Kaydedilmiş görünümleri yönet |
| GET/POST | `/canned-responses` | Hazır yanıtları yönet |

### Customer (`/escalated/api/customer/`)
| Yöntem | Yol | Açıklama |
|--------|------|-------------|
| GET | `/tickets?email=` | Müşteri taleplerini listele |
| POST | `/tickets` | Talep oluştur |
| POST | `/tickets/{id}/replies` | Yanıt ekle |

### Widget (`/escalated/api/widget/`)
| Yöntem | Yol | Açıklama |
|--------|------|-------------|
| POST | `/tickets` | Talep oluştur (genel) |
| GET | `/tickets/{token}` | Misafir tokeniyle talebi görüntüle |
| POST | `/tickets/{token}/replies` | Misafir tokeniyle yanıtla |
| GET | `/kb/search?query=` | Bilgi tabanında ara |
| POST | `/csat/{token}` | Memnuniyet değerlendirmesi gönder |

### Guest (`/escalated/api/guest/`)
| Yöntem | Yol | Açıklama |
|--------|------|-------------|
| GET | `/tickets/{token}` | Talebi görüntüle |
| GET | `/tickets/{token}/replies` | Yanıtları görüntüle |
| POST | `/tickets/{token}/replies` | Yanıt ekle |

## Mimari

```
dev.escalated/
  config/              Otomatik yapılandırma, özellikler, WebSocket yapılandırması
  models/              Tam ilişkilere sahip JPA varlıkları
  repositories/        Spring Data JPA depoları
  services/            İş mantığı (işlemsel)
  controllers/
    admin/             Yönetici REST API
    agent/             Temsilci REST API
    customer/          Müşteri REST API
    widget/            Genel widget API
  events/              Spring uygulama olayları + webhook dinleyicisi
  security/            API token kimlik doğrulama filtresi, güvenlik yapılandırması, 2FA
  scheduling/          @Scheduled görevleri (erteleme, SLA, otomasyonlar)
```

## Kimlik Doğrulama

API uç noktaları Bearer token kimlik doğrulaması kullanır. Yönetici API'si aracılığıyla token oluşturun:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

Yanıt, düz metin tokeni içerir (yalnızca bir kez gösterilir). Sonraki isteklerde kullanın:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Gerçek zamanlı)

`escalated.broadcasting.enabled=true` ile etkinleştirin. SockJS/STOMP aracılığıyla `/escalated/ws`'ye bağlanın.

## Geliştirme

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Lisans

MIT Lisansı. Ayrıntılar için [LICENSE](LICENSE) dosyasına bakın.
