// EXPLAIN: Content ownership checklist — when product mechanics change,
// EXPLAIN: verify the corresponding public content is updated.
//
// permissions             → /parents, /how, FAQ
// task mechanics/catalog  → /tasks, /how, home
// reward mechanics        → /rewards, /how, home
// coins/approval flow     → /how, /parents, FAQ
// Telegram requirements   → /parents, FAQ, CTA microcopy
// privacy/security        → /parents, FAQ
//
// This file is documentation-only; it is not imported at runtime.

export const contentOwnershipMap = {
    permissions: ['parents', 'how', 'faq'],
    taskMechanics: ['tasks', 'how', 'home'],
    rewardMechanics: ['rewards', 'how', 'home'],
    coinsApprovalFlow: ['how', 'parents', 'faq'],
    telegramRequirements: ['parents', 'faq'],
    privacySecurity: ['parents', 'faq'],
} as const;