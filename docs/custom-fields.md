# Custom fields and custom objects

Escalated-laravel's model, shared by every port.

## Field definitions

`escalated_custom_fields`. The admin pages and JSON use Laravel's names; this port's older columns
keep theirs underneath (`field_key` is the slug, `field_type` the type, `is_required`,
`sort_order` the position, `is_active`).

| Key | Notes |
|---|---|
| `name` | Required, up to 255 characters. |
| `slug` | Unique. Made from the name (`Order Number` becomes `order_number`, then `order_number_2`...) when none is given. Kept on update unless one is sent. |
| `type` | `text`, `textarea`, `select`, `multi_select`, `checkbox`, `date`, `number`. |
| `context` | `ticket` (default), `user` or `organization`. Only `ticket` fields take ticket values. |
| `options` | A list of strings. `select` and `multi_select` need at least one. |
| `required`, `active` | Booleans (default `false`, `true`). |
| `placeholder`, `description` | Shown by forms. |
| `validation_rules`, `conditions` | JSON, stored and shown, not enforced. |
| `position` | Order; `POST .../custom-fields/reorder` with `positions: [{id, position}]`. |

## Ticket values

Ticket writes take `custom_fields`, an object keyed by field slug (or numeric id):

```json
{"subject": "...", "custom_fields": {"order_number": "PO-9", "plan": "pro", "tags": ["a", "b"], "vip": true}}
```

Taken by `POST /escalated/api/admin/tickets`, `PUT /escalated/api/admin/tickets/{id}`,
`POST /escalated/api/customer/tickets` and the customer page's `POST /escalated`. Guest and widget
creates do not take them yet.

- Only active fields with context `ticket` count; any other key is an error.
- Creating, every required field must be present and non-empty. Updating, only the keys sent are
  checked and changed; leaving `custom_fields` out changes nothing; an empty value for an optional
  field clears it.
- `number` must be numeric, `date` a valid `YYYY-MM-DD`, `checkbox` a boolean or
  `0`/`1`/`"0"`/`"1"`/`"true"`/`"false"`, `select` one of its options, `multi_select` a list of its
  options, `text` a string up to 255 characters, `textarea` a string.
- Errors are keyed `custom_fields.<slug>`: a 422 `{message, errors}` from the JSON API, back to the
  form with `errors` from a page. They are checked before the ticket is written, so a refused
  create creates no ticket.

Stored as text: `multi_select` a JSON array, `checkbox` `"1"`/`"0"`, the rest as given. Reads
decode them: `custom_fields` is a list of the active ticket fields by position, each
`{id, name, slug, type, options, required, placeholder, description, position, value}`, with
`value` a list, boolean, number or string (`null` when unset). It is on the admin, agent and
customer JSON ticket detail, a top-level prop of the agent, admin and customer `Show` pages, and
(without `value`) a prop of the customer `Create` page.

## Custom objects

`escalated_custom_objects` (`name`, unique `slug` of letters, numbers, `-` and `_` up to 100,
`fields_schema`: a non-empty list of `{name, type, required?, options?}` with `type` one of `text`,
`number`, `select`, `date`, `lookup`) and `escalated_custom_object_records` (`object_id`, `data`).

A record's `data` is an object checked against the schema: required fields non-empty, `number`
numeric, `date` a valid date, `select` one of its options, and no keys the schema lacks. Errors
are keyed `data.<name>`. Deleting an object deletes its records; a record of another object is a
404.

Pages (behind `escalated.ui.enabled`, admins only): `Escalated/Admin/CustomObjects/Index`
(`objects`, each with `records_count`), `Form` (`object`) and `Records` (`object`, `records`
newest first). JSON: `/escalated/api/admin/custom-objects` and `.../{id}/records`.
