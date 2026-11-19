# API 서버 이미지. Render가 저장소 루트를 빌드 컨텍스트로 이 파일을 빌드한다 (render.yaml)
# 로컬 빌드: docker build -f api/Dockerfile -t scanmesh-api .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY common common
COPY api api
# settings.gradle이 worker도 포함하므로 빌드 파일만 넣어 둔다. API 이미지에는 워커 코드가 필요 없다
COPY worker/build.gradle worker/build.gradle
RUN chmod +x gradlew && ./gradlew :api:bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 scanmesh
WORKDIR /app
COPY --from=build /src/api/build/libs/api-0.0.1-SNAPSHOT.jar app.jar
USER scanmesh
# Render 무료 플랜은 메모리가 512MB다. 힙을 컨테이너 메모리의 70%로 묶고, 메모리를 적게 쓰는 GC와 스레드 스택 크기를 쓴다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8190
ENTRYPOINT ["java", "-jar", "app.jar"]
