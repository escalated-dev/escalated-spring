<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
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
  <b>Türkçe</b> ·
  <a href="README.zh-CN.md">简体中文</a>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  Spring Boot uygulamanıza eksiksiz bir yardım masası ekleyin.<br>
  Talepler, SLA'lar, eskalasyon kuralları, bir bilgi bankası ve bir destek widget'ı, kendi veritabanınızda tek bir bağımlılık olarak.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>Web sitesi</b></a> ·
  <a href="https://escalated.dev/docs"><b>Dokümantasyon</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>Değişiklik günlüğü</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="Escalated temsilci gelen kutusu: sayılarıyla kayıtlı görünümler, her talepte SLA geri sayımları ve öncelikler" width="100%">
</picture>

## Neler yapar

<table>
  <tr>
    <td width="40%">
      <h3>Temsilcilerinizin gün boyu çalışabileceği bir gelen kutusu</h3>
      Yazışma, dahili notlar, hazır yanıtlar ve SLA sayaçları tek ekranda. Talepler Postmark, Mailgun veya AWS SES üzerinden e-postayla da gelir.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="Temsilci çalışma alanında yanıt düzenleyicisiyle açık bir talep">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="Yanıt sürelerini, CSAT'i ve SLA ihlallerini gösteren yönetici rapor sayfası">
      </picture>
    </td>
    <td width="40%">
      <h3>Görebildiğiniz SLA'lar ve eskalasyon kuralları</h3>
      Mesai saatlerini ve tatilleri dikkate alan yanıt ve çözüm hedefleri, ihlal olduğunda eskalasyon yapan veya talebi yeniden atayan kurallar ve SLA raporları.
    </td>
  </tr>
</table>

## Özellikler

<table>
<tr>
<td width="33%" valign="top">

**Talepler ve gelen kutusu**
- Durumlar, öncelikler ve atama
- Makrolar ve hazır yanıtlar
- Taleplerde yan yazışmalar
- Talepleri birleştirme, bağlama ve bölme
- Otomatik uyandırmalı erteleme
- Temsilci başına kayıtlı görünümler
- Farklı türlerde özel alanlar

</td>
<td width="33%" valign="top">

**SLA'lar ve yönlendirme**
- Mesai saatleri ve tatiller içeren SLA politikaları
- İhlal, yaş veya yanıtsızlık durumunda eskalasyon
- Yeniden atama, öncelik değiştirme veya departman taşıma
- Otomatik kapatma ve otomatik atama kuralları
- Yetkinliğe dayalı yönlendirme
- Temsilci iş yükü sınırları

</td>
<td width="33%" valign="top">

**E-posta**
- Postmark, Mailgun ve AWS SES üzerinden gelen e-posta
- İmzalı Reply-To ile talep eşleştirme
- Yalnızca talep sahibinden gelen yanıtlar kabul edilir
- Thymeleaf ile markalı HTML e-postalar
- Message-ID ile ileti dizileri

</td>
</tr>
<tr>
<td width="33%" valign="top">

**Müşteriler**
- Herkese açık widget API'si
- Token ile misafir talep erişimi
- Token erişimli CSAT anketleri
- Arama ve geri bildirim içeren bilgi bankası

</td>
<td width="33%" valign="top">

**Yönetim ve güvenlik**
- Roller ve izinler
- Personel için TOTP iki faktörlü doğrulama
- SAML 2, OAuth 2 / OIDC ve JWT ile oturum açma
- Her işlem için denetim kaydı
- Hash'lenmiş API token'ları

</td>
<td width="33%" valign="top">

**Genişletme ve entegrasyon**
- Yeniden denemeli, HMAC imzalı webhook'lar
- Toplu talep içe aktarma
- Yerel Java ve SDK eklentileri
- STOMP/SockJS üzerinden WebSocket (isteğe bağlı)
- Ortak Vue 3 + Inertia arayüzü (isteğe bağlı)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">Tüm özellikler ayrıntılı olarak</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## Özellikler

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

Ardından `implementation("dev.escalated:escalated-spring:0.2.13")` satırını `mavenLocal()` ile birlikte uygulamanıza ekleyin ve `escalated.enabled=true` ayarını yapın. Java 17+ ve Spring Boot 4.1 gerekir. [Başlangıç kılavuzu](../getting-started.md) yapılandırmayı, veritabanını ve oturum açmayı anlatır; [Inertia sayfaları](../ui.md) ise `escalated.ui.enabled=true` ile ortak ekranları açar.

## tiketler, ekler, etkinlik zaman çizelgesi

- [Tüm özellikler](../features.md) · [Başlarken](../getting-started.md) · [Inertia sayfaları](../ui.md)
- [Özelleştirme, yerelleştirme ve gerçek zamanlı güncellemeler](../customization.md) · [Mimari](../architecture.md) · [API uç noktaları](../api-endpoints.md)
- [Erişim denetimi](../access-control.md) · [Talep erişimi ve yönlendirme](../ticket-access-and-routing.md) · [Özel alanlar](../custom-fields.md)
- [Gelen e-posta](../inbound-email.md) · [Misafir erişimi](../guest-access.md) · [Widget'ı yerleştirme](../embedding-the-widget.md)
- [Yönetim panelini yerleştirme](../embedding-admin.md) · [Temsilci paneli](../agent-panel.md) · [Eklentiler](../plugins.md)
- [Bültenler](../newsletters.md) · [Geliştirme](../development.md)

Escalated, [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress) ve [Filament](https://github.com/escalated-dev/escalated-filament) için de kullanılabilir. Arayüz [escalated](https://github.com/escalated-dev/escalated) deposunda bulunur.

## 

MIT. Copyright (c) 2026 Escalated.dev. Ayrıntılar için [LICENSE](../../LICENSE) dosyasına bakın.
