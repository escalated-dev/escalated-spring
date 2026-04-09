<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <b>日本語</b> •
  <a href="README.ko.md">한국어</a> •
  <a href="README.nl.md">Nederlands</a> •
  <a href="README.pl.md">Polski</a> •
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Spring Bootアプリケーション向けの組み込み可能なヘルプデスクシステム。単一の依存関係でフル機能のサポートデスクを任意のJavaアプリケーションに追加できます。

## 機能

1. **Ticket CRUD** -- ステータス、優先度、割り当てによる完全なライフサイクル管理
2. **SLA Policies** -- 営業時間サポートと休日カレンダーを備えた設定可能なSLA
3. **Automations** -- 解決済みチケットの自動クローズと自動割り当てのための時間ベースのルール
4. **Escalation Rules** -- SLA違反時の再割り当てと通知による自動エスカレーション
5. **Macros & Canned Responses** -- エージェント向けの定義済みアクションと応答テンプレート
6. **Custom Fields** -- 複数のフィールドタイプによる拡張可能なチケットデータ
7. **Knowledge Base** -- 検索、閲覧数、フィードバック付きの記事とカテゴリ
8. **Webhooks** -- リトライロジック付きHMAC署名Webhook配信
9. **API Tokens** -- APIアクセス用のSHA-256ハッシュトークン認証
10. **Roles & Permissions** -- きめ細かなロールベースのアクセス制御
11. **Audit Logging** -- すべてのアクションの完全な監査証跡
12. **Import System** -- 構造化データからのチケット一括インポート
13. **Side Conversations** -- チケット内のプライベートスレッド会話
14. **Ticket Merging & Linking** -- 重複チケットの統合と関連チケットのリンク
15. **Ticket Splitting** -- 複雑なチケットを個別の問題に分割
16. **Ticket Snooze** -- `@Scheduled`による自動ウェイクアップ付きチケットスヌーズ
17. **Email Threading** -- ThymeleafによるブランドHTMLメールテンプレートと正しいMessage-IDスレッディング
18. **Saved Views** -- エージェントごとのカスタムフィルター/ソート済みチケットビュー
19. **Widget API** -- サポートウィジェット埋め込み用のパブリックRESTエンドポイント
20. **Real-time Broadcasting** -- STOMP/SockJS経由のWebSocket（オプトイン）
21. **Capacity Management** -- エージェントのワークロード制限の追跡と適用
22. **Skill-based Routing** -- マッチするスキルを持つエージェントへのチケットルーティング
23. **CSAT Ratings** -- トークンベースのアクセスによる顧客満足度調査
24. **2FA (TOTP)** -- エージェントアカウント用の時間ベースワンタイムパスワードサポート
25. **Guest Access** -- 認証不要のトークンベースチケットアクセス

## 要件

- Java 17+
- Spring Boot 3.2+
- リレーショナルデータベース（PostgreSQL、MySQL、または開発用H2）

## インストール

`build.gradle.kts`に依存関係を追加します：

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

または`pom.xml`：

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 設定

`application.properties`または`application.yml`に追加します：

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

## データベースセットアップ

Flywayマイグレーションが含まれており、自動的に実行されます。マイグレーションは`escalated_`プレフィックス付きのすべてのテーブルを作成し、デフォルトのロールと権限をシードします。

## APIエンドポイント

### Admin (`/escalated/api/admin/`)
| メソッド | パス | 説明 |
|--------|------|-------------|
| GET | `/tickets` | チケット一覧（ページネーション、フィルター可能） |
| POST | `/tickets` | チケット作成 |
| GET | `/tickets/{id}` | チケット取得 |
| PUT | `/tickets/{id}` | チケット更新 |
| POST | `/tickets/{id}/assign` | チケット割り当て |
| POST | `/tickets/{id}/status` | ステータス変更 |
| POST | `/tickets/{id}/snooze` | チケットスヌーズ |
| POST | `/tickets/{id}/merge` | チケット統合 |
| POST | `/tickets/{id}/split` | チケット分割 |
| DELETE | `/tickets/{id}` | チケット削除 |
| GET/POST | `/departments` | 部門管理 |
| GET/POST | `/agents` | エージェント管理 |
| GET/POST | `/webhooks` | Webhook管理 |
| GET/POST | `/roles` | ロール管理 |
| GET/POST | `/custom-fields` | カスタムフィールド管理 |
| GET/POST | `/settings` | 設定管理 |
| GET | `/audit-logs` | 監査ログ表示 |
| POST | `/import/tickets` | チケットインポート |
| GET/POST | `/kb/categories` | KBカテゴリ管理 |
| GET/POST | `/kb/articles` | KB記事管理 |

### Agent (`/escalated/api/agent/`)
| メソッド | パス | 説明 |
|--------|------|-------------|
| GET | `/tickets` | 割り当て済み/フィルター済みチケット一覧 |
| GET | `/tickets/{id}` | チケット表示 |
| POST | `/tickets/{id}/replies` | 返信追加 |
| POST | `/tickets/{id}/macro/{macroId}` | マクロ適用 |
| POST | `/tickets/{id}/side-conversations` | サイドカンバセーション作成 |
| POST | `/tickets/{id}/links` | チケットリンク |
| GET/POST | `/saved-views` | 保存済みビュー管理 |
| GET/POST | `/canned-responses` | 定型応答管理 |

### Customer (`/escalated/api/customer/`)
| メソッド | パス | 説明 |
|--------|------|-------------|
| GET | `/tickets?email=` | 顧客チケット一覧 |
| POST | `/tickets` | チケット作成 |
| POST | `/tickets/{id}/replies` | 返信追加 |

### Widget (`/escalated/api/widget/`)
| メソッド | パス | 説明 |
|--------|------|-------------|
| POST | `/tickets` | チケット作成（公開） |
| GET | `/tickets/{token}` | ゲストトークンでチケット表示 |
| POST | `/tickets/{token}/replies` | ゲストトークンで返信 |
| GET | `/kb/search?query=` | ナレッジベース検索 |
| POST | `/csat/{token}` | 満足度評価送信 |

### Guest (`/escalated/api/guest/`)
| メソッド | パス | 説明 |
|--------|------|-------------|
| GET | `/tickets/{token}` | チケット表示 |
| GET | `/tickets/{token}/replies` | 返信表示 |
| POST | `/tickets/{token}/replies` | 返信追加 |

## アーキテクチャ

```
dev.escalated/
  config/              自動設定、プロパティ、WebSocket設定
  models/              完全なリレーション付きJPAエンティティ
  repositories/        Spring Data JPAリポジトリ
  services/            ビジネスロジック（トランザクション）
  controllers/
    admin/             管理者REST API
    agent/             エージェントREST API
    customer/          顧客REST API
    widget/            公開ウィジェットAPI
  events/              Springアプリケーションイベント + Webhookリスナー
  security/            APIトークン認証フィルター、セキュリティ設定、2FA
  scheduling/          @Scheduledタスク（スヌーズ、SLA、自動化）
```

## 認証

APIエンドポイントはBearerトークン認証を使用します。管理者APIでトークンを作成します：

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

レスポンスにはプレーンテキストのトークンが含まれます（一度だけ表示）。後続のリクエストで使用します：

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket（リアルタイム）

`escalated.broadcasting.enabled=true`で有効化します。SockJS/STOMP経由で`/escalated/ws`に接続します。

## 開発

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## ライセンス

MITライセンス。詳細は[LICENSE](LICENSE)を参照してください。
