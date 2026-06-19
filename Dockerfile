# Dockerfile de despliegue (Render): backend Spring Boot + PWA en una sola imagen.
# (Para desarrollo local con docker-compose se usa backend/Dockerfile + nginx.)

# --- Build del backend ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B -DskipTests package

# --- Imagen de ejecución ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/betsim-backend-1.0.0.jar app.jar
# La PWA viaja dentro de la imagen y la sirve el propio backend.
COPY frontend /app/frontend
ENV BETSIM_FRONTEND_PATH=file:/app/frontend/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
