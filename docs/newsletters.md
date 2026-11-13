# Newsletters

## Newsletters (optional, partial port)

JPA entities + renderer for the admin-only newsletter broadcast feature. Schema is auto-derived by Hibernate from the new `@Entity` classes when `spring.jpa.hibernate.ddl-auto=update`. Production hosts using Flyway / Liquibase generate the SQL migration with `mvn spring-boot:run` or `mvn flyway:migrate` after referencing this package.

```java
import dev.escalated.services.newsletter.NewsletterRenderer;

var opts = new NewsletterRenderer.Options();
opts.baseUrl = "https://support.example.com";
opts.defaultTheme = "default";
opts.trackingEnabled = true;
opts.themesDir = "src/main/resources/templates/escalated/newsletter_themes";
opts.markdownToHtml = md -> /* plug in flexmark, commonmark-java, etc. */;
opts.brandName = "Acme";
opts.brandAccent = "#2563eb";

var renderer = new NewsletterRenderer(opts);
var html = renderer.render(delivery, newsletter, contact, template);
```

Ships: `models/newsletter/*.java` (5 JPA entities), `models/Contact.java` (gains `marketingOptOutAt`), `services/newsletter/NewsletterRenderer.java`, `resources/templates/escalated/newsletter_themes/{default,branded}.html`.

Follow-up PR: Flyway / Liquibase migration files, planner/dispatcher/tracker services using Spring `JpaRepository`s, Spring MVC controllers.
