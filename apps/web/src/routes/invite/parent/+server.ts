import type { RequestHandler } from './$types';
import { proxyToBackend } from '$lib/server/proxy';

export const POST: RequestHandler = (event) => proxyToBackend(event);
