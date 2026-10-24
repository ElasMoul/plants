# Dev-delivery variant of frontend/Dockerfile: same production Angular build,
# served over plain http by nginx.dev-delivery.conf (no TLS certs, which are
# per-machine and absent from a clean `git archive`). Build context: frontend/;
# the conf comes from the "devdelivery" additional context (deploy/dev-delivery/).
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# PLA-50: stamped into version.json by the postbuild step (same arg as the backend).
ARG APP_REVISION=
ENV APP_REVISION=${APP_REVISION}
RUN npm run build:prod

FROM nginx:alpine
COPY --from=builder /app/dist/plantpal /usr/share/nginx/html
COPY --from=devdelivery nginx.dev-delivery.conf /etc/nginx/conf.d/default.conf
COPY nginx/routes.inc /etc/nginx/routes.inc
COPY nginx/host-redirect.conf.template /etc/nginx/templates/host-redirect.conf.template
ENV HOST_REDIRECT_FROM=redirect-disabled.invalid PREFERRED_HOST=localhost
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
