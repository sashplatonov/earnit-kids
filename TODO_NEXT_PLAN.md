# Optimize Coins-Kids-Shop for AI Agent (Codex) — Token Efficiency Plan

## Problem

The project uses an AI agent (Codex) for development. Current configuration wastes tokens because:

1. **[AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md) in repo** references a global rules folder with **57 rule directories** — most irrelevant (Next.js, React, TypeScript, Supabase, etc.) for this vanilla Node.js/HTML/CSS project
2. **Global [AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md)** forces the agent to scan all 57 directories to pick relevant rules — expensive context loading
3. **`Fix Rule.md`** is 78 lines (~900 tokens) but [AI_FIX_LOG.md](file:///Users/sash/AI%20Rules/AI_FIX_LOG.md) is **empty** — no learning happening
4. **[default.rules](file:///Users/sash/AI%20Rules/rules/default.rules)** contains auto-approve rules for a different project (`bara-web`) and Java (`./mvnw`) — noise
5. **Documentation is scattered** across [AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md), [docs/architecture.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/docs/architecture.md), `docs/rules-*.md`, [improvement-plan.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/docs/improvement-plan.md), [TODO_NEXT_PLAN.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/TODO_NEXT_PLAN.md) — agent must read 6+ files to understand the project
6. **No Codex-native configuration** — no `codex.md` or `.codex/` directory with optimized instructions

---

## Summary of Findings

### Current State of Agent Rules

| File | Location | Size | Status |
|------|----------|------|--------|
| [AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md) | repo root | 76 lines | ✅ Good, but mixed languages (EN + RU), could be concise |
| Global [AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md) | `/Users/sash/AI Rules/` | 16 lines | ⚠️ Makes agent scan 57 folders |
| `Fix Rule.md` | `/Users/sash/AI Rules/` | 78 lines | ⚠️ Good idea, but log is empty |
| `default.rules` | `/Users/sash/AI Rules/rules/` | 27 lines | ⚠️ Contains rules for other projects |
| `docs/architecture.md` | repo docs | ~200 lines | ✅ Excellent documentation |
| `docs/rules-backend.md` | repo docs | ~150 lines | ⚠️ Redundant with AGENTS.md |
| `docs/rules-frontend.md` | repo docs | ~160 lines | ⚠️ Redundant with AGENTS.md |
| `docs/rules-database.md` | repo docs | ~120 lines | ⚠️ Redundant with AGENTS.md |

### Token Cost Estimates (per agent session)

| Action | Tokens (approx.) | Status |
|--------|-------------------|--------|
| Reading `AGENTS.md` | ~1,200 | OK |
| Agent scanning 57 rule dirs | ~3,000–5,000 | ❌ Wasteful |
| Reading irrelevant rules | ~2,000–4,000 | ❌ Wasteful |
| Reading redundant `docs/rules-*.md` | ~2,500 | ⚠️ Context not always needed |
| Reading empty `AI_FIX_LOG.md` | ~200 | Minor waste |
| **Total waste per session** | **~8,000–12,000** | **Fixable** |

---

## Proposed Changes

### Component 1: Optimized `AGENTS.md` for the repo

#### [MODIFY] [AGENTS.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/AGENTS.md)

Rewrite to be a **single source of truth** for AI agents. Consolidate all critical info into one file, eliminating the need for agents to read `docs/rules-*.md` separately.

Key changes:
- Move the essential backend/frontend/DB rules inline (concisely)
- Remove the global rules indirection
- Add a structured "What to know" section with file map
- Add concise verification commands
- Keep under 120 lines total
- All content in English (per project rules), agent output instructions in Russian

---

### Component 2: Clean up global rules

#### [MODIFY] [AGENTS.md](file:///Users/sash/AI%20Rules/AGENTS.md)

Replace the generic "scan all rules/" approach with explicit per-project pointers:

```diff
-When executing tasks via Codex agents, always apply additional local rules from:
-`/Users/sash/AI Rules/rules`
-
-Select which local rules to apply based on:
-- The task context
-- The rule filename/name in the rules folder
+When executing tasks via Codex agents, use only the project's own `AGENTS.md`.
+Additional rules are only needed if the project AGENTS.md explicitly references them.
```

> [!IMPORTANT]
> This change affects ALL projects. If you have other projects that rely on the global scanning, we should make this project-specific instead.

---

### Component 3: Project-specific `default.rules` cleanup

#### [MODIFY] [default.rules](file:///Users/sash/AI%20Rules/rules/default.rules)

Remove rules for other projects and Java. Add project-specific auto-approve rules:

**Remove:**
- All `bara-web` specific rules (lines 8, 10, 11)
- All `./mvnw` rules (lines 20-23)
- Port 8080 curl rules (lines 4, 12-16)
- Process management rules (lines 17-18)

**Keep/Add for coins-kids-shop:**
```
prefix_rule(pattern=["npm", "install"], decision="allow")
prefix_rule(pattern=["npm", "run", "lint"], decision="allow")
prefix_rule(pattern=["npm", "run", "typecheck"], decision="allow")
prefix_rule(pattern=["npm", "test"], decision="allow")
prefix_rule(pattern=["npm", "run", "test"], decision="allow")
prefix_rule(pattern=["npm", "start"], decision="allow")
prefix_rule(pattern=["npm", "run", "migrate"], decision="allow")
prefix_rule(pattern=["docker", "compose", "up", "-d"], decision="allow")
prefix_rule(pattern=["docker", "compose", "logs"], decision="allow")
prefix_rule(pattern=["docker", "compose", "ps"], decision="allow")
prefix_rule(pattern=["docker", "compose", "down"], decision="allow")
prefix_rule(pattern=["node", "--test"], decision="allow")
prefix_rule(pattern=["sort"], decision="allow")
prefix_rule(pattern=["cat"], decision="allow")
prefix_rule(pattern=["head"], decision="allow")
prefix_rule(pattern=["tail"], decision="allow")
```

---

### Component 4: AI_FIX_LOG.md — Agent Learning from Mistakes

> [!IMPORTANT]
> This is a **real learning mechanism**: when the user points out an error, the agent records it and **never repeats it**.

#### [MODIFY] [AI_FIX_LOG.md](file:///Users/sash/AI%20Rules/AI_FIX_LOG.md)

Initialize as the agent's persistent memory. Agent reads this **at the start of every session**:

```markdown
# AI Fix Log — Agent Learning Memory

This file is the agent's persistent memory of past mistakes.
**Every session MUST start by reading this file.**

## How this works
1. User points out an error → agent records it here
2. Next session → agent reads this file FIRST
3. Agent applies prevention rules BEFORE writing any code

## Active Prevention Rules
<!-- Agent: add compact 1-line rules here as you learn -->

(no rules yet)

## Error History
<!-- Use format: ## [YYYY-MM-DD] <title> / Symptom / Root cause / Fix / Prevention rule / Tags -->

(no entries yet)
```

#### [MODIFY] [AGENTS.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/AGENTS.md)

Add a mandatory section for error learning:

```markdown
## Error Learning (mandatory)
- At session start: read `/Users/sash/AI Rules/AI_FIX_LOG.md`
- Apply ALL prevention rules listed there before writing any code
- When user points out a mistake → record it in the log + add a prevention rule
- Never repeat a logged mistake
```

#### [MODIFY] [Fix Rule.md](file:///Users/sash/AI%20Rules/Fix%20Rule.md)

Simplify from 78 lines (~900 tokens) to ~10 lines (~100 tokens). The detailed format is now in `AI_FIX_LOG.md` itself:

```markdown
# Fix Rule

You maintain a persistent error log at `/Users/sash/AI Rules/AI_FIX_LOG.md`.

1. **Read it** at the start of every task
2. **Apply** all prevention rules before writing code
3. **Record** any new error the user points out (use the format in the log file)
4. **Never repeat** a logged mistake
5. Make the **smallest fix** possible, add a test if logic changed
```

---

### Component 5: Create Codex-native instructions

#### [NEW] [codex.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/codex.md)

Some versions of Codex look for `codex.md` or `CODEX.md` at the root. Create a concise pointer:

```markdown
# Codex Instructions

Refer to `AGENTS.md` for all project rules and conventions.
```

This avoids duplication while ensuring Codex picks up instructions automatically.

---

### Component 6: Consolidate docs for agent-readability

#### [MODIFY] [architecture.md](file:///Users/sash/Dev/Projects/coins-kids-shop-web/docs/architecture.md)

No changes needed — already excellent documentation.

#### `docs/rules-backend.md`, `docs/rules-frontend.md`, `docs/rules-database.md`

**No deletion** — keep for human reference. But add a header note:

```markdown
> [!NOTE]  
> AI agents: core rules are in root `AGENTS.md`. This file provides extended detail for humans.
```

This prevents agents from spending tokens on redundant reading.

---

## Summary of Expected Savings

| Optimization | Tokens Saved Per Session |
|--------------|--------------------------|
| Remove global rules scanning (57 dirs) | ~3,000–5,000 |
| Consolidate rules into `AGENTS.md` | ~2,000–3,000 |
| Clean up `default.rules` | ~500 (less noise) |
| Add "skip" headers to `docs/rules-*.md` | ~2,500 |
| Simplify `Fix Rule.md` (78→10 lines) | ~800 |
| Initialize `AI_FIX_LOG.md` as learning memory | ~200 |
| **Total savings** | **~9,000–12,000 tokens/session** |

With ~20 agent sessions/week, that's **~180k–240k tokens/week** saved.

---

## Files to Change — Summary

| File | Action | Impact |
|------|--------|--------|
| `AGENTS.md` (repo) | Rewrite & consolidate | High — single source of truth |
| `AGENTS.md` (global) | Simplify | High — stops scanning 57 dirs |
| `default.rules` | Clean up | Medium — reduces noise |
| `AI_FIX_LOG.md` | Initialize as learning memory | High — prevents repeated mistakes |
| `Fix Rule.md` | Simplify 78→10 lines | Medium — saves ~800 tokens/session |
| `codex.md` (new) | Create | Low — Codex compatibility |
| `docs/rules-*.md` | Add skip header | Low — prevents agent reading |

---

## Verification Plan

### Automated Tests

No code changes — these are documentation/config files only. Verify with:

```bash
cd /Users/sash/Dev/Projects/coins-kids-shop-web
npm run lint     # Ensure no lint regressions
npm test         # Ensure no test regressions
```

### Manual Verification

1. Run a Codex session on a simple task and observe:
    - Does the agent read fewer files at startup?
    - Does it correctly understand project conventions from `AGENTS.md` alone?
    - Does it auto-approve common commands via `default.rules`?
2. Verify `AGENTS.md` content is complete enough for agent autonomy
