# Usa una imagen base con Java 17 para compilar
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compila el proyecto y salta las pruebas para que sea más rápido
RUN mvn clean package -DskipTests

# Usa una imagen más ligera para ejecutar la aplicación
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copia el archivo .jar generado en el paso anterior
COPY --from=build /app/target/*.jar app.jar
# Expone el puerto (por defecto 8081)
EXPOSE 8081
# Ejecuta la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
