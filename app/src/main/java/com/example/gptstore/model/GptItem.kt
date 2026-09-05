package com.example.gptstore.model

data class GptItem(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    val category: String,
    val isCustom: Boolean = false,
    val isBookmarked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class CategoryCount(
    val name: String,
    val count: Int
)
