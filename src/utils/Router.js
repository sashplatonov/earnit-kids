const { sendJSON, createRouteContext } = require('./controllerUtils');
const parseBody = require('../middleware/body-parser');

class Router {
    constructor() {
        this.routes = [];
        this.middlewares = [];
    }

    use(middleware) {
        this.middlewares.push(middleware);
    }

    add(method, path, handler) {
        this.routes.push({ method, path, handler });
    }

    get(path, handler) {
        this.add('GET', path, handler);
    }

    post(path, handler) {
        this.add('POST', path, handler);
    }

    put(path, handler) {
        this.add('PUT', path, handler);
    }

    delete(path, handler) {
        this.add('DELETE', path, handler);
    }

    // Match path handling dynamic segments e.g. /api/children/:id
    matchPath(routePath, requestPath) {
        const routeParts = routePath.split('/');
        const requestParts = requestPath.split('/');

        if (routeParts.length !== requestParts.length) return false;

        const params = {};
        for (let i = 0; i < routeParts.length; i++) {
            if (routeParts[i].startsWith(':')) {
                params[routeParts[i].slice(1)] = requestParts[i];
            } else if (routeParts[i] !== requestParts[i]) {
                return false;
            }
        }
        return { params };
    }

    async _executeRoute({ route, ctx, req, res }) {
        // Run middleware
        for (const mw of this.middlewares) {
            const mwResult = await mw(ctx, req, res);
            if (mwResult === false) return true; // Handled by middleware
        }

        await route.handler(ctx, req, res);
        return true;
    }

    async handle(req, res, initialContext = null) {
        const ctx = initialContext || createRouteContext(req);

        for (const route of this.routes) {
            if (route.method !== ctx.method && route.method !== 'ALL') continue;

            const match = this.matchPath(route.path, ctx.pathname);
            if (match) {
                ctx.params = match.params;
                return await this._executeRoute({ route, ctx, req, res });
            }
        }

        return false; // Not handled
    }
}

module.exports = Router;
