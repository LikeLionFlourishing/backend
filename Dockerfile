# 저장소만 받은 환경에서도 이미지가 만들어지도록 빌드 단계에서 jar을 생성한다.
# 로컬·CI와 동일한 Gradle 버전을 쓰기 위해 래퍼로 빌드한다.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=UTC
RUN addgroup --system appgroup \
    && adduser --system --ingroup appgroup appuser
COPY --from=builder --chown=appuser:appgroup /workspace/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
# 무료 티어처럼 메모리 한도가 낮은 컨테이너에서 힙이 한도를 넘지 않게 제한한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "-jar", "app.jar"]
