FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*SNAPSHOT.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
