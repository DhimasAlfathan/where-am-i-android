package com.example.mocklocationtester

import org.junit.Assert.*
import org.junit.Test

class SavedPlacesTest {
    private val office = Place("Office building", "10 Main Street", -6.2, 106.8)
    @Test fun selectionHistoryIsRecentUniqueAndBounded() {
        val store=SavedPlaces(null) {}
        for(i in 0..35) store.record(Place("Place $i", "Address", i.toDouble(), 100.0))
        assertEquals(30,store.history().size)
        val repeated=store.history()[10]
        store.record(repeated)
        assertEquals(repeated,store.history().first())
        assertEquals(30,store.history().size)
        assertEquals(1,store.history().count{it.key==repeated.key})
    }
    @Test fun renamedFavoriteAndHistorySurviveRestartWithoutChangingCoordinates() {
        var disk=""
        val store=SavedPlaces(null) { disk=it }
        store.record(office);store.toggleFavorite(office);store.rename(office,"  My office  ")
        val restored=SavedPlaces(disk) {}
        assertEquals("My office",restored.displayName(office))
        assertEquals(office,restored.favorites().single().place)
        assertEquals(office,restored.history().single())
        assertEquals(1,restored.favorites("MY OFFICE").size)
        assertEquals(1,restored.history("main street").size)
        assertEquals(1,restored.favorites("Office building").size)
    }
    @Test fun clearingHistoryKeepsFavoritesAndRemovingFavoriteKeepsHistory() {
        var disk=""
        val store=SavedPlaces(null) { disk=it }
        store.record(office);store.toggleFavorite(office);store.rename(office,"Work")
        store.clearHistory()
        val restored=SavedPlaces(disk) { disk=it }
        assertTrue(restored.history().isEmpty());assertEquals("Work",restored.displayName(office))
        restored.record(office);restored.toggleFavorite(office)
        val again=SavedPlaces(disk) {}
        assertEquals(office,again.history().single());assertTrue(again.favorites().isEmpty())
        assertEquals(office.name,again.displayName(office))
    }
    @Test fun malformedDataAndInvalidCoordinatesDoNotCrashLoading() {
        assertTrue(SavedPlaces("broken") {}.history().isEmpty())
        val data="""{"history":[{"name":"bad","address":"x","lat":91,"lon":1},{"name":"ok","address":"x","lat":1,"lon":2}],"favorites":[{}, {"place":{"name":"ok","address":"x","lat":1,"lon":2},"label":"Home"}]}"""
        val restored=SavedPlaces(data) {}
        assertEquals("ok",restored.history().single().name)
        assertEquals("Home",restored.favorites().single().label)
    }
    @Test fun emptyAndOverlongNamesCannotOverwriteFavorite() {
        val store=SavedPlaces(null) {};store.toggleFavorite(office)
        for(label in listOf("  ","x".repeat(81))) {
            try {store.rename(office,label);fail("Invalid name accepted")}catch(_:IllegalArgumentException){}
        }
        assertEquals(office.name,store.displayName(office))
    }
    @Test fun favoriteSurvivesHistoryEvictionAndUnicodeRoundTrip() {
        var disk=""
        val store=SavedPlaces(null) {disk=it}
        store.record(office);store.toggleFavorite(office);store.rename(office,"Rumah 🏡 • 東京")
        for(i in 0..35)store.record(Place("$i","Address",i.toDouble(),0.0))
        val restored=SavedPlaces(disk) {}
        assertEquals("Rumah 🏡 • 東京",restored.displayName(office))
        assertFalse(restored.history().any{it.key==office.key})
    }
}
