import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { messages } from './i18n.js';
import { PUBLIC_LOCALES, PUBLIC_PAGES, publicLanguageHref, resolvePublicOrigin } from './urls.js';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, '../..');
const templatePath = path.join(scriptDirectory, 'template.html');
const pagesDirectory = path.join(scriptDirectory, 'pages');
const outputDirectory = process.env.PUBLIC_OUTPUT_DIR
    ? path.resolve(projectDirectory, process.env.PUBLIC_OUTPUT_DIR)
    : path.join(projectDirectory, 'static/public');
const template = await readFile(templatePath, 'utf8');
const publicOrigin = resolvePublicOrigin(process.env.APP_URL, {
    production: process.env.DEPLOYMENT_ENV === 'production',
});

const escapeAttribute = (value) => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const replaceAll = (source, values) => Object.entries(values).reduce((result, [key, value]) => result.replaceAll(`{{${key}}}`, String(value)), source);

function navigationFor(activeKey, locale) {
    const prefix = locale === 'ru' ? '/ru' : '';
    const productPages = ['how', 'tasks', 'rewards', 'parents'];
    const productActive = productPages.includes(activeKey) ? ' active' : '';
    const productLinks = productPages.map((key) => `<a href="${prefix}/how.html#${key}">${messages[locale].pageTitles[key]}</a>`).join('');
    const homeActive = activeKey === 'index' ? ' active' : '';
    const faqActive = activeKey === 'faq' ? ' active' : '';
    const homeCurrent = activeKey === 'index' ? ' aria-current="page"' : '';
    const faqCurrent = activeKey === 'faq' ? ' aria-current="page"' : '';
    const homeLabel = messages[locale].pageTitles.index;
    const faqLabel = messages[locale].pageTitles.faq;
    const demoPath = locale === 'ru' ? '/ru/demo' : '/demo';
    return `<a class="tab${homeActive}" href="${prefix}/"${homeCurrent}>${homeLabel}</a><details class="menu-popover${productActive}"><summary class="tab">${messages[locale].productMenu}</summary><div class="menu-popover__panel">${productLinks}</div></details><a class="tab${faqActive}" href="${prefix}/faq.html"${faqCurrent}>${faqLabel}</a><a class="tab" href="${demoPath}">${messages[locale].demo}</a>`;
}

function combinedProductContent(locale) {
    return ['how', 'tasks', 'rewards', 'parents'].map((key) => {
        const content = messages[locale].pages[key].content;
        return content.replace('<section class="page-hero">', `<section id="${key}" class="page-hero">`);
    }).join('');
}

async function generateLocale(locale) {
    const localeDirectory = locale === 'ru' ? path.join(outputDirectory, 'ru') : outputDirectory;
    await mkdir(localeDirectory, { recursive: true });
    for (const page of PUBLIC_PAGES) {
        const fragment = await readFile(path.join(pagesDirectory, `${page.key}.html`), 'utf8');
        const content = page.key === 'how' ? combinedProductContent(locale) : messages[locale].pages[page.key].content;
        const localizedContent = fragment.replaceAll(`{{${page.key}.content}}`, content);
        const englishPath = page.englishPath;
        const output = replaceAll(template, {
            LANG: locale,
            TITLE: escapeAttribute(messages[locale].pageTitles[page.key]),
            DESCRIPTION: escapeAttribute(messages[locale].descriptions[page.key]),
            CANONICAL: publicLanguageHref(englishPath, locale, publicOrigin),
            EN_URL: publicLanguageHref(englishPath, 'en', publicOrigin),
            RU_URL: publicLanguageHref(englishPath, 'ru', publicOrigin),
            EN_SWITCH_URL: `${publicLanguageHref(englishPath, 'en', publicOrigin)}?lang=en`,
            RU_SWITCH_URL: `${publicLanguageHref(englishPath, 'ru', publicOrigin)}?lang=ru`,
            EN_CURRENT: locale === 'en' ? ' aria-current="page"' : '',
            RU_CURRENT: locale === 'ru' ? ' aria-current="page"' : '',
            HOME_URL: locale === 'ru' ? '/ru/' : '/',
            NAV_LABEL: escapeAttribute(messages[locale].navLabel),
            BRAND_LABEL: escapeAttribute(messages[locale].brandLabel),
            FOOTER: escapeAttribute(messages[locale].footer),
            SKIP_LINK: escapeAttribute(messages[locale].skipLink),
            NAV: navigationFor(page.key, locale),
            PAGE_KEY: page.key,
            LANGUAGE_GROUP: escapeAttribute(messages[locale].languageGroup),
            TELEGRAM: escapeAttribute(messages[locale].telegram),
            LOGIN: escapeAttribute(messages[locale].login),
            CONTENT: localizedContent,
            PAGE_SCRIPT: '',
        });
        const outputPath = path.join(localeDirectory, page.artifact);
        await writeFile(outputPath, output);
    }
}

for (const locale of PUBLIC_LOCALES) await generateLocale(locale);
await copyFile(path.join(scriptDirectory, 'i18n.js'), path.join(outputDirectory, 'i18n.js'));
await copyFile(path.join(scriptDirectory, 'urls.js'), path.join(outputDirectory, 'urls.js'));
console.log(`Generated ${PUBLIC_PAGES.length * PUBLIC_LOCALES.length} public pages from ${path.relative(projectDirectory, templatePath)}`);
