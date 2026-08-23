import type { RequestHandler } from './$types';
import { proxyToBackend } from '$lib/server/proxy';

export const GET: RequestHandler = (event) => proxyToBackend(event);
