/** @file Blog controller serving markdown articles */
const fs = require('fs');
const path = require('path');
const marked = require('marked');
const frontMatter = require('front-matter');
const { applyCommonTemplateData, buildSeoReplacements } = require('./seoTemplates');
const { getHtmlHeaders } = require('./staticUtils');
const { PUBLIC_BASE_URL } = require('../config');
const BLOG_DIR = path.join(__dirname, '../../data/blog');
const VIEW_DIR = path.join(__dirname, '../../views');
const CACHE_TTL_MS = 2 * 60 * 1000;

let blogCache = {
    posts: null,
    fetchedAt: 0
};

async function ensureBlogDirectory() {
    try {
        await fs.promises.access(BLOG_DIR);
    } catch (_) {
        await fs.promises.mkdir(BLOG_DIR, { recursive: true });
    }
}

function normalizeSourceDate(value) {
    if (!value) return new Date();
    const result = new Date(value);
    return isNaN(result.getTime()) ? new Date() : result;
}

async function readPosts() {
    if (blogCache.posts && Date.now() - blogCache.fetchedAt < CACHE_TTL_MS) {
        return blogCache.posts;
    }
    await ensureBlogDirectory();
    const files = await fs.promises.readdir(BLOG_DIR);
    const posts = [];
    for (const file of files) {
        if (!file.endsWith('.md')) continue;
        const filePath = path.join(BLOG_DIR, file);
        const raw = await fs.promises.readFile(filePath, 'utf8');
        const parsed = frontMatter(raw);
        const slug = path.basename(file, '.md');
        const date = normalizeSourceDate(parsed.attributes.date);
        const htmlBody = marked.parse(parsed.body);
        posts.push({
            slug,
            title: parsed.attributes.title || slug,
            summary: parsed.attributes.description || parsed.body.split('\n')[0] || '',
            date,
            isoDate: date.toISOString(),
            body: parsed.body,
            html: htmlBody,
            tags: parsed.attributes.tags || []
        });
    }
    posts.sort((a, b) => b.date - a.date);
    blogCache = { posts, fetchedAt: Date.now() };
    return posts;
}

async function listPosts() {
    const posts = await readPosts();
    return posts.map(({ slug, isoDate }) => ({ slug, isoDate }));
}

function buildArticleMeta(post) {
    return {
        title: post.title,
        description: post.summary,
        schema: {
            '@context': 'https://schema.org',
            '@type': 'BlogPosting',
            'headline': post.title,
            'datePublished': post.isoDate,
            'url': `${PUBLIC_BASE_URL}/blog/${post.slug}`
        }
    };
}

async function renderView({ viewName, replacements = {}, headers, req, res }) {
    const viewPath = path.join(VIEW_DIR, viewName);
    const template = await fs.promises.readFile(viewPath, 'utf8');
    const html = applyCommonTemplateData(template, replacements, req);
    res.writeHead(200, headers || getHtmlHeaders(req));
    res.end(html);
}

async function serveBlogIndex(req, res) {
    const posts = await readPosts();
    const seoData = {
        title: 'EarnIt Kids - Блог для родителей и детей',
        description: 'Простые советы о семейных заданиях, монетках и добрых наградах для детей 7+.',
        schema: {
            '@context': 'https://schema.org',
            '@type': 'Blog',
            'name': 'EarnIt Kids — блог'
        }
    };
    const listMarkup = posts.map(post => `
        <article class="blog-card">
            <h2><a href="/blog/${post.slug}">${post.title}</a></h2>
            <small>${new Date(post.date).toLocaleDateString('ru-RU')} · ${post.tags.join(', ') || 'семейные советы'}</small>
            <p>${post.summary}</p>
        </article>
    `).join('');
    const replacements = {
        ...buildSeoReplacements(req, seoData),
        '{{BLOG_LIST}}': listMarkup
    };
    await renderView({
        viewName: 'blog-index.html',
        replacements,
        headers: getHtmlHeaders(req),
        req,
        res
    });
}

async function serveArticle(req, res, slug) {
    const posts = await readPosts();
    const post = posts.find(p => p.slug === slug);
    if (!post) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Статья не найдена');
        return;
    }
    const seoData = buildArticleMeta(post);
    const replacements = {
        ...buildSeoReplacements(req, seoData),
        '{{ARTICLE_TITLE}}': post.title,
        '{{ARTICLE_DATE}}': new Date(post.date).toLocaleDateString('ru-RU'),
        '{{ARTICLE_BODY}}': post.html
    };
    await renderView({
        viewName: 'article.html',
        replacements,
        headers: getHtmlHeaders(req),
        req,
        res
    });
}

module.exports = {
    serveBlogIndex,
    serveArticle,
    listPosts
};
