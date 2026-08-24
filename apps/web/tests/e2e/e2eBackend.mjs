import { createServer } from 'node:http';

const server = createServer((request, response) => {
    const cookies = request.headers.cookie ?? '';
    const session = cookies.match(/(?:^|;\s*)e2e_session=([^;]+)/)?.[1];

    if (session === 'parent' && request.method === 'GET' && request.url?.startsWith('/api/admin/dashboard')) {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify({
            unavailableSections: [],
            overview: { totalFamilies: 4, activeFamilies: 3, totalChildren: 7, activeChildren: 5 },
            coinEconomy: { coins: { earned: 120, spent: 40, spendRate: 33 }, balances: { median: 20, zeroBalancePercent: 25 } },
            tasks: { taskMetrics: { taskCompletions: 18, approvalRate: 80, medianCompletionHours: 12 } },
            parentSignals: { parentBehaviorMetrics: { familiesUsingCatalogPercent: 75, familiesUsingCustomContentPercent: 50, medianApprovalDelayHours: 4, pendingRequestsCount: 2, familiesWithPendingRequests: 1 } },
            childSignals: { childBehaviorMetrics: { percentChildrenEarningNotSpending: 20 } },
            activation: { stages: [{ key: 'registered', label: 'Зарегистрированы', count: 4, percent: 100 }, { key: 'active', label: 'Активны', count: 3, percent: 75 }] },
            activity: { retentionMetrics: { newFamilies: 2, returningFamilies: 3, active7d: 3, active30d: 4 } },
            rewards: { metrics: { requestCount: 6, approvalRate: 80, medianDecisionHours: 5 } },
        }));
        return;
    }

    if (session === 'parent' && request.method === 'GET' && request.url?.startsWith('/api/admin/analytics/trends')) {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify({ points: [
            { date: '2026-08-17', activeFamilies: 2, coinsEarned: 20, coinsSpent: 8 },
            { date: '2026-08-18', activeFamilies: 3, coinsEarned: 30, coinsSpent: 12 },
        ] }));
        return;
    }

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
