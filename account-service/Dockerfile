# Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY build/libs/account-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
