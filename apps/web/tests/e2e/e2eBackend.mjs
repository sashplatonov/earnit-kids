import { createServer } from 'node:http';

const server = createServer((request, response) => {
    const cookies = request.headers.cookie ?? '';
    const session = cookies.match(/(?:^|;\s*)e2e_session=([^;]+)/)?.[1];

    if (request.url === '/api/page-data/session' && request.method === 'GET') {
        const payload = session === 'parent'
            ? { authenticated: true, role: 'parent', familyId: 'e2e-family', familyName: 'E2E Family' }
            : session === 'child'
                ? { authenticated: true, role: 'child', familyId: 'e2e-family', childName: 'E2E Child' }
                : { authenticated: false };
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify(payload));
        return;
    }

    if (request.url === '/api/logout' && request.method === 'POST') {
        const csrfToken = request.headers['x-csrf-token'];
        if (session && csrfToken === 'e2e-csrf') {
            response.writeHead(204);
            response.end();
            return;
        }
        response.writeHead(403);
        response.end();
        return;
    }

    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end('{}');
});

server.listen(18080, '127.0.0.1');
for (const signal of ['SIGINT', 'SIGTERM']) {
    process.once(signal, () => server.close(() => process.exit(0)));
}
