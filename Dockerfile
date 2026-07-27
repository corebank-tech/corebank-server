# 1. 실행 환경을 위한 JDK 21 이미지 (Corretto 21)
FROM amazoncorretto:21-alpine

# 2. 작업 디렉터리 생성
WORKDIR /app

# 3. CI 단계에서 빌드되어 아티팩트로 다운로드된 JAR 파일 복사
COPY build/libs/*SNAPSHOT.jar app.jar

# 4. 서버 포트 노출
EXPOSE 8080

# 5. 스프링 부트 애플리케이션 실행
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
