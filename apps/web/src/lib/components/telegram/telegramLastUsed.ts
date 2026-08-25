/**
 * Human, localized "last used" time for Mini App task/reward rows.
 *
 * Produces compact relative labels such as `сегодня, 08:32` or `12 авг., 19:40`
 * so screens never render raw ISO timestamps.
 */
import { formatDate, formatRelativeTime, type Locale } from '$lib/i18n';

export function formatLastUsedTime(value: string | null | undefined, locale: Locale): string {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';

    const now = new Date();
    const sameDay = date.getFullYear() === now.getFullYear()
        && date.getMonth() === now.getMonth()
        && date.getDate() === now.getDate();

    if (sameDay) {
        const time = formatDate(locale, date, {
            hour: '2-digit',
            minute: '2-digit',
        });
        return `${formatRelativeTime(locale, 0, 'day')}, ${time}`;
    }

    return formatDate(locale, date, {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
    });
}
