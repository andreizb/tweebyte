FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R spring:spring /app
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

