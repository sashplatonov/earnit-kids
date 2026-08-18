export interface AchievementBadge {
    id: string;
    category: 'level' | 'tasks' | 'coins' | 'streak' | 'shop';
    tier: 'bronze' | 'silver' | 'gold' | 'platinum' | 'diamond';
    icon: string;
    /** Tier medal emoji (🥉🥈🥇💠💎) */
    tierIcon: string;
    name: string;
    description: string;
    current: number;
    target: number;
    percent: number;
    earned: boolean;
    /** Name of the highest tier already earned (null if none yet) — shown in tooltip */
    earnedBadge: string | null;
}

export interface AchievementI18n {
    formatNumber(value: number): string;
    t(key: string, variables?: Record<string, string | number>): string;
}

interface AchievementDef {
    id: string;
    category: AchievementBadge['category'];
    tier: AchievementBadge['tier'];
    icon: string;
    nameKey: string;
    descKey: string;
    target: number;
}

const TIER_ICONS: Record<AchievementDef['tier'], string> = {
    bronze: '🥉',
    silver: '🥈',
    gold: '🥇',
    platinum: '💠',
    diamond: '💎',
};

const ACHIEVEMENT_DEFS: AchievementDef[] = [
    // ⭐ Level
    { id: 'level-bronze',   category: 'level',  tier: 'bronze',   icon: '⭐', nameKey: 'achLevelBronze',   descKey: 'achLevelDesc', target: 2 },
    { id: 'level-silver',   category: 'level',  tier: 'silver',   icon: '🌟', nameKey: 'achLevelSilver',   descKey: 'achLevelDesc', target: 5 },
    { id: 'level-gold',     category: 'level',  tier: 'gold',     icon: '👑', nameKey: 'achLevelGold',     descKey: 'achLevelDesc', target: 10 },
    { id: 'level-platinum', category: 'level',  tier: 'platinum', icon: '💠', nameKey: 'achLevelPlatinum', descKey: 'achLevelDesc', target: 20 },
    { id: 'level-diamond',  category: 'level',  tier: 'diamond',  icon: '💎', nameKey: 'achLevelDiamond',  descKey: 'achLevelDesc', target: 40 },
    // ✅ Tasks
    { id: 'tasks-bronze',   category: 'tasks', tier: 'bronze',   icon: '✅', nameKey: 'achTasksBronze',   descKey: 'achTasksDesc', target: 5 },
    { id: 'tasks-silver',   category: 'tasks', tier: 'silver',   icon: '📋', nameKey: 'achTasksSilver',   descKey: 'achTasksDesc', target: 25 },
    { id: 'tasks-gold',     category: 'tasks', tier: 'gold',     icon: '🏆', nameKey: 'achTasksGold',     descKey: 'achTasksDesc', target: 100 },
    { id: 'tasks-platinum', category: 'tasks', tier: 'platinum', icon: '🎖️', nameKey: 'achTasksPlatinum', descKey: 'achTasksDesc', target: 250 },
    { id: 'tasks-diamond',  category: 'tasks', tier: 'diamond',  icon: '🏅', nameKey: 'achTasksDiamond',  descKey: 'achTasksDesc', target: 500 },
    // 🪙 Coins
    { id: 'coins-bronze',   category: 'coins', tier: 'bronze',   icon: '🪙', nameKey: 'achCoinsBronze',   descKey: 'achCoinsDesc', target: 50 },
    { id: 'coins-silver',   category: 'coins', tier: 'silver',   icon: '💰', nameKey: 'achCoinsSilver',   descKey: 'achCoinsDesc', target: 250 },
    { id: 'coins-gold',     category: 'coins', tier: 'gold',     icon: '💎', nameKey: 'achCoinsGold',     descKey: 'achCoinsDesc', target: 1000 },
    { id: 'coins-platinum', category: 'coins', tier: 'platinum', icon: '💠', nameKey: 'achCoinsPlatinum', descKey: 'achCoinsDesc', target: 2500 },
    { id: 'coins-diamond',  category: 'coins', tier: 'diamond',  icon: '👑', nameKey: 'achCoinsDiamond',  descKey: 'achCoinsDesc', target: 5000 },
    // 🔥 Streak
    { id: 'streak-bronze',   category: 'streak', tier: 'bronze',   icon: '🔥', nameKey: 'achStreakBronze',   descKey: 'achStreakDesc', target: 3 },
    { id: 'streak-silver',   category: 'streak', tier: 'silver',   icon: '💥', nameKey: 'achStreakSilver',   descKey: 'achStreakDesc', target: 7 },
    { id: 'streak-gold',     category: 'streak', tier: 'gold',     icon: '🌋', nameKey: 'achStreakGold',     descKey: 'achStreakDesc', target: 30 },
    { id: 'streak-platinum', category: 'streak', tier: 'platinum', icon: '⚡', nameKey: 'achStreakPlatinum', descKey: 'achStreakDesc', target: 60 },
    { id: 'streak-diamond',  category: 'streak', tier: 'diamond',  icon: '🌟', nameKey: 'achStreakDiamond',  descKey: 'achStreakDesc', target: 100 },
    // 🛒 Shop
    { id: 'shop-bronze',   category: 'shop', tier: 'bronze',   icon: '🛒',  nameKey: 'achShopBronze',   descKey: 'achShopDesc', target: 1 },
    { id: 'shop-silver',   category: 'shop', tier: 'silver',   icon: '🛍️', nameKey: 'achShopSilver',   descKey: 'achShopDesc', target: 5 },
    { id: 'shop-gold',     category: 'shop', tier: 'gold',     icon: '🎪', nameKey: 'achShopGold',     descKey: 'achShopDesc', target: 15 },
    { id: 'shop-platinum', category: 'shop', tier: 'platinum', icon: '🏪', nameKey: 'achShopPlatinum', descKey: 'achShopDesc', target: 30 },
    { id: 'shop-diamond',  category: 'shop', tier: 'diamond',  icon: '🎉', nameKey: 'achShopDiamond',  descKey: 'achShopDesc', target: 50 },
];


export interface BuildAchievementsInput {
    earned: number;
    taskCount: number;
    itemCount: number;
    streakValue: number;
    i18n: AchievementI18n;
}

/**
 * Build the "next achievement in each category" view.
 * For each category, finds the highest-tier badge the kid hasn't earned yet
 * and shows progress toward it.
 * If all tiers are earned, shows the gold badge as "completed".
 */
export function buildAchievements(input: BuildAchievementsInput): AchievementBadge[] {
    const { earned, taskCount, itemCount, streakValue, i18n } = input;

    const level = Math.floor(earned / 120) + 1;

    const metrics: Record<AchievementBadge['category'], number> = {
        level,
        tasks: taskCount,
        coins: earned,
        streak: streakValue,
        shop: itemCount,
    };

    const categories: AchievementBadge['category'][] = ['level', 'tasks', 'coins', 'streak', 'shop'];

    return categories.map((category) => {
        const defs = ACHIEVEMENT_DEFS.filter((d) => d.category === category);
        const current = Math.max(0, metrics[category]);

        // Walk tiers in order; the first one NOT yet earned becomes the target badge.
        // Track the highest tier that IS earned for the tooltip.
        let targetDef: AchievementDef = defs[defs.length - 1];
        let earned = true;
        let lastEarnedDef: AchievementDef | null = null;

        for (const def of defs) {
            if (current < def.target) {
                targetDef = def;
                earned = false;
                break;
            }
            lastEarnedDef = def;
        }

        // If all tiers earned, lastEarnedDef is the diamond (last in list).
        // If some tiers earned before the target, lastEarnedDef is the one right before targetDef.
        const earnedBadge = lastEarnedDef != null ? i18n.t(lastEarnedDef.nameKey) : null;

        return {
            id: targetDef.id,
            category: targetDef.category,
            tier: targetDef.tier,
            icon: targetDef.icon,
            tierIcon: TIER_ICONS[targetDef.tier],
            name: i18n.t(targetDef.nameKey),
            description: i18n.t(targetDef.descKey, {
                current: i18n.formatNumber(current),
                target: i18n.formatNumber(targetDef.target),
            }),
            current,
            target: targetDef.target,
            percent: targetDef.target > 0
                ? Math.max(0, Math.round((current / targetDef.target) * 100))
                : 0,
            earned,
            earnedBadge,
        };
    });
}

export function buildAchievementsI18n(analyticsI18n: {
    formatNumber(value: number): string;
    t(key: string, variables?: Record<string, string | number>): string;
}): AchievementI18n {
    return {
        formatNumber: analyticsI18n.formatNumber,
        t(key, variables) {
            return analyticsI18n.t(key, variables);
        },
    };
}
