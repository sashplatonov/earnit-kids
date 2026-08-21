import type { Request } from '$lib/stores/app';
import { getTelegramEntityIcon } from './telegramEntityIcons';
import type { TelegramIconName } from './telegramIconMap';
import { requestKind, type RequestKind } from './telegramRequestKind';

export type RequestStatus = 'pending' | 'approved' | 'rejected' | 'cancelled' | 'unknown';
export type RequestStatusTone = Exclude<RequestStatus, 'unknown'> | 'neutral';

export interface RequestPresentationTranslator {
    kindLabel: (kind: RequestKind) => string;
    statusLabel: (status: RequestStatus) => string;
    metadata: (request: Request, kind: RequestKind, kindLabel: string) => string;
}

export interface TelegramRequestPresentation {
    request: Request;
    title: string;
    kind: RequestKind;
    kindLabel: string;
    entityIcon: TelegramIconName;
    amount: number;
    amountSign: '+' | '-';
    isReward: boolean;
    metadata: string;
    status: RequestStatus;
    statusLabel: string;
    statusTone: RequestStatusTone;
    createdAt: string | null | undefined;
}

export function requestStatus(status: string): RequestStatus {
    if (status === 'pending' || status === 'approved' || status === 'rejected' || status === 'cancelled') {
        return status;
    }
    return 'unknown';
}

export function requestStatusTone(status: string): RequestStatusTone {
    const normalized = requestStatus(status);
    return normalized === 'unknown' ? 'neutral' : normalized;
}

export function presentRequest(request: Request, translator: RequestPresentationTranslator): TelegramRequestPresentation {
    const kind = requestKind(request);
    const kindLabel = translator.kindLabel(kind);
    const title = request.taskName || request.itemName || request.title || kindLabel;

    return {
        request,
        title,
        kind,
        kindLabel,
        entityIcon: getTelegramEntityIcon({
            kind,
            title,
            group: request.taskGroup || request.itemGroup || request.groupName,
        }),
        amount: Math.abs(request.coins ?? request.amount ?? 0),
        amountSign: kind === 'reward' ? '-' : '+',
        isReward: kind === 'reward',
        metadata: translator.metadata(request, kind, kindLabel),
        status: requestStatus(request.status),
        statusLabel: translator.statusLabel(requestStatus(request.status)),
        statusTone: requestStatusTone(request.status),
        createdAt: request.createdAt,
    };
}

export function sortRequestPresentations<T extends { request: Request }>(presentations: T[]): T[] {
    return [...presentations].sort((a, b) => {
        const aPending = a.request.status === 'pending';
        const bPending = b.request.status === 'pending';
        if (aPending !== bPending) return aPending ? -1 : 1;

        const aTime = a.request.createdAt ? new Date(a.request.createdAt).getTime() : 0;
        const bTime = b.request.createdAt ? new Date(b.request.createdAt).getTime() : 0;
        const safeATime = Number.isFinite(aTime) ? aTime : 0;
        const safeBTime = Number.isFinite(bTime) ? bTime : 0;
        return safeBTime - safeATime;
    });
}
