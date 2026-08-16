# Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/audit-consumer.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
