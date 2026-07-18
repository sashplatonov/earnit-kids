import { expect, type Locator } from '@playwright/test';

type Rect = {
    selector?: string;
    left: number;
    right: number;
    top: number;
    bottom: number;
    width: number;
    height: number;
};

const CARD_REGIONS = ['.card__badge-row', '.card__header', '.card__comment', '.card__meta', '.card__actions'];

function intersects(first: Rect, second: Rect): boolean {
    return first.left < second.right && first.right > second.left && first.top < second.bottom && first.bottom > second.top;
}

/**
 * Protects the catalog card contract against CSS regressions that are hard to
 * catch with text-only assertions: regions must stay inside the card and must
 * not overlap unrelated sibling regions.
 */
export async function assertCatalogCardLayout(card: Locator): Promise<void> {
    const result = await card.evaluate((element, selectors) => {
        const cardRect = element.getBoundingClientRect();
        const regions: Rect[] = [];
        for (const selector of selectors) {
            const node = element.querySelector(selector);
            if (!(node instanceof HTMLElement)) continue;
            const rect = node.getBoundingClientRect();
            const styles = getComputedStyle(node);
            if (rect.width <= 0 || rect.height <= 0 || styles.display === 'none') continue;
            regions.push({
                selector,
                left: rect.left,
                right: rect.right,
                top: rect.top,
                bottom: rect.bottom,
                width: rect.width,
                height: rect.height,
            });
        }

        const chips = Array.from(element.querySelectorAll('.card__badge, .card__status, .card__compact-chip'))
            .filter((node): node is HTMLElement => node instanceof HTMLElement)
            .filter((node) => {
                const rect = node.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0 && getComputedStyle(node).display !== 'none';
            })
            .map((node) => {
                const rect = node.getBoundingClientRect();
                return {
                    text: node.textContent?.trim() ?? '',
                    left: rect.left,
                    right: rect.right,
                    top: rect.top,
                    bottom: rect.bottom,
                    width: rect.width,
                    height: rect.height,
                    scrollWidth: node.scrollWidth,
                    clientWidth: node.clientWidth,
                    scrollHeight: node.scrollHeight,
                    clientHeight: node.clientHeight,
                    lineHeight: Number.parseFloat(getComputedStyle(node).lineHeight),
                };
            });

        return {
            card: {
                left: cardRect.left,
                right: cardRect.right,
                top: cardRect.top,
                bottom: cardRect.bottom,
            },
            regions,
            chips,
        };
    }, CARD_REGIONS);

    for (const region of result.regions) {
        expect(region.left, `${region.selector} escapes card left edge`).toBeGreaterThanOrEqual(result.card.left - 1);
        expect(region.right, `${region.selector} escapes card right edge`).toBeLessThanOrEqual(result.card.right + 1);
        expect(region.top, `${region.selector} escapes card top edge`).toBeGreaterThanOrEqual(result.card.top - 1);
        expect(region.bottom, `${region.selector} escapes card bottom edge`).toBeLessThanOrEqual(result.card.bottom + 1);
    }

    for (let index = 0; index < result.regions.length; index += 1) {
        for (let nextIndex = index + 1; nextIndex < result.regions.length; nextIndex += 1) {
            expect(
                intersects(result.regions[index], result.regions[nextIndex]),
                `${result.regions[index].selector} overlaps ${result.regions[nextIndex].selector}`
            ).toBe(false);
        }
    }

    for (const chip of result.chips) {
        expect(chip.text, 'chip text should not be empty').not.toBe('');
        expect(chip.scrollWidth, `chip "${chip.text}" clips horizontally`).toBeLessThanOrEqual(chip.clientWidth + 1);
        expect(chip.scrollHeight, `chip "${chip.text}" clips vertically`).toBeLessThanOrEqual(chip.clientHeight + 1);
        expect(chip.height, `chip "${chip.text}" has no readable height`).toBeGreaterThanOrEqual(20);
        expect(chip.lineHeight, `chip "${chip.text}" has invalid line height`).toBeGreaterThan(0);
    }

    for (let index = 0; index < result.chips.length; index += 1) {
        for (let nextIndex = index + 1; nextIndex < result.chips.length; nextIndex += 1) {
            expect(
                intersects(result.chips[index], result.chips[nextIndex]),
                `chip "${result.chips[index].text}" overlaps chip "${result.chips[nextIndex].text}"`
            ).toBe(false);
        }
    }
}

export async function assertDesktopCatalogRow(card: Locator): Promise<void> {
    const layout = await card.evaluate((element) => {
        const content = element.querySelector('.task-card__layout, .shop-card__layout');
        return {
            height: element.getBoundingClientRect().height,
            minHeight: getComputedStyle(element).minHeight,
            flexWrap: content instanceof HTMLElement ? getComputedStyle(content).flexWrap : '',
            progressBars: element.querySelectorAll('.catalog-progress').length,
        };
    });

    expect(layout.minHeight).toBe('0px');
    expect(layout.flexWrap).toBe('nowrap');
    expect(layout.height, 'list card should remain a compact desktop row').toBeLessThanOrEqual(110);
    expect(layout.progressBars, 'list card should not contain progress bars').toBe(0);
}

export async function assertCatalogGroupLayout(groupNav: Locator, mobile: boolean): Promise<void> {
    const layout = await groupNav.locator('.catalog-group-nav__scroll').evaluate((element, isMobile) => {
        if (!isMobile) element.style.width = '260px';
        const styles = getComputedStyle(element);
        const result = {
            flexWrap: styles.flexWrap,
            overflowX: styles.overflowX,
            clientHeight: element.clientHeight,
        };
        if (!isMobile) element.style.removeProperty('width');
        return result;
    }, mobile);

    expect(layout.flexWrap).toBe(mobile ? 'nowrap' : 'wrap');
    if (mobile) expect(layout.overflowX).toBe('auto');
    else expect(layout.clientHeight, 'desktop groups should wrap into multiple rows when constrained').toBeGreaterThan(50);
}
