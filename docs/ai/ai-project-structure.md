Goal: Create a compact, token-efficient project documentation pack in /docs that lets me plan features in ChatGPT without scanning the repo.

Repo rules: follow AGENTS.md. Use ripgrep before reading files. Read minimal sections only. Do not paste file contents unless asked. Keep docs concise.

Deliverables (create or update these files):
1) docs/PROJECT_BRIEF.md
2) docs/REPO_MAP.md
3) docs/ARCH_CONTRACTS.md
4) docs/DEV_WORKFLOW.md
5) docs/FEATURE_TEMPLATE.md
6) docs/PLAN_OUTPUT_SPEC.md (defines the exact output format for plan/tasks/handoff)

Constraints:
- Each file must be short and stable:
    - PROJECT_BRIEF: 1–2 pages max
    - REPO_MAP: ~40–80 lines
    - ARCH_CONTRACTS: only stable interfaces/contracts, no essays
    - DEV_WORKFLOW: commands + where configs live + common pitfalls (short)
    - FEATURE_TEMPLATE: fill-in form I will copy into ChatGPT
    - PLAN_OUTPUT_SPEC: strict format for plan.md, tasks.md, handoff.md
- Prefer bullets, tables only if necessary.
- Do not include secrets. Never open .env. Use .env.example only.

Method:
- Use ripgrep to locate the single most important entry points:
    - package scripts / build commands
    - app bootstrap/server entry
    - routing/controller locations
    - DB migrations folder + how migrations are applied
    - role/permission model if present
- Read only the relevant parts of those files.
- Produce the docs. Keep wording compact and actionable.

After writing:
- Run minimal checks (only if repo has them): lint/test/build as appropriate.
- Provide a short summary and a Conventional Commit message.