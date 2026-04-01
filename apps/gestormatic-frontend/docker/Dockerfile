FROM node:20-alpine AS build
WORKDIR /app
COPY . .
RUN npm ci
RUN npx nx build gestormatic-frontend

FROM nginx:alpine
COPY --from=build /app/dist/apps/gestormatic-frontend/browser /usr/share/nginx/html
