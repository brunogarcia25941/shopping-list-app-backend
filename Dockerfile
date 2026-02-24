# Passo 1: Usar o Java 21 para compilar o código
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
# Dá permissão de execução ao ficheiro gradlew
RUN chmod +x ./gradlew
# Compila a aplicação
RUN ./gradlew buildFatJar --no-daemon

# Passo 2: Criar a imagem final super leve só para correr a app
FROM eclipse-temurin:21-jre
EXPOSE 8080
WORKDIR /app
# Copia o ficheiro compilado do Passo 1
COPY --from=build /app/build/libs/*-all.jar app.jar

# Comando para arrancar o servidor
ENTRYPOINT ["java", "-jar", "app.jar"]