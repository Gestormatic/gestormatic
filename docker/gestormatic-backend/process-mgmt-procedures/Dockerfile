FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copia todo el monorepo
COPY . .

# Da permisos al wrapper (asegúrate de que la ruta al archivo mvnw sea correcta)
RUN chmod +x ./apps/gestormatic-backend/process-mgmt-procedures/mvnw

# Usar -f para apuntar al pom.xml específico
RUN ./apps/gestormatic-backend/process-mgmt-procedures/mvnw \
    -f apps/gestormatic-backend/process-mgmt-procedures/pom.xml \
    clean package -DskipTests

# Etapa final
FROM eclipse-temurin:21-jre-alpine
# Asegúrate de que la ruta de origen coincida con donde Maven deja el .jar
COPY --from=build /app/apps/gestormatic-backend/process-mgmt-procedures/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
