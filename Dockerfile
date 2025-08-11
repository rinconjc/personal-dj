FROM openjdk:21
WORKDIR /app
COPY public ./public
COPY public/js/release ./public/js
COPY target/ai-dj-backend.jar ./
ENTRYPOINT [ "java", "-jar", "ai-dj-backend.jar" ]
