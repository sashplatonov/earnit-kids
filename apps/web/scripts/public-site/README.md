# Public-site generator

<a name="top"></a>

The generator builds static public pages from the SvelteKit public-site source.
It does not build the Telegram workspace or the live demo; those remain normal
SvelteKit routes.

## Table of contents

- [🚀 Generate pages](#-generate-pages)
- [🧭 Inputs and output](#-inputs-and-output)
- [🧪 Verify a change](#-verify-a-change)

## 🚀 Generate pages

```bash
cd apps/web
npm run generate:public
```

Run `npm run build:static` when you need the complete static build.

[↑ Back to top](#top)

## 🧭 Inputs and output

The generator reads its templates and data from this directory and writes the
public build output used by the static adapter. Keep public URLs, titles, and
locale links in the generator data so the sitemap and pages agree.

✅ Treat generated output as build output. Change the source files, then run
the generator; do not hand-edit generated pages.

[↑ Back to top](#top)

## 🧪 Verify a change

```bash
cd apps/web
npm run test
npm run build:static
```

[↑ Back to top](#top)
