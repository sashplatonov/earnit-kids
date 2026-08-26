# Public site generator

The public marketing pages are static HTML artifacts generated from one shared template.

- Edit shared head, header, CTA, footer, or navigation markup in `template.html`.
- Edit page-specific content in `pages/*.html`.
- Run `npm run generate:public` to update `static/public/*.html`.
- `npm run build` runs the generator before building the web app.

Do not edit generated files in `static/public/*.html` directly; they are kept in the repository as the deployable static entry points.
