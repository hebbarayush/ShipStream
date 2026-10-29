# Build stage: Node, only to produce the static bundle. Not shipped.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite bakes VITE_API_URL into the bundle at build time (it's a static SPA, there's no server to read
# env vars from at runtime) - so it has to be a build arg, not a plain runtime ENV.
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Runtime stage: static files behind nginx, nothing from the Node toolchain.
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
