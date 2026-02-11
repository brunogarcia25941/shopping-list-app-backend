package com


import com.routes.shoppingRoutes
import io.ktor.server.application.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Configurar a ligação
    val connectionString = System.getenv("MONGO_URI")
        ?: throw IllegalStateException("ERRO: A variável de ambiente 'MONGO_URI' não foi encontrada! Configura-a no IntelliJ.")

    val client = KMongo.createClient(connectionString).coroutine
    val database = client.getDatabase("shopping_list_db") // Nome da base de dados (pode ser qualquer um)

    // Teste rápido para ver se ligou (opcional, vai aparecer nos logs)
    println("Ligado à base de dados: ${database.name}")

    configureSerialization()
    configureMonitoring()
    configureSockets()
    routing {
        shoppingRoutes(database)
        // base de dados para as rotas

        get("/") {
            call.respondText("Servidor de Compras a funcionar")
        }
    }
}
