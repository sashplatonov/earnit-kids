import { expect, test } from '@playwright/test';
import {
    DEFAULT_PARENT_PASSWORD,
    addChild,
    approveFirstRequest,
    createReward,
    createTask,
    expectHeaderBalance,
    getChildMagicLink,
    loginChildByMagicLink,
    loginParent,
    logout,
    openFamilyApp,
    registerParent,
    uniqueEmail,
} from './helpers';

test('parent can register, child can complete task, and reward purchase is approved', async ({ page }) => {
    const email = uniqueEmail('family.flow');
    const childName = 'Тестовый Ребёнок';
    const taskTitle = 'Убрать комнату';
    const rewardTitle = 'Час игры на PlayStation';

    await registerParent(page, email, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);

    await addChild(page, childName);
    await createTask(page, taskTitle, 50, 'Убрать игрушки и застелить кровать');
    await createReward(page, rewardTitle, 50, 'После уроков');

    const childLink = await getChildMagicLink(page);

    await loginChildByMagicLink(page, childLink, taskTitle);
    await expectHeaderBalance(page, 0);
    await page.locator('#tasks-section [data-task-action="request"]').first().click();
    await expect(page.getByText(/Request sent|Заявка отправлена/i)).toBeVisible();

    await logout(page);
    await loginParent(page, email, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    await loginChildByMagicLink(page, childLink, taskTitle);
    await expectHeaderBalance(page, 50);
    await page.getByRole('link', { name: /Rewards|Награды/i }).click();
    await expect(page.getByRole('heading', { name: rewardTitle })).toBeVisible();
    await page.locator('#shop-section [data-shop-action="request"]').first().click();
    await expect(page.getByText(/Purchase request sent|Заявка на покупку отправлена/i)).toBeVisible();

    await logout(page);
    await loginParent(page, email, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    await loginChildByMagicLink(page, childLink);
    await page.getByRole('link', { name: /Rewards|Награды/i }).click();
    await expectHeaderBalance(page, 0);
});