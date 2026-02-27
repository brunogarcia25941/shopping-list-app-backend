package com


import io.ktor.http.HttpStatusCode
import routes.shoppingRoutes
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Configurar a ligação
    val connectionString = System.getenv("MONGO_URI")
        ?: throw IllegalStateException("ERRO: A variável de ambiente 'MONGO_URI' não foi encontrada! Configura-a no IntelliJ.")

    val client = KMongo.createClient(connectionString).coroutine
    val database = client.getDatabase("shopping_list_db")

    println("Ligado à base de dados: ${database.name}")


    // ABRIR AS PORTAS DO SERVIDOR (CORS)

    install(CORS) {
        anyHost() // Permite que qualquer site aceda (incluindo o localhost:8080)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
    }

    configureSerialization()
    configureMonitoring()
    configureSockets()

    routing {
        shoppingRoutes(database)

        get("/") {
            call.respondText("Servidor de Compras a funcionar")
        }

        head("/") {
            call.respond(HttpStatusCode.OK)
        }
    }
}
