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
    await page.getByRole('button', { name: 'Выполнил!' }).click();
    await expect(page.getByText('Заявка отправлена')).toBeVisible();

    await logout(page);
    await loginParent(page, email, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    await loginChildByMagicLink(page, childLink, taskTitle);
    await expectHeaderBalance(page, 50);
    await page.getByRole('tab', { name: 'Награды' }).click();
    await expect(page.getByRole('heading', { name: rewardTitle })).toBeVisible();
    await page.getByRole('button', { name: 'Запросить' }).click();
    await expect(page.getByText('Заявка на покупку отправлена!')).toBeVisible();

    await logout(page);
    await loginParent(page, email, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    await loginChildByMagicLink(page, childLink);
    await page.getByRole('tab', { name: 'Награды' }).click();
    await expectHeaderBalance(page, 0);
});