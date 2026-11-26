# The site for a server: the Vite build, served by Caddy, which also passes /api to the backend.
# Built from the repo root (npm workspaces): docker compose -f docker-compose.prod.yml up --build

# Stage 1: build the static site
FROM node:24-alpine AS build
WORKDIR /repo
COPY package.json package-lock.json ./
COPY wok-and-roll-front/package.json wok-and-roll-front/
RUN npm ci --no-audit --no-fund
COPY wok-and-roll-front wok-and-roll-front
RUN npm run build

# Stage 2: Caddy with the site and the Caddyfile
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /repo/wok-and-roll-front/dist /srv
