# Dev-delivery variant of frontend/Dockerfile: same production Angular build,
# served over plain http by nginx.dev-delivery.conf (no TLS certs, which are
# per-machine and absent from a clean `git archive`). Build context: frontend/;
# the conf comes from the "devdelivery" additional context (deploy/dev-delivery/).
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build:prod

FROM nginx:alpine
COPY --from=builder /app/dist/plantpal /usr/share/nginx/html
COPY --from=devdelivery nginx.dev-delivery.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
