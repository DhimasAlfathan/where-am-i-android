package com.example.mocklocationtester

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class Place(val name: String, val address: String, val lat: Double, val lon: Double) {
    val key: String get() = String.format(Locale.US, "%.6f,%.6f", if (lat == 0.0) 0.0 else lat, if (lon == 0.0) 0.0 else lon)
    val valid: Boolean get() = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}
internal data class Favorite(val place: Place, val label: String)

/** One persisted document keeps history and favorites consistent across process restarts. */
internal class SavedPlaces(serialized: String?, private val save: (String) -> Unit) {
    private val recent = mutableListOf<Place>()
    private val favorites = linkedMapOf<String, Favorite>()
    init {
        val root = try { JSONObject(serialized ?: "{}") } catch (_: Exception) { JSONObject() }
        val history = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until history.length()) {
            val place = decode(history.optJSONObject(i)) ?: continue
            if (recent.none { it.key == place.key } && recent.size < 30) recent.add(place)
        }
        val saved = root.optJSONArray("favorites") ?: JSONArray()
        for (i in 0 until saved.length()) {
            val item = saved.optJSONObject(i) ?: continue
            val place = decode(item.optJSONObject("place")) ?: continue
            val label = item.optString("label").trim().take(80).ifBlank { place.name }
            favorites[place.key] = Favorite(place, label)
        }
    }
    fun history(query: String = ""): List<Place> = recent.filter { matches(it, query) }
    fun favorites(query: String = ""): List<Favorite> = favorites.values.filter { matches(it.place, query) }
    fun favorite(place: Place): Favorite? = favorites[place.key]
    fun displayName(place: Place): String = favorite(place)?.label ?: place.name
    private fun matches(place: Place, query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || displayName(place).contains(q, true) || place.name.contains(q, true) || place.address.contains(q, true) || place.key.contains(q)
    }
    fun record(place: Place) {
        require(place.valid)
        recent.removeAll { it.key == place.key }
        recent.add(0, place)
        if (recent.size > 30) recent.removeAt(recent.lastIndex)
        persist()
    }
    fun toggleFavorite(place: Place) {
        require(place.valid)
        if (favorites.remove(place.key) == null) favorites[place.key] = Favorite(place, place.name)
        persist()
    }
    fun rename(place: Place, label: String) {
        val trimmed = label.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 80)
        val old = favorites[place.key] ?: return
        favorites[place.key] = old.copy(label = trimmed)
        persist()
    }
    fun clearHistory() { recent.clear(); persist() }
    private fun persist() {
        val history = JSONArray(); recent.forEach { history.put(encode(it)) }
        val saved = JSONArray(); favorites.values.forEach { saved.put(JSONObject().put("place", encode(it.place)).put("label", it.label)) }
        save(JSONObject().put("history", history).put("favorites", saved).toString())
    }
    private fun encode(place: Place) = JSONObject().put("name", place.name).put("address", place.address).put("lat", place.lat).put("lon", place.lon)
    private fun decode(item: JSONObject?): Place? {
        if (item == null) return null
        return try {
            Place(item.getString("name"), item.getString("address"), item.getDouble("lat"), item.getDouble("lon")).takeIf { it.valid }
        } catch (_: Exception) { null }
    }
}
