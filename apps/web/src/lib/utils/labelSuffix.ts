/**
 * Formats a "Label: value" suffix.
 *
 * Some labels are already translated with trailing punctuation (e.g. "Note:").
 * If we blindly add another colon in the UI, we end up with "Note:: value".
 */

export function formatLabelSuffix(label: string, value: string, separator = ':'): string {
    const safeLabel = (label ?? '').trim();
    const safeValue = (value ?? '').trim();

    if (!safeLabel || !safeValue) return '';

    // If translator already included punctuation ("Note:"), don't add it again.
    const needsSeparator = !safeLabel.endsWith(separator);

    return `${safeLabel}${needsSeparator ? separator : ''} ${safeValue}`;
}
