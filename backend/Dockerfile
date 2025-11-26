FROM container-registry.oracle.com/graalvm/jdk:21

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

EXPOSE 8080

CMD ["java", "-jar", "-Dspring.profiles.active=prod", "build/libs/backend-0.0.1-SNAPSHOT.jar"]
