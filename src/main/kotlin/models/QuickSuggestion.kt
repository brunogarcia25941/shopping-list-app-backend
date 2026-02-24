package com.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId

@Serializable
data class QuickSuggestion(
    @BsonId val id: String = "",
    val familyCode: String = "",
    val name: String
)