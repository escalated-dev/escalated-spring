<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <b>Français</b> •
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

Un système de helpdesk intégrable pour les applications Spring Boot. Ajoutez un bureau d'assistance complet à toute application Java avec une seule dépendance.

## Fonctionnalités

1. **Ticket CRUD** -- Gestion complète du cycle de vie avec statuts, priorités et affectations
2. **SLA Policies** -- SLAs configurables avec support des heures ouvrables et calendriers de jours fériés
3. **Automations** -- Règles temporelles pour la fermeture automatique des tickets résolus et l'affectation automatique
4. **Escalation Rules** -- Escalade automatique en cas de violation de SLA avec réaffectation et notifications
5. **Macros & Canned Responses** -- Actions prédéfinies et modèles de réponse pour les agents
6. **Custom Fields** -- Données de tickets extensibles avec plusieurs types de champs
7. **Knowledge Base** -- Articles et catégories avec recherche, compteur de vues et retours
8. **Webhooks** -- Livraison de webhooks signés HMAC avec logique de réessai
9. **API Tokens** -- Authentification par jeton haché SHA-256 pour l'accès à l'API
10. **Roles & Permissions** -- Contrôle d'accès granulaire basé sur les rôles
11. **Audit Logging** -- Piste d'audit complète pour toutes les actions
12. **Import System** -- Importation en masse de tickets à partir de données structurées
13. **Side Conversations** -- Conversations privées avec fils de discussion au sein des tickets
14. **Ticket Merging & Linking** -- Fusionner les tickets en double et lier les tickets associés
15. **Ticket Splitting** -- Diviser les tickets complexes en problèmes séparés
16. **Ticket Snooze** -- Mettre en veille les tickets avec réveil automatique via `@Scheduled`
17. **Email Threading** -- Modèles d'e-mail HTML personnalisés via Thymeleaf avec chaînage correct de Message-ID
18. **Saved Views** -- Vues de tickets personnalisées filtrées/triées par agent
19. **Widget API** -- Points de terminaison REST publics pour intégrer un widget d'assistance
20. **Real-time Broadcasting** -- WebSocket via STOMP/SockJS (optionnel)
21. **Capacity Management** -- Suivi et application des limites de charge de travail des agents
22. **Skill-based Routing** -- Acheminer les tickets vers les agents ayant les compétences correspondantes
23. **CSAT Ratings** -- Enquêtes de satisfaction client avec accès par jeton
24. **2FA (TOTP)** -- Support de mot de passe à usage unique basé sur le temps pour les comptes agents
25. **Guest Access** -- Accès aux tickets par jeton sans authentification

## Prérequis

- Java 17+
- Spring Boot 3.2+
- Une base de données relationnelle (PostgreSQL, MySQL ou H2 pour le développement)

## Installation

Ajoutez la dépendance à votre `build.gradle.kts` :

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

Ou `pom.xml` :

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configuration

Ajoutez à votre `application.properties` ou `application.yml` :

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

## Configuration de la base de données

Les migrations Flyway sont incluses et s'exécutent automatiquement. La migration crée toutes les tables préfixées par `escalated_` et initialise les rôles et permissions par défaut.

## Points de terminaison API

### Admin (`/escalated/api/admin/`)
| Méthode | Chemin | Description |
|--------|------|-------------|
| GET | `/tickets` | Lister les tickets (paginé, filtrable) |
| POST | `/tickets` | Créer un ticket |
| GET | `/tickets/{id}` | Obtenir un ticket |
| PUT | `/tickets/{id}` | Mettre à jour un ticket |
| POST | `/tickets/{id}/assign` | Affecter un ticket |
| POST | `/tickets/{id}/status` | Changer le statut |
| POST | `/tickets/{id}/snooze` | Mettre en veille un ticket |
| POST | `/tickets/{id}/merge` | Fusionner des tickets |
| POST | `/tickets/{id}/split` | Diviser un ticket |
| DELETE | `/tickets/{id}` | Supprimer un ticket |
| GET/POST | `/departments` | Gérer les départements |
| GET/POST | `/agents` | Gérer les agents |
| GET/POST | `/webhooks` | Gérer les webhooks |
| GET/POST | `/roles` | Gérer les rôles |
| GET/POST | `/custom-fields` | Gérer les champs personnalisés |
| GET/POST | `/settings` | Gérer les paramètres |
| GET | `/audit-logs` | Voir les journaux d'audit |
| POST | `/import/tickets` | Importer des tickets |
| GET/POST | `/kb/categories` | Gérer les catégories KB |
| GET/POST | `/kb/articles` | Gérer les articles KB |

### Agent (`/escalated/api/agent/`)
| Méthode | Chemin | Description |
|--------|------|-------------|
| GET | `/tickets` | Lister les tickets assignés/filtrés |
| GET | `/tickets/{id}` | Voir le ticket |
| POST | `/tickets/{id}/replies` | Ajouter une réponse |
| POST | `/tickets/{id}/macro/{macroId}` | Appliquer une macro |
| POST | `/tickets/{id}/side-conversations` | Créer une conversation parallèle |
| POST | `/tickets/{id}/links` | Lier des tickets |
| GET/POST | `/saved-views` | Gérer les vues enregistrées |
| GET/POST | `/canned-responses` | Gérer les réponses prédéfinies |

### Customer (`/escalated/api/customer/`)
| Méthode | Chemin | Description |
|--------|------|-------------|
| GET | `/tickets?email=` | Lister les tickets client |
| POST | `/tickets` | Créer un ticket |
| POST | `/tickets/{id}/replies` | Ajouter une réponse |

### Widget (`/escalated/api/widget/`)
| Méthode | Chemin | Description |
|--------|------|-------------|
| POST | `/tickets` | Créer un ticket (public) |
| GET | `/tickets/{token}` | Voir le ticket par jeton d'invité |
| POST | `/tickets/{token}/replies` | Répondre via jeton d'invité |
| GET | `/kb/search?query=` | Rechercher dans la base de connaissances |
| POST | `/csat/{token}` | Soumettre une évaluation de satisfaction |

### Guest (`/escalated/api/guest/`)
| Méthode | Chemin | Description |
|--------|------|-------------|
| GET | `/tickets/{token}` | Voir le ticket |
| GET | `/tickets/{token}/replies` | Voir les réponses |
| POST | `/tickets/{token}/replies` | Ajouter une réponse |

## Architecture

```
dev.escalated/
  config/              Auto-configuration, propriétés, configuration WebSocket
  models/              Entités JPA avec relations complètes
  repositories/        Dépôts Spring Data JPA
  services/            Logique métier (transactionnelle)
  controllers/
    admin/             API REST administrateur
    agent/             API REST agent
    customer/          API REST client
    widget/            API widget publique
  events/              Événements d'application Spring + écouteur de webhooks
  security/            Filtre d'authentification par jeton API, configuration de sécurité, 2FA
  scheduling/          @Scheduled tâches (veille, SLA, automatisations)
```

## Authentification

Les points de terminaison API utilisent l'authentification par jeton Bearer. Créez des jetons via l'API administrateur :

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

La réponse inclut le jeton en texte clair (affiché une seule fois). Utilisez-le dans les requêtes suivantes :

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (Temps réel)

Activez avec `escalated.broadcasting.enabled=true`. Connectez-vous à `/escalated/ws` via SockJS/STOMP.

## Développement

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## Licence

Licence MIT. Voir [LICENSE](LICENSE) pour les détails.
