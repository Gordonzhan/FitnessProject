FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY maven-settings-cloud.xml ./maven-settings-cloud.xml
COPY pom.xml ./

# 依赖与源码分层：pom 和镜像配置不变时可复用依赖层；国内镜像规避 Maven Central 403。
RUN mvn -B -ntp -s ./maven-settings-cloud.xml \
  -Dmaven.wagon.http.retryHandler.count=3 \
  -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -s ./maven-settings-cloud.xml \
  -Dmaven.wagon.http.retryHandler.count=3 \
  -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
  && apt-get install --yes --no-install-recommends ca-certificates \
  && update-ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd --system fitness \
  && useradd --system --gid fitness --no-create-home fitness
WORKDIR /app
COPY --from=build /workspace/target/fitness-diary-0.0.1-SNAPSHOT.jar app.jar
# 保留 Temurin 的默认入口脚本：它会把系统 CA 同步到 JVM truststore；
# 非 root 运行时会在 /tmp 创建独立 truststore 并自动传给 JVM。
ENV USE_SYSTEM_CA_CERTS=1
USER fitness
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail --silent --show-error "http://127.0.0.1:${PORT:-8080}/api/health" > /dev/null || exit 1
ENTRYPOINT ["/__cacert_entrypoint.sh"]
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
