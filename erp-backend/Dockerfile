ARG APP_VERSION=v1.133

FROM maven:3.9.9-eclipse-temurin-17 AS build

ARG APP_VERSION
WORKDIR /app

COPY pom.xml settings-aliyun.xml ./
RUN mvn -B -Dmaven.test.skip=true dependency:go-offline -s settings-aliyun.xml

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package -s settings-aliyun.xml

FROM eclipse-temurin:17-jre

ARG APP_VERSION
LABEL org.opencontainers.image.title="erp-backend" \
      org.opencontainers.image.version="${APP_VERSION}"
WORKDIR /app

ENV APP_UPLOADS_DIR=/app/uploads

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser && mkdir -p /app/uploads && chown -R appuser:appgroup /app

COPY --from=build /app/target/*.jar /app/app.jar

USER appuser

EXPOSE 8101

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
