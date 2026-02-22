FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/apps/gestormatic-backend/process-mgmt-login/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
