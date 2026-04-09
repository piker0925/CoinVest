# Run Stage (ARM64 호환을 위해 multi-arch 이미지 사용)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# GitHub Actions 워크플로우에서 빌드된 JAR를 복사 (context: . 기준)
COPY backend/build/libs/*.jar app.jar

# 운영 환경용 JVM 옵션 (Oracle Cloud ARM 24GB 최적화)
# -XX:+UseG1GC: ARM 환경에서 효율적인 GC
# -Xms4g -Xmx4g: 4GB Heap 할당
# -Duser.timezone=Asia/Seoul: 한국 시장 데이터 처리용 타임존 설정
ENV JAVA_OPTS="-XX:+UseG1GC -Xms4g -Xmx4g -XX:MaxGCPauseMillis=200 -Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
