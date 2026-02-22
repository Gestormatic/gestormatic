FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copia todo el monorepo (contexto .)
COPY . .

Dar permisos de ejecución al wrapper
RUN chmod +x ./apps/gestormatic-backend/process-mgmt-login/mvnw

# CORRECCIÓN 2: Ejecutar el build (Asegúrate de que mvnw esté en la raíz)
RUN ./apps/gestormatic-backend/process-mgmt-login/mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Copia el jar generado a la raíz de la nueva imagen
COPY --from=build /app/apps/gestormatic-backend/process-mgmt-login/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
