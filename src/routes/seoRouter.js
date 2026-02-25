/** @file SEO helpers for sitemap generation */
const { PUBLIC_BASE_URL } = require('../config');
const { listPosts } = require('../controllers/blogController');
const staticPaths = ['/', '/about', '/faq', '/features/tasks', '/features/shop', '/blog'];

function buildUrlEntry(loc, lastmod) {
    const lastModTag = lastmod ? `<lastmod>${lastmod}</lastmod>` : '';
    return `    <url>
        <loc>${PUBLIC_BASE_URL.replace(/\/$/, '')}${loc}</loc>
${lastModTag}
    </url>`;
}

async function handleSitemap(req, res) {
    const posts = await listPosts();
    const entries = staticPaths.map(path => buildUrlEntry(path, new Date().toISOString()));
    const blogEntries = posts.map(post => buildUrlEntry(`/blog/${post.slug}`, post.isoDate));
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${entries.concat(blogEntries).join('\n')}
</urlset>`;
    res.writeHead(200, { 'Content-Type': 'application/xml; charset=utf-8' });
    res.end(xml);
}

async function routeSeo(pathOnly, req, res) {
    if (pathOnly === '/sitemap.xml') {
        await handleSitemap(req, res);
        return true;
    }
    return false;
}

module.exports = {
    routeSeo,
    handleSitemap
};
