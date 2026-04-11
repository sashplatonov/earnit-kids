/** @file Build Version utility helpers */
const BUILD_ENV_KEYS = [
    'BUILD_TIMESTAMP',
    'RENDER_BUILD_TIMESTAMP',
    'RENDER_DEPLOYMENT_START_TIME',
];

const padTwo = (value) => String(value).padStart(2, '0');

const HYBRID_TIMESTAMP = /^(\d{4})-(\d{2})-(\d{2})-(\d{2})-(\d{2})$/;

const parseHyphenatedTimestamp = (value) => {
    if (typeof value !== 'string') return null;
    const match = value.trim().match(HYBRID_TIMESTAMP);
    if (!match) return null;
    const [, year, month, day, hours, minutes] = match;
    return new Date(Date.UTC(
        Number(year),
        Number(month) - 1,
        Number(day),
        Number(hours),
        Number(minutes),
    ));
};

const toDate = (value) => {
    if (!value) {
        return null;
    }

    if (value instanceof Date) {
        return value;
    }

    const parsedHyphenated = parseHyphenatedTimestamp(value);
    if (parsedHyphenated) {
        return parsedHyphenated;
    }

    const parsedValue = typeof value === 'number' ? value : Number(value);
    if (!Number.isNaN(parsedValue)) {
        const parsedDate = new Date(parsedValue);
        if (!Number.isNaN(parsedDate.getTime())) {
            return parsedDate;
        }
    }

    const fallbackDate = new Date(value);
    if (!Number.isNaN(fallbackDate.getTime())) {
        return fallbackDate;
    }

    return null;
};

const formatBuildValue = (value) => {
    const date = toDate(value) ?? new Date();
    const year = date.getUTCFullYear();
    const month = padTwo(date.getUTCMonth() + 1);
    const day = padTwo(date.getUTCDate());
    const hours = padTwo(date.getUTCHours());
    const minutes = padTwo(date.getUTCMinutes());

    return `${year}-${month}-${day}-${hours}-${minutes}`;
};

const getBuildVersion = () => {
    for (const key of BUILD_ENV_KEYS) {
        const value = process.env[key];
        if (value) {
            return formatBuildValue(value);
        }
    }

    return formatBuildValue();
};

module.exports = {
    formatBuildValue,
    getBuildVersion,
};
