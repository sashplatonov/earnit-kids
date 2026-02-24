# Optimization Plan v2 — MCP Self-Setup + Smart Rule Linking

## Goal

Further reduce Codex agent token usage by ~3,700–6,200 tokens/session through smart rule selection, MCP self-configuration, and context filtering.

---

## Component 1: Smart Rule Linking (instead of deleting rule dirs)

### Problem
`/Users/sash/AI Rules/rules/` has 57 rule directories for different tech stacks. Agent shouldn't scan all of them, but some may be relevant to specific projects.

### Solution
Add an instruction to `AGENTS.md` that tells the agent to **auto-select relevant rules** during project initialization and link only those in the project's `AGENTS.md`.

#### [MODIFY] [AGENTS.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/AGENTS.md)

Add a new section:

```markdown
## Rule Selection (on first session or when asked to update rules)
When initializing or updating this file, scan `/Users/sash/AI Rules/rules/` and:
1. Read each rule directory name — match against the project's tech stack (Node.js, vanilla JS, PostgreSQL, CommonJS)
2. For relevant rule sets, read and extract useful rules
3. Add links to only those relevant rule files in the "Extended Rules" section below
4. Ignore rule sets for frameworks/stacks not used in this project

### Extended Rules
<!-- Agent: add links to relevant rule files here during init -->
- [git-conventional-commit-messages](file:///Users/sash/AI%20Rules/rules/git-conventional-commit-messages) — commit message format
```

**Impact:** Agent reads ~2-3 relevant rule files instead of scanning 57. New projects also benefit — agent picks their stack-specific rules automatically.

---

## Component 2: MCP Self-Configuration

### Problem
Agent needs MCP servers for efficient file access, docs lookup, and DB schema queries — but shouldn't require manual setup.

### Solution
Add an instruction to `AGENTS.md` telling the agent to **configure MCP servers itself** on first session.

#### [MODIFY] [AGENTS.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/AGENTS.md)

Add section:

```markdown
## MCP Server Setup (auto-configure on first session)
If `.codex/config.toml` does not exist or is missing MCP entries, create/update it with:

### Required MCP servers
1. **filesystem** — targeted file reads instead of full files
   `codex mcp add filesystem -- npx -y @anthropic/mcp-filesystem /Users/sash/Dev/Projects/coins-kids-shop-web`
2. **context7** — library documentation without web search
   `codex mcp add context7 -- npx -y @anthropic/context7-mcp`
3. **postgres** — direct DB schema/data queries (read-only)
   `codex mcp add postgres -- npx -y @anthropic/mcp-postgres`
   Set `DATABASE_URL` from the project's docker-compose.yml or .env.example (never read .env directly)

After setup, verify with `codex mcp list`.
```

#### [NEW] [.codex/config.toml](file:///Users/sash/Dev/Projects/coins-kids-shop-web/.codex/config.toml)

Create as a starter template that agent will extend:

```toml
# Codex CLI project configuration
# Agent auto-manages MCP entries — do not remove this file
```

**Impact:** Agent sets up its own tools on first run. No manual config needed. Saves ~2,000–4,000 tokens/session after MCP is active.

---

## Component 3: Auto-Generate `.codexignore`

### Problem
Codex may scan irrelevant directories: `node_modules`, `coverage`, `.playwright-browsers`, `data/`.

### Solution
Add a **global instruction** so the agent auto-creates/updates `.codexignore` during project initialization.

#### [MODIFY] [Global AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md)

Add to global rules:

```markdown
## Context Filtering (on first session per project)
If `.codexignore` does not exist in the project root, create it. If it exists, review and update.
Always exclude: `node_modules/`, `coverage/`, `.git/`, `.idea/`, `test-results/`.
Scan the project for other non-source directories (build artifacts, data dumps, browser caches) and add them.
```

**Savings:** ~500–1,000 tokens/session. Works across ALL projects, not just this one.

---

## Component 4: Auto-Generate JSDoc `@file` Headers

### Problem
Agent reads full files (200–400 lines) just to understand their purpose.

### Solution
Add a **global instruction** so the agent auto-adds/updates `@file` JSDoc headers during project initialization.

#### [MODIFY] [Global AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md)

Add to global rules:

```markdown
## File Headers (on first session per project)
For every source file in `src/` and `public/js/` that lacks a `/** @file ... */` header:
- Add a 1-line JSDoc `@file` comment as the first line
- Describe the file's purpose, main exports, and tech (e.g., "SQL queries", "REST routes")
- Keep it under 100 characters
- Do NOT modify existing headers unless they are inaccurate
```

**Savings:** ~500 tokens/session. Agent reads the 1-line header and decides whether to read the full file.

---

## Component 5: Expand `default.rules`

#### [MODIFY] [default.rules](file:///Users/sash/AI%20Rules/rules/default.rules)

Append safe commands for auto-approval:

```
prefix_rule(pattern=["npm", "run", "check"], decision="allow")
prefix_rule(pattern=["npm", "run", "build"], decision="allow")
prefix_rule(pattern=["npm", "run", "test:coverage"], decision="allow")
prefix_rule(pattern=["npm", "run", "test:integration"], decision="allow")
prefix_rule(pattern=["npx", "-y"], decision="allow")
prefix_rule(pattern=["ls"], decision="allow")
prefix_rule(pattern=["wc"], decision="allow")
prefix_rule(pattern=["grep"], decision="allow")
prefix_rule(pattern=["find"], decision="allow")
prefix_rule(pattern=["echo"], decision="allow")
prefix_rule(pattern=["mkdir"], decision="allow")
prefix_rule(pattern=["codex", "mcp"], decision="allow")
```

**Impact:** Fewer approval pauses → faster sessions

---

## Summary

| Component | Savings/Session |
|-----------|----------------|
| Smart rule linking (2-3 vs 57 dirs) | ~500 |
| MCP filesystem (targeted reads) | ~1,000–2,000 |
| MCP context7 (docs without web search) | ~500–1,000 |
| MCP postgres (direct DB schema) | ~500–1,000 |
| Auto `.codexignore` (global) | ~500–1,000 |
| Auto JSDoc headers (global) | ~500 |
| Expanded `default.rules` | ~200 (speed) |
| **Total v2 savings** | **~3,700–6,200 tokens/session** |

### Combined with v1

| Phase | Savings/Session |
|-------|----------------|
| v1 (completed) | ~9,000–12,000 |
| v2 (this plan) | ~3,700–6,200 |
| **Total** | **~12,700–18,200 tokens/session** |
| **~20 sessions/week** | **~255k–365k tokens/week** |

---

## Verification

```bash
npm run lint && npm test   # No regressions from JSDoc changes
codex mcp list             # MCP servers registered after first session
```

## Progress
- [x] Component 1 (Rule Selection) и Component 2 (MCP Server Setup) реализованы в проектных правилах и `.codex/config.toml` (24 февраля 2026). Пока не требовались глобальные изменения.
- [x] Component 3 (Context Filtering) добавлена секция в глобальный `AGENTS.md` и создан `.codexignore` (24 февраля 2026).
- [x] Component 4 (File Headers) описана процедура в глобальном `AGENTS.md` (24 февраля 2026).
- [x] Component 5 (Expanded `default.rules`) выполнена — добавлены все безопасные `prefix_rule` (24 февраля 2026).
- План v2 завершён; текущие инструкции охватывают все указанные компоненты.
