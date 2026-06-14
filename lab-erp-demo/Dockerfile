ARG APP_VERSION=v1.154

FROM node:20-alpine AS build

ARG APP_VERSION

WORKDIR /app

COPY package*.json .npmrc ./
RUN npm config set fetch-retries 6 \
 && npm config set fetch-retry-factor 2 \
 && npm config set fetch-retry-mintimeout 10000 \
 && npm config set fetch-retry-maxtimeout 120000 \
 && npm config set fetch-timeout 300000 \
 && (npm ci --registry=https://registry.npmmirror.com/ \
   || npm ci --registry=https://mirrors.cloud.tencent.com/npm/ \
   || npm ci --registry=https://registry.npmjs.org/)

COPY . .
RUN npm run build

FROM nginx:1.27-alpine

ARG APP_VERSION
LABEL org.opencontainers.image.title="lab-erp-demo" \
      org.opencontainers.image.version="${APP_VERSION}"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
