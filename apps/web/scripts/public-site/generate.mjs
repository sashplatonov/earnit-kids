import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, '../..');
const templatePath = path.join(scriptDirectory, 'template.html');
const i18nPath = path.join(scriptDirectory, 'i18n.js');
const pagesDirectory = path.join(scriptDirectory, 'pages');
const outputDirectory = path.join(projectDirectory, 'static/public');

const pages = [
    { file: 'index.html', title: 'Home', description: 'Tasks, coins and rewards for children in Telegram without the daily hassle.' },
    { file: 'how.html', title: 'How it works', description: 'How EarnIt Kids works for parents and children in Telegram.' },
    { file: 'tasks.html', title: 'Tasks', description: 'Tasks, groups, completion history and coin limits explained.' },
    { file: 'rewards.html', title: 'Rewards', description: 'How family rewards and spending coins work in EarnIt Kids.' },
    { file: 'parents.html', title: 'For parents', description: 'Approvals, children, limits, notifications and family settings.' },
    { file: 'faq.html', title: 'Questions', description: 'Answers to parents’ frequently asked questions about EarnIt Kids.' },
];

const template = await readFile(templatePath, 'utf8');
const navigationFor = (activeFile) => pages.map((page) => {
    const active = page.file === activeFile ? ' active' : '';
    const current = page.file === activeFile ? ' aria-current="page"' : '';
    const href = page.file === 'index.html' ? '/' : `/${page.file}`;
    return `<a class="tab${active}" href="${href}"${current}>${page.title}</a>`;
}).join('');

await mkdir(outputDirectory, { recursive: true });
await copyFile(i18nPath, path.join(outputDirectory, 'i18n.js'));

for (const page of pages) {
    const content = await readFile(path.join(pagesDirectory, page.file), 'utf8');
    const output = template
        .replace('{{TITLE}}', page.title)
        .replace('{{DESCRIPTION}}', page.description)
        .replace('{{PAGE_KEY}}', page.file.replace('.html', ''))
        .replace('{{NAV}}', navigationFor(page.file))
        .replace('{{CONTENT}}', content);
    await writeFile(path.join(outputDirectory, page.file), output);
}

console.log(`Generated ${pages.length} public pages from ${path.relative(projectDirectory, templatePath)}`);
