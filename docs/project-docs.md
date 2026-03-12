# Project Docs Playbook
<a name="top"></a>

## Who, Why, and What now
- **Audience:** teammates owning documentation or runbooks for EarnIt Kids.
- **Goal:** capture the current documentation expectations, testing/verif flow, and reading order so future doc updates stay consistent.
- **Current behavior:** instructions live in `AGENTS.md`, but a focused docs guide helps collaborators remember the required commands, manual steps, and order-of-operations for design + backend references.

[↩ Back to toc](#table-of-contents)

## Table of Contents {#table-of-contents}
1. [Testing & verification](#testing-verification)
2. [Manual shop/purchase checks](#manual-shop-flows)
3. [Documentation index & reading order](#documentation-index)
4. [Doc author reminders](#doc-reminders)

[↑ Back to top](#top)

## Testing & verification {#testing-verification}
- Run the full automated suite after doc changes: `npm run lint`, `npm test`, `npm run build`.
- Mention the manual Playwright smoke command `npm run test:ui:e2e` whenever reporting verification status.
- All commands must pass before closing the session per `AGENTS.md` (and failures should be filed as follow‑ups rather than skipped).

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## Manual shop/purchase checks {#manual-shop-flows}
Execute these manual flows whenever you touch shop‑ or purchase‑related material (even if automated tests cover them indirectly):
1. Add, edit, and delete a shop item to exercise the UI, validations, and persistence logic.
2. Perform an admin direct purchase flow to confirm payment routing and notification payloads.
3. Submit a child request, approve/deny it as admin, and confirm resulting notification/state changes.
4. Toggle frequency/money limits and observe both the request UI and backend enforcement responses.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## Documentation index & reading order {#documentation-index}
| File | When to read |
|------|-------------|
| `docs/design-concept.md` | First for anything design-related (visual language, UX principles, constraints). |
| `docs/rules-frontend.md` | After design concept: CSS/HTMX patterns and frontend-specific rules. |
| `docs/architecture.md` | Onboard to overall system design before touching architectural sections. |
| `docs/rules-backend.md` | When redefining backend coding or data-flow patterns. |
| `docs/rules-database.md` | Prioritize for SQL, migrations, schema guidance. |
| `docs/telegram-setup.md` | Refer to this for Telegram bot integration procedures. |
| `docs/openapi.yaml` | Consult when working on API surfaces or requirements. |

These files anchor the broader doc universe and are referenced from `AGENTS.md`. Add new docs here and link them from this table or `AGENTS.md` if they become cross-cutting.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)

## Doc author reminders {#doc-reminders}
- Keep sections compact and command-driven; prefer bullets/steps over long prose.
- Include assumptions, failure modes, and rollback notes when changes affect operations.
- Anchor every long doc with a short TOC; add quick navigation helpers after each major section (e.g., `[↑ Back to top]`).
- Use emojis only when they add clarity (📝 for tips, ⚠️ for warnings, ✅ for outcomes).
- Update this playbook whenever verification commands or manual flows change.

[↩ Back to toc](#table-of-contents) | [↑ Back to top](#top)
