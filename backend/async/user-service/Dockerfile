FROM eclipse-temurin:21-jdk-jammy@sha256:801b7e1a9c4befaf82bf9a2a58025ef43a7694bbc84779187ad0524d84742772 AS build
WORKDIR /workspace
COPY .mvn ./.mvn
COPY mvnw ./
COPY pom.xml ./
COPY lombok.config ./
COPY src ./src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy@sha256:199aebeb3adcde4910695cdebfe782ada38dadb6cc8013159b58d3724451befd
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R spring:spring /app
USER spring
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

