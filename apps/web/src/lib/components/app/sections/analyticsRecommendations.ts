export interface AnalyticsRecommendationView {
    id: string;
    icon: string;
    text: string;
}

export function normalizeAnalyticsRecommendations(
    input: Array<{ icon?: unknown; text?: unknown }> | null | undefined
): AnalyticsRecommendationView[] {
    return (input ?? [])
        .map((item, index) => {
            const icon = typeof item?.icon === 'string' && item.icon.trim() !== '' ? item.icon : '✨';
            const text = typeof item?.text === 'string' ? item.text.trim() : '';
            return {
                id: `${index}:${icon}:${text}`,
                icon,
                text,
            };
        })
        .filter((item) => item.text !== '');
}