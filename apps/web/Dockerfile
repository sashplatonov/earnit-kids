FROM node:20-alpine AS build
WORKDIR /workspace/web

ARG VITE_NEW_RELIC_BROWSER_ENABLED
ARG VITE_NEW_RELIC_BROWSER_INFO
ARG VITE_NEW_RELIC_BROWSER_INIT
ARG VITE_NEW_RELIC_BROWSER_LOADER_CONFIG

COPY apps/web/package*.json ./
RUN npm ci

COPY apps/web ./
RUN npm run build

FROM node:20-alpine
WORKDIR /workspace/web

COPY apps/web/package*.json ./
RUN npm ci --omit=dev --ignore-scripts

COPY --from=build /workspace/web/build ./build
COPY --from=build /workspace/web/data ./data
COPY --from=build /workspace/web/scripts ./scripts

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD wget -q --spider http://127.0.0.1:3000/healthz || exit 1

CMD ["npm", "run", "start"]
