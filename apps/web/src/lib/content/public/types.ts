// EXPLAIN: Small typed contracts for public site marketing content.
// EXPLAIN: These are intentionally minimal — not a CMS schema engine.
// EXPLAIN: Page content modules use `satisfies` to validate shape at compile time.

export interface PublicCard {
    title: string;
    description: string;
}

export interface PublicStep {
    number: number;
    title: string;
    description: string;
}

export interface PublicFeature {
    text: string;
}

export interface PublicFaqItem {
    question: string;
    answer: string;
}

export interface PublicScreenshot {
    src: string;
    alt: string;
    captionTitle: string;
    captionText: string;
    width: number;
    height: number;
}

export interface PublicPageMeta {
    title: string;
    description: string;
}

export interface PublicCallout {
    title: string;
    text: string;
}

export interface PublicMetric {
    value: string;
    label: string;
}