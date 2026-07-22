# syntax=docker/dockerfile:1.7

ARG MANDREL_BUILDER_IMAGE=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25
ARG JAVA_XMX=2500m
FROM ${MANDREL_BUILDER_IMAGE} AS build
WORKDIR /workspace

ARG JAVA_XMX
ENV JAVA_XMX=${JAVA_XMX}

COPY apps/backend/.mvn/ .mvn/
COPY apps/backend/mvnw ./mvnw
COPY apps/backend/pom.xml ./pom.xml

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
	sh ./mvnw -B -ntp -Dmaven.test.skip=true dependency:go-offline

COPY apps/backend/config ./config
COPY apps/backend/src ./src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
	sh ./mvnw -B -ntp -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dspotbugs.skip=true -Djacoco.skip=true -Dnative -Dquarkus.native.native-image-xmx=${JAVA_XMX} package

RUN mkdir -p /workspace/runtime-data/catalog

FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /work/

COPY --from=build /workspace/target/*-runner /work/application
COPY --from=build --chown=185:0 --chmod=0770 /workspace/runtime-data/ /work/data/

EXPOSE 8080

USER 185

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
