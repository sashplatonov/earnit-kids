/**
 * Blog post loader — reads markdown files from apps/web/data/blog/
 * Mirrors the legacy blogController.js behaviour.
 */
import { readdir, readFile } from 'fs/promises';
import { join, basename, dirname } from 'path';
import { marked } from 'marked';
import fm from 'front-matter';
import { fileURLToPath } from 'url';
import type { Locale } from '$lib/i18n';

const BLOG_ROOT_DIR = join(dirname(fileURLToPath(import.meta.url)), '../../../data/blog');

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

function getCandidateDirectories(locale: Locale): string[] {
    if (locale === 'ru') {
        return [join(BLOG_ROOT_DIR, 'ru'), BLOG_ROOT_DIR, join(BLOG_ROOT_DIR, 'en')];
    }

    return [join(BLOG_ROOT_DIR, 'en'), BLOG_ROOT_DIR];
}

async function readDirectoryFiles(directory: string): Promise<string[]> {
    try {
        return await readdir(directory);
    } catch {
        return [];
    }
}

export async function listPosts(locale: Locale): Promise<Pick<BlogPost, 'slug' | 'title' | 'summary' | 'isoDate' | 'tags'>[]> {
    const posts: BlogPost[] = [];
    const seen = new Set<string>();

    for (const directory of getCandidateDirectories(locale)) {
        const files = await readDirectoryFiles(directory);

        for (const file of files) {
            if (!file.endsWith('.md')) continue;

            const slug = basename(file, '.md');
            if (seen.has(slug)) {
                continue;
            }

            try {
                const raw = await readFile(join(directory, file), 'utf8');
            const parsed = fm<Record<string, unknown>>(raw);
            const date = normaliseDate(parsed.attributes.date);
                seen.add(slug);
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
                continue;
            }
        }
    }

    posts.sort((a, b) => b.date.getTime() - a.date.getTime());
    return posts;
}

export async function loadPost(locale: Locale, slug: string): Promise<BlogPost | null> {
    for (const directory of getCandidateDirectories(locale)) {
        const filePath = join(directory, `${slug}.md`);
        let raw: string;

        try {
            raw = await readFile(filePath, 'utf8');
        } catch {
            continue;
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

    return null;
}
