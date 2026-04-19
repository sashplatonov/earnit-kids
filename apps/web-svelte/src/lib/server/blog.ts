/**
 * Blog post loader — reads markdown files from apps/web-svelte/data/blog/
 * Mirrors the legacy blogController.js behaviour.
 */
import { readdir, readFile } from 'fs/promises';
import { join, basename, dirname } from 'path';
import { marked } from 'marked';
import fm from 'front-matter';
import { fileURLToPath } from 'url';

const BLOG_DIR = join(dirname(fileURLToPath(import.meta.url)), '../../../data/blog');

export interface BlogPost {
    slug: string;
    title: string;
    summary: string;
    date: Date;
    isoDate: string;
    html: string;
    tags: string[];
}

function normaliseDate(value: unknown): Date {
    if (!value) return new Date();
    const d = new Date(String(value));
    return isNaN(d.getTime()) ? new Date() : d;
}

export async function listPosts(): Promise<Pick<BlogPost, 'slug' | 'title' | 'summary' | 'isoDate' | 'tags'>[]> {
    let files: string[];
    try {
        files = await readdir(BLOG_DIR);
    } catch {
        return [];
    }
    const posts: BlogPost[] = [];
    for (const file of files) {
        if (!file.endsWith('.md')) continue;
        try {
            const raw = await readFile(join(BLOG_DIR, file), 'utf8');
            const parsed = fm<Record<string, unknown>>(raw);
            const slug = basename(file, '.md');
            const date = normaliseDate(parsed.attributes.date);
            posts.push({
                slug,
                title: String(parsed.attributes.title ?? slug),
                summary: String(parsed.attributes.description ?? parsed.body.split('\n')[0] ?? ''),
                date,
                isoDate: date.toISOString(),
                html: '',
                tags: Array.isArray(parsed.attributes.tags) ? (parsed.attributes.tags as string[]) : [],
            });
        } catch {
            // skip unreadable files
        }
    }
    posts.sort((a, b) => b.date.getTime() - a.date.getTime());
    return posts;
}

export async function loadPost(slug: string): Promise<BlogPost | null> {
    const filePath = join(BLOG_DIR, `${slug}.md`);
    let raw: string;
    try {
        raw = await readFile(filePath, 'utf8');
    } catch {
        return null;
    }
    const parsed = fm<Record<string, unknown>>(raw);
    const date = normaliseDate(parsed.attributes.date);
    const html = String(await marked.parse(parsed.body));
    return {
        slug,
        title: String(parsed.attributes.title ?? slug),
        summary: String(parsed.attributes.description ?? parsed.body.split('\n')[0] ?? ''),
        date,
        isoDate: date.toISOString(),
        html,
        tags: Array.isArray(parsed.attributes.tags) ? (parsed.attributes.tags as string[]) : [],
    };
}
