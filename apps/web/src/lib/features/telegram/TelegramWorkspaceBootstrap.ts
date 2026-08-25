import {
    completeTelegramAccountLink,
    exchangeTelegramInitData,
    initializeTelegramWebApp,
} from '$lib/services/telegram';

export type TelegramBootstrapState = 'ready' | 'retry' | 'unavailable' | 'unlinked' | 'non-telegram';

export type TelegramBootstrapResult = {
    state: TelegramBootstrapState;
    role?: string;
    locale?: 'en' | 'ru';
    languageSetupRequired?: boolean;
    message?: string;
};

function isHexToken(value: string): boolean {
    return /^[0-9a-fA-F]+$/.test(value);
}

export async function bootstrapTelegramWorkspace(): Promise<TelegramBootstrapResult> {
    const telegram = initializeTelegramWebApp();
    if (!telegram) return { state: 'non-telegram' };
    if (!telegram.initData) return { state: 'retry' };

    const rawStartParam = telegram.initDataUnsafe?.start_param
        ?? new URLSearchParams(window.location.search).get('tgWebAppStartParam');
    const childInviteToken = rawStartParam?.startsWith('ci_') ? rawStartParam : '';
    let pairingFailed = false;
    const pairingToken = rawStartParam && isHexToken(rawStartParam) ? rawStartParam : '';
    if (pairingToken && !childInviteToken) {
        pairingFailed = !(await completeTelegramAccountLink(pairingToken, telegram.initData)).ok;
    }

    const response = await exchangeTelegramInitData(telegram.initData, childInviteToken || null);
    if (!response.ok) {
        return {
            state: response.status === 404 ? 'unavailable'
                : pairingFailed ? 'retry' : 'unlinked',
        };
    }

    try {
        const payload = await response.clone().json() as { role?: unknown; locale?: unknown; languageSetupRequired?: unknown };
        return {
            state: 'ready',
            role: typeof payload.role === 'string' ? payload.role : '',
            locale: payload.locale === 'ru' ? 'ru' : 'en',
            languageSetupRequired: payload.languageSetupRequired === true,
        };
    } catch {
        return { state: 'ready', role: '' };
    }
}
