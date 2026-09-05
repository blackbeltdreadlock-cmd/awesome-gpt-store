package com.example.gptstore.data

import android.content.Context
import android.content.SharedPreferences
import com.example.gptstore.model.GptItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.util.UUID

class GptRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gpt_store_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _itemsFlow = MutableStateFlow<List<GptItem>>(emptyList())
    val itemsFlow: StateFlow<List<GptItem>> = _itemsFlow.asStateFlow()

    private var defaultGpts: List<GptItem> = emptyList()
    private var customGpts: MutableList<GptItem> = mutableListOf()
    private var bookmarkedIds: MutableSet<String> = mutableSetOf()

    init {
        coroutineScope.launch {
            loadInitialData()
        }
    }

    private fun loadInitialData() {
        // 1. Load bookmarked IDs
        val savedBookmarks = prefs.getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
        bookmarkedIds = savedBookmarks.toMutableSet()

        // 2. Load custom GPTs
        val customJson = prefs.getString(KEY_CUSTOM_GPTS, null)
        if (!customJson.isNullOrBlank()) {
            try {
                val listType = object : TypeToken<List<GptItem>>() {}.type
                val loadedCustom: List<GptItem>? = gson.fromJson(customJson, listType)
                if (loadedCustom != null) {
                    customGpts = loadedCustom.toMutableList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Load default GPTs from assets/gpts.json
        try {
            context.assets.open("gpts.json").use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val listType = object : TypeToken<List<GptItem>>() {}.type
                defaultGpts = gson.fromJson(reader, listType) ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            defaultGpts = emptyList()
        }

        rebuildList()
    }

    private fun rebuildList() {
        val combined = mutableListOf<GptItem>()
        // User custom GPTs appear first
        combined.addAll(customGpts.map { it.copy(isBookmarked = bookmarkedIds.contains(it.id)) })
        // Default GPTs
        combined.addAll(defaultGpts.map { it.copy(isBookmarked = bookmarkedIds.contains(it.id)) })
        _itemsFlow.value = combined
    }

    fun toggleBookmark(id: String) {
        if (bookmarkedIds.contains(id)) {
            bookmarkedIds.remove(id)
        } else {
            bookmarkedIds.add(id)
        }
        prefs.edit().putStringSet(KEY_BOOKMARKS, bookmarkedIds).apply()
        rebuildList()
    }

    fun addCustomGpt(name: String, url: String, description: String, category: String): GptItem {
        val newItem = GptItem(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = name.trim(),
            url = url.trim(),
            description = description.trim(),
            category = category.trim(),
            isCustom = true,
            isBookmarked = false,
            createdAt = System.currentTimeMillis()
        )
        customGpts.add(0, newItem)
        saveCustomGpts()
        rebuildList()
        return newItem
    }

    fun deleteCustomGpt(id: String) {
        customGpts.removeAll { it.id == id }
        bookmarkedIds.remove(id)
        saveCustomGpts()
        prefs.edit().putStringSet(KEY_BOOKMARKS, bookmarkedIds).apply()
        rebuildList()
    }

    private fun saveCustomGpts() {
        val json = gson.toJson(customGpts)
        prefs.edit().putString(KEY_CUSTOM_GPTS, json).apply()
    }

    companion object {
        private const val KEY_BOOKMARKS = "bookmarked_ids"
        private const val KEY_CUSTOM_GPTS = "custom_gpts"
    }
}
