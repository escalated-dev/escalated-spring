# Local translation overrides

Drop `messages_{locale}.properties` files in this directory to override
individual translation keys shipped by the central
[`dev.escalated:escalated-locale`](https://github.com/escalated-dev/escalated-locale)
Maven artifact.

## Resolution order

Spring's `MessageSource` walks the chain configured in
[`MessageSourceConfig`](../../java/dev/escalated/config/MessageSourceConfig.java)
in order. The first basename to resolve a key wins:

1. `classpath:i18n/overrides/messages` — files in **this** directory
2. `classpath:META-INF/escalated/locale/messages` — shipped by the central artifact

Any key you do *not* redefine here continues to resolve from the central
bundle, so override files can be sparse.

## Example

To rename `status.open` to "Active" for English only:

```properties
# src/main/resources/i18n/overrides/messages_en.properties
status.open=Active
```

## Conventions

- Use the same key names as the central artifact (`status.*`, `priority.*`,
  `activity.*`, …).
- Keep override files **sparse** — only the keys you actually want to change.
- Filename uses Java's `Locale` convention: `messages_en.properties`,
  `messages_pt_BR.properties`, etc.
- UTF-8 encoded.

## When NOT to use overrides

If a translation in the central artifact is wrong (typo, mistranslation),
open a PR against [`escalated-locale`](https://github.com/escalated-dev/escalated-locale)
instead — every host plugin benefits from the fix.

Reserve this directory for host-app branding adjustments ("Open" → "Active",
"Ticket" → "Case", etc.).
