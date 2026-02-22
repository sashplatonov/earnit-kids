# Stage 1: Build
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Production
FROM node:20-alpine
WORKDIR /app

# Install only production dependencies
COPY package*.json ./
RUN npm ci --only=production && \
    apk add --no-cache postgresql-client

# Copy application files
# We copy src, scripts, views, public (which now includes dist), and data
COPY --from=build /app/src ./src
COPY --from=build /app/scripts ./scripts
COPY --from=build /app/views ./views
COPY --from=build /app/public ./public
COPY --from=build /app/data ./data
COPY --from=build /app/.env.example ./

EXPOSE 3000
ENV NODE_ENV=production

# Security: run as non-root user (optional but better)
# RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# USER appuser

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD wget -q --spider http://127.0.0.1:3000/api/health || exit 1

CMD ["npm", "start"]
