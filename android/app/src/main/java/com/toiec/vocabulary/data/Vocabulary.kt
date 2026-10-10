package com.toiec.vocabulary.data

import org.json.JSONObject

data class Vocabulary(val id: String, val day: Int, val word: String, val meaning: String)
data class Day(val day: Int, val title: String, val count: Int)
data class Catalog(val days: List<Day>, val entries: List<Vocabulary>) {
    companion object {
        fun parse(json: String): Catalog {
            val root = JSONObject(json)
            val days = root.getJSONArray("days")
            val entries = root.getJSONArray("entries")
            val catalog = Catalog(
                List(days.length()) { i -> days.getJSONObject(i).let { Day(it.getInt("day"), it.getString("title"), it.getInt("count")) } },
                List(entries.length()) { i -> entries.getJSONObject(i).let { Vocabulary(it.getString("id"), it.getInt("day"), it.getString("word"), it.getString("meaning")) } },
            )
            require(catalog.days.isNotEmpty() && catalog.entries.isNotEmpty())
            require(catalog.entries.all { it.word.isNotBlank() && it.meaning.isNotBlank() })
            require(catalog.days.all { day -> catalog.entries.count { it.day == day.day } == day.count })
            return catalog
        }
    }
}
