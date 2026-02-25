# Passo 1: Usar o Java 21 para compilar o código
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew

# MAGIA: Obriga o Gradle a usar no máximo 256MB de RAM para não "crashar" o Render
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs='-Xmx256m'"

# Compila a aplicação ignorando os testes
RUN ./gradlew buildFatJar -x test --no-daemon

# Passo 2: Criar a imagem final super leve
FROM eclipse-temurin:21-jre
EXPOSE 8080
WORKDIR /app

# Copia o ficheiro compilado
COPY --from=build /app/build/libs/*-all.jar app.jar

# Comando para arrancar o servidor
ENTRYPOINT ["java", "-jar", "app.jar"]