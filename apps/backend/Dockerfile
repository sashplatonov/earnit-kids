ARG MANDREL_BUILDER_IMAGE=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25
ARG JAVA_XMX=2500m
FROM ${MANDREL_BUILDER_IMAGE} AS build
WORKDIR /workspace

ARG JAVA_XMX
ENV JAVA_XMX=${JAVA_XMX}

COPY apps/backend/.mvn/ .mvn/
COPY apps/backend/mvnw ./mvnw
COPY apps/backend/pom.xml ./pom.xml
COPY apps/backend/config ./config
COPY apps/backend/src ./src

RUN sh ./mvnw -Dmaven.test.skip=true -Dnative -Dquarkus.native.native-image-xmx=${JAVA_XMX} package

# Install PostgreSQL 18 client tools from PGDG on UBI9 (glibc).
# Quarkus micro image is UBI9-based, so the binaries are compatible.
FROM registry.access.redhat.com/ubi9/ubi-minimal:latest AS pg-cli
RUN microdnf install -y wget ca-certificates \
    && wget -qO /tmp/pgdg.rpm https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm \
    && rpm -ivh /tmp/pgdg.rpm \
    && microdnf install -y postgresql18 \
    && microdnf clean all \
    && rm -rf /var/cache/yum /tmp/pgdg.rpm

FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/

# PostgreSQL 18 client tools for backup/restore
COPY --from=pg-cli /usr/pgsql-18/bin/pg_dump /usr/bin/
COPY --from=pg-cli /usr/pgsql-18/bin/pg_restore /usr/bin/
COPY --from=pg-cli /usr/pgsql-18/bin/psql /usr/bin/
COPY --from=pg-cli /usr/pgsql-18/lib/libpq.so.5* /usr/lib64/
# Runtime dependencies needed by libpq
COPY --from=pg-cli /usr/lib64/libldap.so.2* /usr/lib64/
COPY --from=pg-cli /usr/lib64/liblber.so.2* /usr/lib64/
COPY --from=pg-cli /usr/lib64/libsasl2.so.3* /usr/lib64/
COPY --from=pg-cli /usr/lib64/libz.so.1* /usr/lib64/
COPY --from=pg-cli /usr/lib64/libssl.so.3* /usr/lib64/
COPY --from=pg-cli /usr/lib64/libcrypto.so.3* /usr/lib64/

COPY --from=build /workspace/target/*-runner /work/application

EXPOSE 8080

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
