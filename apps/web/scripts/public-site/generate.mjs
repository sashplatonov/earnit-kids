import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { messages } from './i18n.js';
import { PUBLIC_LOCALES, PUBLIC_PAGES } from './urls.js';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, '../..');
const templatePath = path.join(scriptDirectory, 'template.html');
const pagesDirectory = path.join(scriptDirectory, 'pages');
const outputDirectory = path.join(projectDirectory, 'static/public');
const template = await readFile(templatePath, 'utf8');

const escapeAttribute = (value) => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const replaceAll = (source, values) => Object.entries(values).reduce((result, [key, value]) => result.replaceAll(`{{${key}}}`, String(value)), source);

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
        const localizedContent = fragment.replaceAll(`{{${page.key}.content}}`, messages[locale].pages[page.key].content);
        const englishPath = page.englishPath;
        const localizedPath = locale === 'ru' ? `/ru${englishPath}` : englishPath;
        const output = replaceAll(template, {
            LANG: locale,
            TITLE: escapeAttribute(messages[locale].pageTitles[page.key]),
            DESCRIPTION: escapeAttribute(messages[locale].descriptions[page.key]),
            CANONICAL: localizedPath,
            EN_URL: englishPath,
            RU_URL: `/ru${englishPath}`,
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
        });
        const outputPath = path.join(localeDirectory, page.artifact);
        await writeFile(outputPath, output);
    }
}

for (const locale of PUBLIC_LOCALES) await generateLocale(locale);
await copyFile(path.join(scriptDirectory, 'i18n.js'), path.join(outputDirectory, 'i18n.js'));
await copyFile(path.join(scriptDirectory, 'urls.js'), path.join(outputDirectory, 'urls.js'));
console.log(`Generated ${PUBLIC_PAGES.length * PUBLIC_LOCALES.length} public pages from ${path.relative(projectDirectory, templatePath)}`);
