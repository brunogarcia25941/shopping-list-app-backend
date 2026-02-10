package com.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class ShoppingItem(
    @BsonId
    val id: String = ObjectId().toString(), // Gera um ID automático do MongoDB
    val name: String,
    val quantity: Int,
    val isBought: Boolean = false
)