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


            sessions.forEach { session ->
                session.send(Frame.Text("REFRESH")) // Envia apenas um aviso
            }

            // Responde "OK, Criado" e devolve o item com o ID gerado
            call.respond(HttpStatusCode.Created, item)
        }

        // 3. Rota para APAGAR um item (DELETE)
        delete("/{id}") {
            // Vai buscar o ID ao URL (ex: /shopping-list/12345)
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID em falta")
                return@delete
            }

            // Apaga da base de dados (MongoDB)
            val apagou = collection.deleteOneById(id).wasAcknowledged()

            if (apagou) {
                // Se apagou com sucesso, avisa toda a gente para atualizar a lista
                sessions.forEach { session ->
                    session.send(Frame.Text("REFRESH"))
                }
                call.respond(HttpStatusCode.OK, "Item apagado")
            } else {
                call.respond(HttpStatusCode.NotFound, "Item não encontrado")
            }
        }

        // 4. Rota para ATUALIZAR um item (PUT)
        put("/{id}") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "ID em falta")
                return@put
            }

            // Recebe a versão nova do item enviada pela app
            val updatedItem = call.receive<ShoppingItem>()

            // Substitui o item antigo por este novo na base de dados
            val atualizou = collection.updateOneById(id, updatedItem).wasAcknowledged()

            if (atualizou) {
                // Se correu bem, avisa toda a gente para atualizar o ecrã
                sessions.forEach { session ->
                    session.send(Frame.Text("REFRESH"))
                }
                call.respond(HttpStatusCode.OK, updatedItem)
            } else {
                call.respond(HttpStatusCode.NotFound, "Item não encontrado")
            }
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