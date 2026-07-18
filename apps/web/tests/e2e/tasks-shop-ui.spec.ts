import { expect, test } from '@playwright/test';
import {
    DEFAULT_PARENT_PASSWORD,
    addChild,
    createReward,
    createTask,
    getChildMagicLink,
    loginChildByMagicLink,
    loginParent,
    logout,
    openFamilyApp,
    registerParent,
    uniqueEmail,
} from './helpers';
import { assertCatalogCardLayout, assertCatalogGroupLayout, assertDesktopCatalogRow } from './cardLayoutAssertions';

const CHILD_NAME = 'Визуальная проверка';
const TASK_TITLE = 'Подготовить школьный рюкзак и проверить все тетради на завтра';
const REWARD_TITLE = 'Большой семейный вечер с настольной игрой и любимым десертом';

test('parent and child share the modern Tasks and Shop progress surfaces', async ({ page }) => {
    test.setTimeout(90_000);
    const email = uniqueEmail('tasks.shop.visual');
    await page.setViewportSize({ width: 1024, height: 900 });
    await registerParent(page, email, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);
    await addChild(page, CHILD_NAME);
    await createTask(page, TASK_TITLE, 35, 'Длинное описание проверяет, что карточка растёт и элементы не перекрываются.');
    await createTask(page, 'Прочитать десять страниц книги', 15, 'Вторая группа проверяет перенос фильтров.', 'Чтение и развитие');
    await createReward(page, REWARD_TITLE, 120, 'Награда с длинным названием и читаемыми чипами.');
    await createReward(page, 'Поход в любимое кафе', 80, 'Вторая группа проверяет desktop wrap.', 'Семейные впечатления');
    const childLink = await getChildMagicLink(page);

    await page.getByRole('link', { name: /Tasks|Задания/i }).click();
    await expect(page.locator('#tasks-section .today-summary')).toBeVisible();
    await expect(page.locator('#tasks-section .today-summary__eyebrow')).toContainText(/Today|Сегодня/i);
    await expect(page.locator('#tasks-section .reward-goal')).toContainText(/has not chosen|пока не выбрал/i);
    await expect(page.locator('#tasks-section .today-summary')).toHaveCSS('display', 'grid');
    expect(await page.locator('#tasks-section .today-summary').evaluate((element) => element.getBoundingClientRect().height)).toBeLessThanOrEqual(110);
    await assertCatalogCardLayout(page.locator('#tasks-list .task-card').first());
    await assertDesktopCatalogRow(page.locator('#tasks-list .task-card').first());
    await assertCatalogGroupLayout(page.locator('#tasks-group-nav'), false);

    await page.goto('/app/shop');
    await expect(page.locator('#shop-section')).toBeVisible();
    await expect(page.locator('#shop-section .reward-goal')).toContainText(/No reward goal|Цель-награда пока не выбрана/i);
    await assertCatalogCardLayout(page.locator('#shop-list .shop-card').first());
    await assertDesktopCatalogRow(page.locator('#shop-list .shop-card').first());
    await assertCatalogGroupLayout(page.locator('#shop-group-nav'), false);

    await logout(page);
    await loginChildByMagicLink(page, childLink, TASK_TITLE);
    await page.goto('/app/shop');
    await expect(page.locator('#shop-section')).toBeVisible();
    await page.locator('[data-shop-action="goal"]').first().click();
    await expect(page.locator('#shop-section .reward-goal')).toContainText(REWARD_TITLE);

    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.locator('#shop-section .catalog-section-header')).toBeVisible();
    await assertCatalogGroupLayout(page.locator('#shop-group-nav'), true);
    await assertCatalogCardLayout(page.locator('#shop-list .shop-card').first());
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);

    await logout(page);
    await loginParent(page, email, DEFAULT_PARENT_PASSWORD);
    await page.getByRole('link', { name: /Rewards|Награды/i }).click();
    await expect(page.locator('#shop-section .reward-goal')).toContainText(REWARD_TITLE);
    await expect(page.locator('#shop-section .reward-goal__clear')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);

    await page.getByRole('link', { name: /Tasks|Задания/i }).click();
    await expect(page.locator('#tasks-section .today-summary')).toContainText(REWARD_TITLE);
    await assertCatalogGroupLayout(page.locator('#tasks-group-nav'), true);
    await assertCatalogCardLayout(page.locator('#tasks-list .task-card').first());
});
