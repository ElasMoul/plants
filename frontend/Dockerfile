# Stage 1 — build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build:prod

# Stage 2 — serve with Nginx
FROM nginx:alpine
COPY --from=builder /app/dist/plantpal /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx/routes.inc /etc/nginx/routes.inc
# Rendered by the nginx image's envsubst; the defaults leave the host redirect inert.
COPY nginx/host-redirect.conf.template /etc/nginx/templates/host-redirect.conf.template
ENV HOST_REDIRECT_FROM=redirect-disabled.invalid PREFERRED_HOST=localhost
COPY nginx/certs /etc/nginx/certs
EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
