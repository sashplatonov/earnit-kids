import type { RequestHandler } from './$types';
import { proxyToBackend } from '$lib/server/proxy';

const handleProxy: RequestHandler = (event) => proxyToBackend(event);

export const GET = handleProxy;
export const POST = handleProxy;
export const PUT = handleProxy;
export const PATCH = handleProxy;
export const DELETE = handleProxy;
export const OPTIONS = handleProxy;
export const HEAD = handleProxy;
