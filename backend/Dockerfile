FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src

RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /work

COPY --from=build /workspace/backend/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/backend/target/quarkus-app/*.jar ./
COPY --from=build /workspace/backend/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/backend/target/quarkus-app/quarkus/ ./quarkus/

ENV QUARKUS_HTTP_HOST=0.0.0.0
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 CMD wget -q --spider http://127.0.0.1:8080/q/health/ready || exit 1

CMD ["java", "-jar", "quarkus-run.jar"]
