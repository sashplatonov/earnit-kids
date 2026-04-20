import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import type { Request, ShopItem, Task } from '../../src/lib/stores/app';
import { buildRequestCatalog, resolveRequestCard } from '../../src/lib/components/app/sections/requestDetails';

describe('resolveRequestCard', () => {
    it('fills task request description and group from the task card', () => {
        const task: Task = {
            id: 7,
            name: 'Уроки без напоминаний',
            coins: 12,
            groupName: 'Учеба',
            comment: 'Собрать рюкзак и сделать домашнее задание',
        };
        const request = {
            id: 1,
            requestType: 'earn',
            taskId: 7,
            taskName: 'Уроки без напоминаний',
            status: 'pending',
            coins: 12,
            moneyAmount: 0,
        } as Request;

        const details = resolveRequestCard(request, buildRequestCatalog({ tasks: [task] }));

        expect(details.title).toBe('Уроки без напоминаний');
        expect(details.description).toBe('Собрать рюкзак и сделать домашнее задание');
        expect(details.group).toBe('Учеба');
        expect(details.coins).toBe(12);
        expect(details.moneyAmount).toBe(0);
        expect(details.isPurchase).toBe(false);
        expect(details.typeLabel).toBe('Задание');
        expect(details.typeChipClass).toBe('request-chip--type-task');
        expect(details.iconClass).toBe('icon-coin-stack');
        expect(details.amountPrefix).toBe('+');
    });

    it('fills purchase request description and group from the reward card', () => {
        const reward: ShopItem = {
            id: 11,
            name: 'Небольшая косметика',
            price: 10,
            groupName: 'Красота',
            comment: 'Только после согласования с родителем',
            moneyLimit: 800,
        };
        const request = {
            id: 2,
            requestType: 'shop_purchase',
            taskId: 11,
            itemId: 11,
            taskName: 'Небольшая косметика',
            status: 'pending',
            coins: 10,
            moneyAmount: 800,
        } as Request;

        const details = resolveRequestCard(request, buildRequestCatalog({ shopItems: [reward] }));

        expect(details.title).toBe('Небольшая косметика');
        expect(details.description).toBe('Только после согласования с родителем');
        expect(details.group).toBe('Красота');
        expect(details.coins).toBe(10);
        expect(details.moneyAmount).toBe(800);
        expect(details.isPurchase).toBe(true);
        expect(details.typeLabel).toBe('Товар');
        expect(details.typeChipClass).toBe('request-chip--type-purchase');
        expect(details.iconClass).toBe('icon-shop');
        expect(details.amountPrefix).toBe('−');
    });

    it('keeps explicit request fields when they are present', () => {
        const task: Task = {
            id: 4,
            name: 'Тренировка',
            coins: 5,
            groupName: 'Спорт',
            comment: 'Из карточки',
        };
        const request = {
            id: 3,
            requestType: 'earn',
            taskId: 4,
            taskName: 'Тренировка',
            status: 'approved',
            coins: 5,
            taskGroup: 'Особая группа',
            taskComment: 'Свое описание из заявки',
        } as Request;

        const details = resolveRequestCard(request, buildRequestCatalog({ tasks: [task] }));

        expect(details.group).toBe('Особая группа');
        expect(details.description).toBe('Свое описание из заявки');
    });

    it('prefers pre-resolved backend title and group fields', () => {
        const request = {
            id: 4,
            requestType: 'earn',
            status: 'pending',
            title: 'Готовое название',
            description: 'Готовое описание',
            groupName: 'Готовая группа',
            coins: 9,
            moneyAmount: 120,
        } as Request;

        const details = resolveRequestCard(request, buildRequestCatalog());

        expect(details.title).toBe('Готовое название');
        expect(details.description).toBe('Готовое описание');
        expect(details.group).toBe('Готовая группа');
        expect(details.coins).toBe(9);
        expect(details.moneyAmount).toBe(120);
    });
});

describe('app shell body padding override', () => {
    it('removes public top-nav body padding for the authenticated app shell', () => {
        const appHtml = readFileSync(new URL('../../src/app.html', import.meta.url), 'utf8');

        expect(appHtml).toContain('body:has(#app)');
        expect(appHtml).toContain('padding-top: 0 !important;');
    });
});