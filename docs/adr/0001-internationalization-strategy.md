# ADR 0001: Internationalization strategy

- Status: Accepted
- Date: 2026-08-25
- Scope: public web, authenticated workspace, Telegram Mini App, Telegram Bot,
  and REST presentation boundaries

## Context

EarnIt Kids has one family-scoped domain but several presentation surfaces:
the public web site, the authenticated browser workspace, the Telegram Mini
App, and Telegram Bot delivery. The existing web frontend already has a typed
SvelteKit catalog, SSR locale state, URL and cookie handling, and native
`Intl` formatting. The backend already owns request-locale handling and
localized Bean Validation messages.

The product needs English and Russian first, while leaving room for Serbian
and additional locales. A public visitor may prefer a language independently
of any family. A family, however, must have one language so that parents,
children, the Mini App, and bot messages agree. API and domain contracts must
remain language-neutral, and user-created task, reward, and catalog values
must remain data rather than translation keys.

## Decision

We will extend the existing typed SvelteKit catalog and use the platform
`Intl` APIs. We will not introduce a separate i18n framework, translation
service, shared frontend/backend catalog package, or database translation
table for the current product.

The initially supported locale identifiers are `en` and `ru`. Locale input is
normalized to a supported base language: `en`, `en-US`, and other `en-*`
values resolve to `en`; `ru`, `ru-RU`, and other `ru-*` values resolve to
`ru`; unsupported values resolve to `en`. Persisted family locale values are
the normalized base tags only. Future locales must be added to the shared
supported-locale list, catalog parity checks, backend bundles where needed,
and the resolution tests before they can be selected or persisted.

### Ownership and precedence

Public pages are visitor-owned and use canonical `/{locale}/...` URLs. The
public precedence is URL locale, then the locale cookie, then
`Accept-Language`, then `en`. A language control writes the URL and cookie;
the URL is authoritative for indexed public content.

Authenticated family surfaces are family-owned. A family administrator
chooses the locale during first family entry and may change it later. The
saved family locale overrides URL, cookie, `Accept-Language`, and Telegram's
`user.language_code` for workspace, Mini App, and Bot presentation. An
unconfigured family has `NULL` locale: its administrator must complete the
blocking setup choice, while non-administrators receive the safe `en`
fallback and cannot write a locale. No personal parent, child, or Telegram
identity locale is persisted.

| Surface | Owner | Resolution precedence | Fallback |
| --- | --- | --- | --- |
| Public web | Visitor | URL, cookie, `Accept-Language` | `en` |
| Authenticated workspace | Family administrator's saved family locale | Family locale over all client hints; unconfigured non-admin fallback | `en` |
| Telegram Mini App | Saved family locale | Family locale over Telegram/browser hints | `en` until setup |
| Telegram Bot | Recipient's family | Family locale resolved at delivery time | `en` |

### Translation ownership and safety

Web UI translations live in the typed frontend catalog. Telegram Bot messages
and other server-generated delivery text live in backend resource bundles.
Logs remain developer-facing English. Catalog values never contain untrusted
HTML; user values are interpolated as text. Telegram HTML/Markdown is escaped
at the delivery boundary, and callback data uses stable identifiers rather
than translated labels.

The existing catalog must gain lazy domain loading, locale-parity validation,
typed interpolation contracts, and `Intl.PluralRules`/
`Intl.DateTimeFormat` coverage as implementation work proceeds. This keeps
SSR-safe behavior and avoids a framework migration whose runtime and typing
cost would exceed the current two-locale requirement.

Email localization is not applicable yet: no email delivery or email-template
producer exists in the repository. If email is introduced, it must define its
own server-owned bundle and recipient-locale contract first. User-created task,
reward, and catalog names are never translation keys and are never
automatically translated.

### API error strategy

REST errors expose a stable machine-readable contract: the existing RFC-7807
fields plus `errorCode`, safe `params`, and `traceId`. `errorCode` and
`params` are identical for the same failure regardless of request locale.
During the compatibility window, `detail` may contain bounded localized
presentation text for existing consumers, but frontend logic must never parse
or branch on it. Web maps codes to its own catalog and uses a generic localized
fallback for unknown newer codes. Backend bundles are used only by REST
adapters and server-generated channels, never by domain logic.

Validation, authorization, business conflicts, and unknown failures map to
deterministic codes. Parameters contain only safe field names and typed safe
values; they never expose exception class names, SQL values, raw headers, or
user-provided HTML. API enums and display labels remain language-neutral.

## Alternatives considered

1. **Add a general-purpose web i18n library.** Rejected for now: the existing
   typed catalog already provides the required SSR boundary and route-domain
   organization. It needs validation and loading improvements, not a migration.
2. **Share one translation package between frontend and backend.** Rejected:
   deployment and runtime coupling would increase while web UI and bot
   delivery have different ownership and escaping requirements.
3. **Persist a locale on every parent, child, or Telegram identity.** Rejected:
   it would allow one family to present contradictory languages and make bot
   delivery depend on a personal hint instead of family policy.
4. **Store translated values in database tables.** Rejected for current
   user-owned task, reward, and catalog data. Those values are user content,
   not system copy; stable translation keys would be appropriate only for a
   future system-owned preset catalog.
5. **Use localized API enums and display labels.** Rejected: API contracts
   must be stable across clients and locales; presentation resolves labels at
   the owning surface.

## Consequences

- Public links are explicit and indexable by language, while family content
  remains consistent across browser and Telegram clients.
- Adding a locale requires catalog parity, backend bundle, normalization, and
  regression-test updates rather than business-logic changes.
- Existing localized REST `detail` consumers can migrate incrementally, but
  new clients must use codes, parameters, and trace IDs.
- Family onboarding gains a required administrator decision for existing
  families with no configured locale.
- There is no email work until an email producer exists, and no automatic
  translation or localization of user-created content.

## Public URL and SEO strategy

Public canonical pages use `/{locale}/...`; each localized page emits its
locale-aware canonical URL and alternate-language links, and the sitemap
lists supported localized URLs. The bare `/telegram` entry point remains a
non-indexed launch URL because Telegram supplies its launch parameters there.
It does not establish the family locale.
