/** @file View controller — renders public pages and static assets */
/* eslint max-lines: ["error", { max: 400, skipBlankLines: true, skipComments: true }] */
'use strict';

const fs = require('fs');
const path = require('path');
const { applyCommonTemplateData, buildSeoReplacements } = require('./seoTemplates');
const {
    getHtmlHeaders,
    normalizeStaticPath,
    resolvePublicFilePath,
    sendStaticFile,
    tryServeDistOverride,
    setServeNotFoundHandler
} = require('./staticUtils');
const { createLogger } = require('../utils/logger');

const logger = createLogger('viewController');
const fsPromises = fs.promises;

const FEATURE_PAGES = {
    tasks: {
        slug: 'tasks',
        title: 'EarnIt Kids - Добрые семейные задания',
        description: 'Простые задания для детей, понятные шаги для родителей и честные монетки за старание.',
        heading: 'Задания, которые хочется выполнять',
        subheading: 'Ребенок видит понятную цель, а родители спокойно следят за прогрессом.',
        bullets: [
            'Добавляйте домашние дела в пару кликов: убрать игрушки, почитать 10 минут, помочь на кухне.',
            'Ребенок отмечает выполнение, а родители подтверждают результат.',
            'За каждое выполненное дело начисляются монетки.'
        ],
        ctaText: 'Попробовать задания',
        ctaLink: '/login.html',
        image: '/img/feature-tasks.svg'
    },
    shop: {
        slug: 'shop',
        title: 'EarnIt Kids - Семейный магазин наград',
        description: 'Обменивайте монетки на радости: мультик, прогулка, настольная игра или маленький приз.',
        heading: 'Магазин радостей за монетки',
        subheading: 'Дети учатся копить и выбирать, родители сохраняют контроль и бюджет.',
        bullets: [
            'Создавайте награды: от 20 минут игры до семейного похода в парк.',
            'Задавайте лимиты, чтобы траты были разумными.',
            'Смотрите историю обменов и обсуждайте решения вместе с ребенком.'
        ],
        ctaText: 'Открыть магазин наград',
        ctaLink: '/login.html',
        image: '/img/feature-shop.svg'
    }
};

const LANDING_SEO = {
    title: 'EarnIt Kids - Семейные задания и награды',
    description: 'Помогаем детям 7+ выполнять полезные дела с интересом, а родителям легко поддерживать порядок без ссор.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        'name': 'EarnIt Kids',
        'description': 'Сервис для семейной мотивации детей и управления вознаграждениями.',
        'url': '/'
    }
};

const ABOUT_SEO = {
    title: 'EarnIt Kids - О проекте',
    description: 'Узнайте, как EarnIt Kids помогает семьям превращать рутину в понятную и добрую игру.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'AboutPage',
        'name': 'EarnIt Kids — О проекте'
    }
};

const FAQ_SEO = {
    title: 'EarnIt Kids - Частые вопросы',
    description: 'Короткие ответы для родителей и детей о заданиях, монетках и наградах в EarnIt Kids.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        'name': 'EarnIt Kids — Часто задаваемые вопросы'
    }
};

const FAQ_ITEMS = [
    {
        question: 'Как это работает?',
        answer: 'Родители дают задание, ребенок выполняет его и получает монетки. Потом монетки можно обменять на награды.'
    },
    {
        question: 'Нужно устанавливать приложение?',
        answer: 'Нет, все работает прямо в браузере на телефоне и компьютере.'
    },
    {
        question: 'Можно ограничить траты монет?',
        answer: 'Да, родители ставят лимиты и решают, какие награды доступны и как часто их можно брать.'
    }
];

function getNoStoreHtmlHeaders(req) {
    return {
        ...getHtmlHeaders(req),
        'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
        Pragma: 'no-cache',
        Expires: '0'
    };
}

function buildFaqMarkup(items) {
    return items.map(item => `
        <article class="faq-card">
            <details>
                <summary>
                    <span>${item.question}</span>
                    <span class="faq-card__icon">❓</span>
                </summary>
                <p>${item.answer}</p>
            </details>
        </article>`).join('');
}

function formatBullets(bullets) {
    return bullets.map(bullet => `<li>${bullet}</li>`).join('');
}

async function respondWithView({ viewName, req, res, seoData = {}, extraReplacements = {}, headers }) {
    const viewPath = path.join(__dirname, '../../views', viewName);
    try {
        const template = await fsPromises.readFile(viewPath, 'utf8');
        const context = {
            ...buildSeoReplacements(req, seoData),
            ...extraReplacements
        };
        const html = applyCommonTemplateData(template, context, req);
        res.writeHead(200, headers || getHtmlHeaders(req));
        res.end(html);
    } catch (err) {
        logger.error({ err: err.message, viewName }, 'Failed to render view');
        res.writeHead(500);
        res.end('Server Error');
    }
}

async function serveStatic(req, res) {
    const rawUrl = req.url.split('?')[0];
    const urlPath = normalizeStaticPath(rawUrl);
    if (tryServeDistOverride(rawUrl, req, res)) return;

    const resolvedPath = resolvePublicFilePath(urlPath);
    if (!resolvedPath) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    sendStaticFile({ filePath: resolvedPath, req, res });
}

async function serveSuperAdmin(req, res) {
    await respondWithView({
        viewName: 'super-admin.html',
        req,
        res,
        seoData: {},
        headers: getHtmlHeaders(req)
    });
}

async function serveLanding(req, res) {
    await respondWithView({
        viewName: 'landing.html',
        req,
        res,
        seoData: LANDING_SEO
    });
}

function getFeatureBySlug(slug) {
    if (!slug) return null;
    return FEATURE_PAGES[slug.replace(/\/+$/, '').toLowerCase()] || null;
}

async function serveFeaturePage(req, res, slug) {
    const feature = getFeatureBySlug(slug);
    if (!feature) {
        await serveNotFound(req, res);
        return;
    }

    const seoData = {
        title: feature.title,
        description: feature.description,
        schema: {
            '@context': 'https://schema.org',
            '@type': 'WebPage',
            'name': feature.title,
            'description': feature.description
        }
    };

    const extraReplacements = {
        '{{FEATURE_HEADING}}': feature.heading,
        '{{FEATURE_SUBTITLE}}': feature.subheading,
        '{{FEATURE_DESCRIPTION}}': feature.description,
        '{{FEATURE_IMAGE}}': feature.image,
        '{{FEATURE_PILL}}': feature.ctaText,
        '{{FEATURE_LINK}}': feature.ctaLink,
        '{{FEATURE_BULLETS}}': `<ul>${formatBullets(feature.bullets)}</ul>`
    };

    await respondWithView({ viewName: 'feature-page.html', req, res, seoData, extraReplacements });
}

async function serveAbout(req, res) {
    await respondWithView({ viewName: 'about.html', req, res, seoData: ABOUT_SEO });
}

async function serveFaq(req, res) {
    await respondWithView({
        viewName: 'faq.html',
        req,
        res,
        seoData: FAQ_SEO,
        extraReplacements: { '{{FAQ_ITEMS}}': buildFaqMarkup(FAQ_ITEMS) }
    });
}

async function serveResetPassword(req, res) {
    await respondWithView({
        viewName: 'reset-password.html',
        req,
        res,
        seoData: {
            title: 'Сброс пароля | EarnIt Kids',
            description: 'Восстановите доступ к аккаунту EarnIt Kids.',
            schema: {
                '@context': 'https://schema.org',
                '@type': 'WebPage',
                'name': 'Сброс пароля'
            }
        }
    });
}

async function serveVerify(req, res) {
    await respondWithView({
        viewName: 'verify.html',
        req,
        res,
        seoData: {
            title: 'Подтвердите вход | EarnIt Kids',
            description: 'Подтверждение входа в EarnIt Kids.',
            schema: {
                '@context': 'https://schema.org',
                '@type': 'WebPage',
                'name': 'Подтверждение входа'
            }
        }
    });
}

async function serveNotFound(req, res) {
    await respondWithView({
        viewName: '404.html',
        req,
        res,
        seoData: {
            title: 'Страница не найдена | EarnIt Kids',
            description: 'Запрашиваемая страница не найдена.',
            schema: {
                '@context': 'https://schema.org',
                '@type': 'WebPage',
                'name': '404 — EarnIt Kids'
            }
        }
    });
    return;
}

setServeNotFoundHandler((req, res) => {
    void serveNotFound(req, res);
});

module.exports = {
    serveStatic,
    serveSuperAdmin,
    serveLanding,
    serveFeaturePage,
    serveAbout,
    serveFaq,
    serveResetPassword,
    serveVerify,
    getNoStoreHtmlHeaders
};
