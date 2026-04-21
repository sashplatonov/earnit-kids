import { browser } from '$app/environment';
import { getContext, setContext } from 'svelte';
import { writable, type Writable } from 'svelte/store';
import { FALLBACK_I18N_PAYLOAD, createTranslationRuntime, type I18nPayload, type TranslationRuntime } from './index';

const I18N_CONTEXT = Symbol('earnit-kids-i18n');

let clientRuntime: TranslationRuntime = createTranslationRuntime(FALLBACK_I18N_PAYLOAD);

export function provideI18n(payload: I18nPayload): Writable<TranslationRuntime> {
    const runtime = createTranslationRuntime(payload);
    const store = writable(runtime);
    setContext(I18N_CONTEXT, store);

    if (browser) {
        clientRuntime = runtime;
        document.documentElement.lang = runtime.locale;
    }

    return store;
}

export function updateI18n(store: Writable<TranslationRuntime>, payload: I18nPayload): void {
    const runtime = createTranslationRuntime(payload);
    store.set(runtime);

    if (browser) {
        clientRuntime = runtime;
        document.documentElement.lang = runtime.locale;
    }
}

export function useI18n(): Writable<TranslationRuntime> {
    return getContext<Writable<TranslationRuntime>>(I18N_CONTEXT);
}

export function translateClient(...args: Parameters<TranslationRuntime['t']>): string {
    return clientRuntime.t(...args);
}
