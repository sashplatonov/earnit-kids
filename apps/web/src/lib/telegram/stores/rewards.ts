import { writable, type Writable } from 'svelte/store';
import type { CatalogRewardTemplate } from '$lib/telegram/stores/types';

export const catalogRewards: Writable<CatalogRewardTemplate[]> = writable([]);