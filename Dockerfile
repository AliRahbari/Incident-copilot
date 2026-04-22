FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY incident-copilot-core/pom.xml incident-copilot-core/pom.xml
COPY incident-copilot-app/pom.xml incident-copilot-app/pom.xml
RUN mvn -B -q dependency:go-offline
COPY incident-copilot-core/src incident-copilot-core/src
COPY incident-copilot-app/src incident-copilot-app/src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/incident-copilot-app/target/incident-copilot-app-*.jar app.jar
EXPOSE 8585
ENTRYPOINT ["java", "-jar", "app.jar"]
