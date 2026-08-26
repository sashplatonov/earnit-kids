import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { messages } from './i18n.js';
import { PUBLIC_LOCALES, PUBLIC_PAGES, publicLanguageHref, resolvePublicOrigin } from './urls.js';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, '../..');
const templatePath = path.join(scriptDirectory, 'template.html');
const pagesDirectory = path.join(scriptDirectory, 'pages');
const outputDirectory = path.join(projectDirectory, 'static/public');
const template = await readFile(templatePath, 'utf8');
const publicOrigin = resolvePublicOrigin(process.env.APP_URL, {
    production: process.env.DEPLOYMENT_ENV === 'production',
});

const escapeAttribute = (value) => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const replaceAll = (source, values) => Object.entries(values).reduce((result, [key, value]) => result.replaceAll(`{{${key}}}`, String(value)), source);
const publicPath = (pathname, locale, origin) => new URL(publicLanguageHref(pathname, locale, origin)).pathname;

function navigationFor(activeKey, locale) {
    return PUBLIC_PAGES.map((page) => {
        const active = page.key === activeKey ? ' active' : '';
        const current = page.key === activeKey ? ' aria-current="page"' : '';
        const href = locale === 'ru' ? `/ru${page.englishPath}` : page.englishPath;
        return `<a class="tab${active}" href="${href}"${current}>${messages[locale].pageTitles[page.key]}</a>`;
    }).join('');
}

async function generateLocale(locale) {
    const localeDirectory = locale === 'ru' ? path.join(outputDirectory, 'ru') : outputDirectory;
    await mkdir(localeDirectory, { recursive: true });
    for (const page of PUBLIC_PAGES) {
        const fragment = await readFile(path.join(pagesDirectory, `${page.key}.html`), 'utf8');
        const localizedContent = page.key === 'demo'
            ? `<section class="demo-root" data-demo-root aria-labelledby="demo-title"><div class="container"><p class="demo-loading" data-demo-loading>${messages[locale].demo.required}</p><noscript>${messages[locale].demo.required}</noscript></div></section>`
            : fragment.replaceAll(`{{${page.key}.content}}`, messages[locale].pages[page.key].content);
        const englishPath = page.englishPath;
        const output = replaceAll(template, {
            LANG: locale,
            TITLE: escapeAttribute(messages[locale].pageTitles[page.key]),
            DESCRIPTION: escapeAttribute(messages[locale].descriptions[page.key]),
            CANONICAL: publicLanguageHref(englishPath, locale, publicOrigin),
            EN_URL: publicLanguageHref(englishPath, 'en', publicOrigin),
            RU_URL: publicLanguageHref(englishPath, 'ru', publicOrigin),
            DEMO_URL: publicPath('/demo.html', locale, publicOrigin),
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
            DEMO_LINK: escapeAttribute(messages[locale].demoLink),
            DEMO_LINK_LABEL: escapeAttribute(messages[locale].demoLinkLabel),
            CONTENT: localizedContent,
            PAGE_SCRIPT: page.key === 'demo' ? '<script type="module" src="/public/demo.js"></script>' : '',
        });
        const outputPath = path.join(localeDirectory, page.artifact);
        await writeFile(outputPath, output);
    }
}

for (const locale of PUBLIC_LOCALES) await generateLocale(locale);
await copyFile(path.join(scriptDirectory, 'i18n.js'), path.join(outputDirectory, 'i18n.js'));
await copyFile(path.join(scriptDirectory, 'urls.js'), path.join(outputDirectory, 'urls.js'));
await copyFile(path.join(scriptDirectory, 'demo-data.js'), path.join(outputDirectory, 'demo-data.js'));
await copyFile(path.join(scriptDirectory, 'demo.js'), path.join(outputDirectory, 'demo.js'));
console.log(`Generated ${PUBLIC_PAGES.length * PUBLIC_LOCALES.length} public pages from ${path.relative(projectDirectory, templatePath)}`);
