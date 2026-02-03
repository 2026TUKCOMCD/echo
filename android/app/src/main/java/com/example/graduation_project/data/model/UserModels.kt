package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val name: String,
    val age: Int? = null,
    val birthday: String? = null
)

@Serializable
data class UserPreferences(
    val hobby: String? = null,
    val occupation: String? = null,
    val familyRelation: String? = null,
    val preferredTopics: List<String> = emptyList()
)
