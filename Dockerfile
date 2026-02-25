# Passo 1: Usar o Java 21 para compilar o código
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# -------------------------------------------------------------
# MAGIA DO CACHE: Copiamos APENAS os ficheiros do Gradle primeiro
# -------------------------------------------------------------
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Dá permissão ao Gradle
RUN chmod +x ./gradlew

# A nossa "Dieta" de RAM obrigatória
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs='-Xmx384m'"

# Mandamos o Gradle descarregar a internet toda (Bibliotecas) e guardar em Cache!
# O "|| true" serve para ele não falhar caso procure código que ainda não copiámos.
RUN ./gradlew dependencies --no-daemon || true

# -------------------------------------------------------------
# SÓ AGORA copiamos o teu código Kotlin (que é o que muda sempre)
# -------------------------------------------------------------
COPY src src

# Compila a aplicação ignorando os testes (Isto agora vai ser super rápido!)
RUN ./gradlew buildFatJar -x test --no-daemon

# Passo 2: Criar a imagem final super leve
FROM eclipse-temurin:21-jre
EXPOSE 8080
WORKDIR /app

# Copia o ficheiro compilado do Passo 1
COPY --from=build /app/build/libs/*-all.jar app.jar

# Comando para arrancar o servidor
ENTRYPOINT ["java", "-jar", "app.jar"]