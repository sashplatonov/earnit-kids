FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Install Maven in the JDK25 image (apt-get available on Debian-based Temurin builds)
RUN apt-get update && apt-get install -y maven wget ca-certificates && rm -rf /var/lib/apt/lists/*

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src

# Skip tests and Checkstyle during image build to avoid CI/lint blockers in container build
# Keep the project Java release (25) as configured in pom.xml
RUN mvn -f backend/pom.xml -DskipTests -Dcheckstyle.skip=true package

FROM eclipse-temurin:25-jdk
WORKDIR /work

# Ensure runtime image has `wget` for the HEALTHCHECK command
RUN apt-get update && apt-get install -y wget ca-certificates && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/backend/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/backend/target/quarkus-app/*.jar ./
COPY --from=build /workspace/backend/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/backend/target/quarkus-app/quarkus/ ./quarkus/

ENV QUARKUS_HTTP_HOST=0.0.0.0
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 CMD wget -q --spider http://127.0.0.1:8080/q/health/ready || exit 1

CMD ["java", "-jar", "quarkus-run.jar"]
