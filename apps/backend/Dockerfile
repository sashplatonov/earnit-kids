ARG MANDREL_BUILDER_IMAGE=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25
ARG JAVA_XMX=2500m
FROM ${MANDREL_BUILDER_IMAGE} AS build
WORKDIR /workspace

ARG JAVA_XMX
ENV JAVA_XMX=${JAVA_XMX}

COPY backend/.mvn/ .mvn/
COPY backend/mvnw ./mvnw
COPY backend/pom.xml ./pom.xml
COPY backend/config ./config
COPY backend/src ./src

RUN sh ./mvnw -Dmaven.test.skip=true -Dnative -Dquarkus.native.native-image-xmx=${JAVA_XMX} package

FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/

COPY --from=build /workspace/target/*-runner /work/application

EXPOSE 8080

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
