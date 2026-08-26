import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, '../..');
const templatePath = path.join(scriptDirectory, 'template.html');
const pagesDirectory = path.join(scriptDirectory, 'pages');
const outputDirectory = path.join(projectDirectory, 'static/public');

const pages = [
    { file: 'index.html', title: 'Главная', description: 'Задания, монеты и награды для детей в Telegram без лишней рутины.' },
    { file: 'how.html', title: 'Как работает', description: 'Как EarnIt Kids работает для родителя и ребёнка в Telegram.' },
    { file: 'tasks.html', title: 'Задания', description: 'Как устроены задания, группы, история выполнения и лимиты монет.' },
    { file: 'rewards.html', title: 'Награды', description: 'Как устроены семейные награды и трата монет в EarnIt Kids.' },
    { file: 'parents.html', title: 'Для родителей', description: 'Подтверждения, дети, лимиты, уведомления и семейные настройки EarnIt Kids.' },
    { file: 'faq.html', title: 'Вопросы', description: 'Ответы на частые вопросы родителей об EarnIt Kids.' },
];

const template = await readFile(templatePath, 'utf8');
const navigationFor = (activeFile) => pages.map((page) => {
    const active = page.file === activeFile ? ' active' : '';
    const current = page.file === activeFile ? ' aria-current="page"' : '';
    return `<a class="tab${active}" href="${page.file}"${current}>${page.title}</a>`;
}).join('');

await mkdir(outputDirectory, { recursive: true });

for (const page of pages) {
    const content = await readFile(path.join(pagesDirectory, page.file), 'utf8');
    const output = template
        .replace('{{TITLE}}', page.title)
        .replace('{{DESCRIPTION}}', page.description)
        .replace('{{NAV}}', navigationFor(page.file))
        .replace('{{CONTENT}}', content);
    await writeFile(path.join(outputDirectory, page.file), output);
}

console.log(`Generated ${pages.length} public pages from ${path.relative(projectDirectory, templatePath)}`);
