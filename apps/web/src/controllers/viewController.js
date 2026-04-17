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
        title: 'EarnIt Kids - Family-friendly tasks',
        description: 'Simple tasks for kids, clear steps for parents, and fair coins for effort.',
        heading: 'Tasks kids enjoy completing',
        subheading: 'Kids see a clear goal and parents can easily track progress.',
        bullets: [
            'Add chores in a couple of clicks: tidy toys, read for 10 minutes, help in the kitchen.',
            'The child marks completion and parents confirm the result.',
            'Coins are awarded for each completed task.'
        ],
        ctaText: 'Try the tasks',
        ctaLink: '/login.html',
        image: '/img/feature-tasks.svg'
    },
    shop: {
        slug: 'shop',
        title: 'EarnIt Kids - Family rewards shop',
        description: 'Exchange coins for treats: a movie, a walk, a board game, or a small prize.',
        heading: 'Rewards shop',
        subheading: 'Kids learn to save and choose, parents keep control and budget.',
        bullets: [
            'Create rewards: from 20 minutes of play to a family trip to the park.',
            'Set limits so spending stays reasonable.',
            'View exchange history and discuss choices with your child.'
        ],
        ctaText: 'Open rewards shop',
        ctaLink: '/login.html',
        image: '/img/feature-shop.svg'
    }
};

const LANDING_SEO = {
    title: 'EarnIt Kids - Family tasks and rewards',
    description: 'Helps children 7+ complete helpful tasks with engagement, and makes it easy for parents to maintain order without conflict.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        'name': 'EarnIt Kids',
        'description': 'A service for family motivation and reward management.',
        'url': '/'
    }
};

const ABOUT_SEO = {
    title: 'EarnIt Kids - About',
    description: 'Learn how EarnIt Kids helps families turn routine into a clear and positive experience.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'AboutPage',
        'name': 'EarnIt Kids — About'
    }
};

const FAQ_SEO = {
    title: 'EarnIt Kids - Frequently Asked Questions',
    description: 'Short answers for parents and kids about tasks, coins, and rewards in EarnIt Kids.',
    schema: {
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        'name': 'EarnIt Kids — FAQ'
    }
};

const FAQ_ITEMS = [
    {
        question: 'How does it work?',
        answer: 'Parents assign a task, the child completes it and earns coins. Coins can then be exchanged for rewards.'
    },
    {
        question: 'Do we need to install an app?',
        answer: 'No, it works directly in the browser on phone and computer.'
    },
    {
        question: 'Can coin spending be limited?',
        answer: 'Yes, parents set limits and decide which rewards are available and how often they can be claimed.'
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
            title: 'Password reset | EarnIt Kids',
            description: 'Restore access to your EarnIt Kids account.',
            schema: {
                '@context': 'https://schema.org',
                '@type': 'WebPage',
                'name': 'Password reset'
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
            title: 'Verify sign-in | EarnIt Kids',
            description: 'Sign-in verification for EarnIt Kids.',
            schema: {
                '@context': 'https://schema.org',
                '@type': 'WebPage',
                'name': 'Sign-in verification'
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
            title: 'Page not found | EarnIt Kids',
            description: 'The requested page could not be found.',
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
