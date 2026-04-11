# Stage 1: Build는 GitHub Actions가 수행하므로 여기서는 실행 환경만 정의
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# JAR 복사 (docker-compose context: . 기준)
COPY backend/build/libs/*.jar app.jar

# JVM 옵션 및 타임존 설정 (Oracle Cloud ARM 24GB 최적화)
ENV JAVA_OPTS="-Xmx4g -Xms4g -XX:+UseG1GC -Duser.timezone=Asia/Seoul"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
