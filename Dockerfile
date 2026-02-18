FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN apk add --no-cache postgresql-client
RUN npm install
COPY . .
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD wget -q --spider http://127.0.0.1:3000/ || exit 1
CMD ["npm", "start"]
