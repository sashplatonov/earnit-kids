# Design Concept — EarnIt Kids

## Purpose

This document defines the visual and interaction concept for public and authenticated UI surfaces in EarnIt Kids.  
It should be used as a decision baseline for any frontend design update.

## Product Feel

- Family-friendly and trustworthy, not childish or noisy.
- Calm dark-first interface with clear accents for actions and status.
- Fast to scan on mobile, with strong visual hierarchy for daily routines.

## Core Design Principles

1. Clarity over decoration  
Primary actions, balances, and request statuses must be obvious within the first screen view.
2. Consistent semantics  
The same color, icon, and wording pattern must represent the same meaning across all pages.
3. Progressive disclosure  
Show critical information first; place advanced options in secondary controls or modals.
4. Role-aware UI  
Parent/admin and child views must share visual language but expose different action density and risk controls.

## Visual Language

### Color

- Dark-first canvas with high text contrast.
- Accent colors communicate intent, not decoration:
  - Primary action
  - Positive/approved state
  - Warning/limit state
  - Critical/destructive state
- Avoid random one-off colors in components; every color token must map to a semantic role.

### Typography

- Use rounded, human-friendly sans-serif for readability on small screens.
- Keep a strict type scale for:
  - page title
  - section title
  - body text
  - helper/meta text
- Avoid dense paragraphs in app UI; prefer short labels and compact helper text.

### Shape and Spacing

- Soft card geometry and clear grouping of related actions.
- Keep spacing rhythm consistent within sections and across modals.
- Dense data views (history, requests) should prioritize alignment and scanability over large decorative spacing.

## Interaction Patterns

### Buttons and Actions

- Primary action: visually dominant and unique per viewport section.
- Secondary action: less prominent, never competing with primary action.
- Destructive action: always explicit and visually distinct.

### Forms and Inputs

- Labels must be explicit and short.
- Validation feedback appears near the field and uses consistent wording.
- Avoid hidden required fields and implicit side effects.

### State and Feedback

- Loading, success, empty, and error states are mandatory for each data-driven section.
- Toasts are for short confirmation only; critical errors need inline context and recovery path.
- Request lifecycle (`pending`, `approved`, `rejected`) must remain visually consistent in every module.

## Motion and Micro-interactions

- Use small purposeful transitions for section switching, modal open/close, and list updates.
- Motion should support orientation and feedback, never distract from task completion.
- Respect reduced-motion preferences when possible.

## Responsive Behavior

- Mobile-first layouts are mandatory.
- Navigation and key actions must stay reachable with one thumb.
- Tables and dense admin content should degrade to readable stacked layouts on small widths.

## Public Pages vs App UI

- Public pages must use plain Russian copy for parents and children.
- Public pages should focus on value and onboarding clarity, without internal implementation details.
- Authenticated UI can be more operational but should keep the same tone and visual semantics.

## Design Review Checklist

Use this checklist before merging visual changes:

- Is the main user action obvious on first view?
- Are color and icon semantics consistent with existing patterns?
- Is the change readable and usable on mobile?
- Are empty/loading/error states covered?
- Does the copy stay simple and user-facing (especially on public pages)?
- Does the update preserve parent/child flow consistency for tasks, shop, and requests?
