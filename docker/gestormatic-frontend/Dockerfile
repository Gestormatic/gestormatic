FROM node:20-alpine AS build
WORKDIR /app
COPY . .
RUN npm ci
RUN npx nx build frontend

FROM nginx:alpine
COPY --from=build /app/dist/apps/frontend/browser /usr/share/nginx/html
