<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.it.md">Italiano</a> ·
  <b>日本語</b> ·
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
  Spring Boot アプリに本格的なヘルプデスクを追加できます。<br>
  チケット、SLA、エスカレーションルール、ナレッジベース、サポートウィジェットを、あなた自身のデータベース上で動く 1 つの依存関係として提供します。
</p>

<p align="center">
  <a href="https://escalated.dev"><b>ウェブサイト</b></a> ·
  <a href="https://escalated.dev/docs"><b>ドキュメント</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>変更履歴</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="Escalated のエージェント受信トレイ: 件数付きの保存済みビュー、各チケットの SLA カウントダウンと優先度" width="100%">
</picture>

## できること

<table>
  <tr>
    <td width="40%">
      <h3>エージェントが一日中作業できる受信トレイ</h3>
      会話、内部メモ、定型返信、SLA タイマーを 1 つの画面にまとめています。チケットは Postmark、Mailgun、AWS SES 経由のメールでも届きます。
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="エージェントのワークスペースで開いたチケットと返信エディター">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="応答時間、CSAT、SLA 違反を表示する管理者向けレポートページ">
      </picture>
    </td>
    <td width="40%">
      <h3>ひと目でわかる SLA とエスカレーションルール</h3>
      営業時間と休日を考慮した応答・解決目標、違反時にエスカレーションや再割り当てを行うルール、SLA レポートを備えています。
    </td>
  </tr>
</table>

## 機能

<table>
<tr>
<td width="33%" valign="top">

**チケットと受信トレイ**
- ステータス、優先度、担当の割り当て
- マクロと定型返信
- チケット上のサイド会話
- チケットの統合、リンク、分割
- 自動で再開するスヌーズ
- エージェントごとの保存済みビュー
- 複数の型に対応したカスタムフィールド

</td>
<td width="33%" valign="top">

**SLA とルーティング**
- 営業時間と休日に対応した SLA ポリシー
- 違反、経過時間、無応答でのエスカレーション
- 再割り当て、優先度の変更、部門の移動
- 自動クローズと自動割り当てのルール
- スキルベースのルーティング
- エージェントの作業量の上限

</td>
<td width="33%" valign="top">

**メール**
- Postmark、Mailgun、AWS SES からの受信
- 署名付き Reply-To によるチケットの特定
- 依頼者からの返信のみ受け付け
- Thymeleaf によるブランド付き HTML メール
- Message-ID によるスレッド化

</td>
</tr>
<tr>
<td width="33%" valign="top">

**顧客**
- 公開ウィジェット API
- トークンによるゲストのチケットアクセス
- トークンでアクセスする CSAT アンケート
- 検索とフィードバックに対応したナレッジベース

</td>
<td width="33%" valign="top">

**管理とセキュリティ**
- ロールと権限
- スタッフ向け TOTP 二要素認証
- SAML 2、OAuth 2 / OIDC、JWT によるサインイン
- すべての操作の監査ログ
- ハッシュ化された API トークン

</td>
<td width="33%" valign="top">

**拡張と連携**
- 再試行付きの HMAC 署名 Webhook
- チケットの一括インポート
- ネイティブ Java プラグインと SDK プラグイン
- STOMP/SockJS 経由の WebSocket (オプトイン)
- 共通の Vue 3 + Inertia UI (オプトイン)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">すべての機能の詳細</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## 機能一覧

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

その後、`mavenLocal()` とあわせて `implementation("dev.escalated:escalated-spring:0.2.13")` をアプリに追加し、`escalated.enabled=true` を設定します。Java 17 以上と Spring Boot 4.1 が必要です。設定、データベース、サインインについては[スタートガイド](../getting-started.md)を、`escalated.ui.enabled=true` で共通画面を有効にする方法は [Inertia ページ](../ui.md)を参照してください。

## グ、添付ファイル、アクティビティタイムライン

- [すべての機能](../features.md) · [はじめに](../getting-started.md) · [Inertia ページ](../ui.md)
- [カスタマイズ、ローカライズ、リアルタイム](../customization.md) · [アーキテクチャ](../architecture.md) · [API エンドポイント](../api-endpoints.md)
- [アクセス制御](../access-control.md) · [チケットのアクセスとルーティング](../ticket-access-and-routing.md) · [カスタムフィールド](../custom-fields.md)
- [受信メール](../inbound-email.md) · [ゲストアクセス](../guest-access.md) · [ウィジェットの埋め込み](../embedding-the-widget.md)
- [管理画面の埋め込み](../embedding-admin.md) · [エージェントパネル](../agent-panel.md) · [プラグイン](../plugins.md)
- [ニュースレター](../newsletters.md) · [開発](../development.md)

Escalated は [Laravel](https://github.com/escalated-dev/escalated-laravel)、[Rails](https://github.com/escalated-dev/escalated-rails)、[Django](https://github.com/escalated-dev/escalated-django)、[NestJS](https://github.com/escalated-dev/escalated-nestjs)、[AdonisJS](https://github.com/escalated-dev/escalated-adonis)、[Symfony](https://github.com/escalated-dev/escalated-symfony)、[Phoenix](https://github.com/escalated-dev/escalated-phoenix)、[Go](https://github.com/escalated-dev/escalated-go)、[.NET](https://github.com/escalated-dev/escalated-dotnet)、[WordPress](https://github.com/escalated-dev/escalated-wordpress)、[Filament](https://github.com/escalated-dev/escalated-filament) 向けにも提供しています。UI は [escalated](https://github.com/escalated-dev/escalated) にあります。

## 

MIT。Copyright (c) 2026 Escalated.dev。詳細は [LICENSE](../../LICENSE) を参照してください。
