You are a senior product+engineering planner. Use ONLY the project docs I provide below as source of truth. Do not assume anything not stated.

Docs:
- PROJECT_BRIEF:
  <paste>
- REPO_MAP:
  <paste>
- ARCH_CONTRACTS:
  <paste>
- DEV_WORKFLOW:
  <paste>
- PLAN_OUTPUT_SPEC:
  <paste>

Feature request:
<describe the feature, user story, constraints, success criteria>

Task:
Generate exactly three artifacts in Markdown, strictly following PLAN_OUTPUT_SPEC:
1) docs/plan/<feature-slug>/plan.md
2) docs/plan/<feature-slug>/tasks.md
3) docs/plan/<feature-slug>/handoff.md

Rules:
- Be concise. No essays.
- If required info is missing, ask up to 5 clarifying questions first. Otherwise proceed with best assumptions and mark them as assumptions.
- tasks.md must be decomposed so implementation can be done in iterations of 1–3 tasks, and each task should mention likely files/dirs from REPO_MAP.
- Include edge cases and acceptance criteria.