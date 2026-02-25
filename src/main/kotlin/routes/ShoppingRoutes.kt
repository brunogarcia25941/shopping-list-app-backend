package routes

import com.models.QuickSuggestion
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import models.ShoppingItem
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import java.util.concurrent.ConcurrentHashMap
import org.litote.kmongo.and
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


// NOVA LÓGICA: Em vez de uma lista única de sessões, temos um "Dicionário" (Mapa)
// que liga o "Código da Família" a uma "Lista de Telemóveis (Sessões)" ligados a ela.
val familyRooms = ConcurrentHashMap<String, MutableList<DefaultWebSocketServerSession>>()

@Serializable
data class WsMessage(
    val action: String, // "ADD", "UPDATE", "DELETE", "DELETE_BOUGHT"
    val item: ShoppingItem? = null,
    val itemId: String? = null
)

fun Route.shoppingRoutes(db: CoroutineDatabase) {
    val collection = db.getCollection<ShoppingItem>("shopping_items")

    // Todas as rotas agora começam com /shopping-list/{familyCode}
    route("/shopping-list/{familyCode}") {

        // 1. LER a lista daquela família específica (GET)
        get {
            val familyCode = call.parameters["familyCode"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            // Filtra no MongoDB: Só traz os itens onde familyCode == código do URL
            // Trazemos os itens, mas usamos o .map para APAGAR a foto da memória antes de enviar para o telemóvel.
            val items = collection.find(ShoppingItem::familyCode eq familyCode).toList().map {
                it.copy(photoBase64 = null)
            }
            call.respond(HttpStatusCode.OK, items)
        }

        // LER APENAS UM ITEM (Usado para descarregar a foto a pedido)
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val item = collection.findOneById(id)
            if (item != null) {
                call.respond(HttpStatusCode.OK, item)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // --- ROTAS DAS SUGESTÕES RÁPIDAS ---
        get("/suggestions") {
            val familyCode = call.parameters["familyCode"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val suggestions = db.getCollection<QuickSuggestion>("quick_suggestions")
                .find(QuickSuggestion::familyCode eq familyCode).toList()
            call.respond(HttpStatusCode.OK, suggestions)
        }

        post("/suggestions") {
            val familyCode = call.parameters["familyCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val sug = call.receive<QuickSuggestion>()
            val finalId = if (sug.id.isBlank()) org.bson.types.ObjectId().toString() else sug.id
            val toSave = sug.copy(id = finalId, familyCode = familyCode)
            db.getCollection<QuickSuggestion>("quick_suggestions").insertOne(toSave)
            call.respond(HttpStatusCode.Created, toSave)
        }

        delete("/suggestions/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.getCollection<QuickSuggestion>("quick_suggestions").deleteOneById(id)
            call.respond(HttpStatusCode.OK, "Sugestão apagada")
        }
        // -----------------------------------

        // 2. CRIAR um item naquela família (POST)
        post {
            val familyCode = call.parameters["familyCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val item = call.receive<ShoppingItem>()

            // Se o ID vier vazio, geramos um novo ObjectId do MongoDB
            val finalId = if (item.id.isBlank()) org.bson.types.ObjectId().toString() else item.id

            // Garante que o item fica associado à família correta E com o ID correto
            val itemToSave = item.copy(
                id = finalId,
                familyCode = familyCode
            )

            collection.insertOne(itemToSave)

            // Avisa APENAS os telemóveis que estão na sala desta família
            val msg = Json.encodeToString(WsMessage("ADD", item = itemToSave.copy(photoBase64 = null)))
            familyRooms[familyCode]?.forEach { session -> session.send(Frame.Text(msg)) }

            call.respond(HttpStatusCode.Created, itemToSave)
        }

        // 3. ATUALIZAR um item (PUT)
        put("/{id}") {
            val familyCode = call.parameters["familyCode"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)

            val updatedItem = call.receive<ShoppingItem>()
            val atualizou = collection.updateOneById(id, updatedItem.copy(familyCode = familyCode)).wasAcknowledged()

            if (atualizou) {
                val msg = Json.encodeToString(WsMessage("UPDATE", item = updatedItem.copy(photoBase64 = null)))
                familyRooms[familyCode]?.forEach { session -> session.send(Frame.Text(msg)) }
                call.respond(HttpStatusCode.OK, updatedItem)
            } else {
                call.respond(HttpStatusCode.NotFound, "Item não encontrado")
            }
        }

        // 4. APAGAR um item (DELETE)
        delete("/{id}") {
            val familyCode = call.parameters["familyCode"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)

            val apagou = collection.deleteOneById(id).wasAcknowledged()

            if (apagou) {
                val msg = Json.encodeToString(WsMessage("DELETE", itemId = id))
                familyRooms[familyCode]?.forEach { session -> session.send(Frame.Text(msg)) }
                call.respond(HttpStatusCode.OK, "Item apagado")
            } else {
                call.respond(HttpStatusCode.NotFound, "Item não encontrado")
            }
        }

        // 4.5 APAGAR TODOS OS COMPRADOS
        delete("/bought") {
            val familyCode = call.parameters["familyCode"] ?: return@delete call.respond(HttpStatusCode.BadRequest)

            // Apaga todos os itens da família que tenham o isBought a true
            val result = collection.deleteMany(
                and(
                    ShoppingItem::familyCode eq familyCode,
                    ShoppingItem::isBought eq true
                )
            )

            if (result.deletedCount > 0) {
                // Avisa a sala para recarregar
                val msg = Json.encodeToString(WsMessage("DELETE_BOUGHT"))
                familyRooms[familyCode]?.forEach { session -> session.send(Frame.Text(msg)) }
                call.respond(HttpStatusCode.OK, "Itens apagados")
            } else {
                call.respond(HttpStatusCode.NotFound, "Nada para apagar")
            }
        }


        // 5. WEBSOCKET - O "Túnel" específico desta família
        webSocket("/updates") {
            val familyCode = call.parameters["familyCode"] ?: return@webSocket

            // Se a sala da família ainda não existir, cria-a
            val room = familyRooms.computeIfAbsent(familyCode) { mutableListOf() }

            // O teu telemóvel entra na sala
            room.add(this)

            try {
                // Fica aqui parado a manter o túnel aberto...
                for (frame in incoming) { }
            } finally {
                // Quando fechas a app, sais da sala
                room.remove(this)
                // Limpeza: Se a sala ficar vazia, apaga-a para poupar memória no servidor
                if (room.isEmpty()) {
                    familyRooms.remove(familyCode)
                }
            }
        }
    }
}