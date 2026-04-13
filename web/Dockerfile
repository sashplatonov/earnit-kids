FROM node:20-alpine AS build
WORKDIR /workspace/web

COPY web/package*.json ./
RUN npm ci

COPY web/public ./public
COPY web/views ./views
COPY web/data ./data
COPY web/src ./src
COPY web/scripts ./scripts

RUN npm run build --ignore-scripts

FROM node:20-alpine
WORKDIR /workspace/web

COPY web/package*.json ./
RUN npm ci --omit=dev

COPY --from=build /workspace/web/public ./public
COPY --from=build /workspace/web/views ./views
COPY --from=build /workspace/web/data ./data
COPY --from=build /workspace/web/src ./src

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD wget -q --spider http://127.0.0.1:3000/healthz || exit 1

CMD ["node", "src/server.js"]