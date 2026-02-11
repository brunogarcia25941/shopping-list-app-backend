package com.routes

import com.models.ShoppingItem // O teu modelo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.webSocket
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.util.*
import io.ktor.websocket.*


// Um conjunto (Set) seguro para guardar as sessões de quem está ligado
val sessions = Collections.synchronizedSet<WebSocketSession?>(LinkedHashSet())

fun Route.shoppingRoutes(db: CoroutineDatabase) {
    // ir buscar a "tabela" (collection) de itens da base de dados
    val collection = db.getCollection<ShoppingItem>()


    route("/shopping-list") {

        // 1. Rota para LER a lista (GET)
        get {
            val items = collection.find().toList() // Vai ao Mongo e traz tudo
            call.respond(items) // Envia para quem pediu (em JSON)
        }

        // 2. Rota para CRIAR um item (POST)
        post {
            // O servidor recebe o JSON e transforma em objeto ShoppingItem (ContentNegotiation)
            val item = call.receive<ShoppingItem>()

            // Guarda na base de dados
            collection.insertOne(item)


            // Transformar o item novo em JSON string para enviar pelo socket
            val jsonItem = kotlinx.serialization.json.Json.encodeToString(ShoppingItem.serializer(), item)

            sessions.forEach { session ->
                // Envia a mensagem para cada telemóvel ligado
                session.send(Frame.Text(jsonItem))
            }

            // Responde "OK, Criado" e devolve o item com o ID gerado
            call.respond(HttpStatusCode.Created, item)
        }

        // Rota WebSocket: ws://localhost:8080/shopping-list/updates
        webSocket("/updates") {
            sessions.add(this) // alguem ligou-se e portanto adiciona à lista.
            println("Nova conexão WebSocket! Total: ${sessions.size}")

            try {
                // Mantém a conexão aberta à espera de mensagens (loop infinito)
                for (frame in incoming) {
                    // Por enquanto não fazemos nada com o que recebemos aqui
                }
            } finally {
                // Alguém desligou-se ou a net caiu.
                sessions.remove(this)
                println("Conexão fechada. Total: ${sessions.size}")
            }
        }

    }
}