ARG MANDREL_BUILDER_IMAGE=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25
FROM ${MANDREL_BUILDER_IMAGE} AS build
WORKDIR /workspace

COPY backend/.mvn/ .mvn/
COPY backend/mvnw ./mvnw
COPY backend/pom.xml ./pom.xml
COPY backend/config ./config
COPY backend/src ./src

RUN sh ./mvnw -DskipTests -Dnative package

FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/

COPY --from=build /workspace/target/*-runner /work/application

EXPOSE 8080

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
