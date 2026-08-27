# syntax=docker/dockerfile:1.7

FROM node:24.19.0-alpine3.24 AS build
WORKDIR /workspace/web

ARG APP_URL
ARG DEPLOYMENT_ENV=production

COPY apps/web/package*.json ./
RUN --mount=type=cache,target=/root/.npm,sharing=locked npm ci

COPY apps/web ./
RUN APP_URL="$APP_URL" DEPLOYMENT_ENV="$DEPLOYMENT_ENV" npm run build && npm prune --omit=dev --ignore-scripts

FROM node:24.19.0-alpine3.24
WORKDIR /workspace/web

RUN apk add --no-cache wget ca-certificates

COPY --from=build /workspace/web/package*.json ./
COPY --from=build /workspace/web/node_modules ./node_modules
COPY --from=build /workspace/web/build ./build
COPY --from=build /workspace/web/scripts ./scripts

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

USER node

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD wget -q --spider http://127.0.0.1:3000/healthz || exit 1

CMD ["node", "scripts/preview.mjs"]
