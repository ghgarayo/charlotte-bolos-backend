# syntax=docker/dockerfile:1

##############################################################
# deps - baixa as dependencias uma unica vez (cache de layer)
##############################################################
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
# go-offline popula /root/.m2 dentro da imagem; o volume nomeado do compose
# e inicializado a partir dai, entao o primeiro start ja vem "quente".
RUN mvn -B dependency:go-offline

##############################################################
# dev - roda via spring-boot:run com DevTools (hot reload)
##############################################################
FROM deps AS dev
WORKDIR /app
# 8080 = API | 5005 = debug remoto (JDWP)
EXPOSE 8080 5005
CMD ["mvn", "-B", "spring-boot:run"]

##############################################################
# build - empacota o jar
##############################################################
FROM deps AS build
WORKDIR /app
COPY src ./src
RUN mvn -B clean package -DskipTests

##############################################################
# runtime - imagem enxuta para rodar o jar
##############################################################
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 -m spring
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
