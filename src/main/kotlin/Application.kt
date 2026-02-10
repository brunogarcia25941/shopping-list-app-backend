package com


import io.ktor.server.application.*
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Configurar a ligação
    val connectionString = "mongodb+srv://user:password@cluster0..."

    val client = KMongo.createClient(connectionString).coroutine
    val database = client.getDatabase("shopping_list_db") // Nome da base de dados (pode ser qualquer um)

    // Teste rápido para ver se ligou (opcional, vai aparecer nos logs)
    println("Ligado à base de dados: ${database.name}")
    configureSerialization()
    configureMonitoring()
    configureSockets()
    configureRouting()
}
