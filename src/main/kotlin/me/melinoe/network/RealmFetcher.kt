package me.melinoe.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import me.melinoe.Melinoe
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object RealmFetcher {
    // Fallbacks in case they aren't fetched properly
    var naServers: List<String> = listOf("Ashburn", "Bayou", "Cedar", "Dakota", "Eagleton", "Farrion", "Groveridge", "Holloway", "Hub-1", "Missions")
    var euServers: List<String> = listOf("Astra", "Balkan", "Creska", "Draskov", "Estenmoor", "Falkenburg", "Galla", "Helmburg", "Ivarn", "Jarnwald", "Krausenfeld", "Lindenburg", "Hub-1", "Missions")
    var sgServers: List<String> = listOf("Asura", "Bayan", "Chantara", "Hub-1", "Missions")
    
    private const val SERVERS_JSON_URL = "https://gist.githubusercontent.com/Retuning/64674dd830e79fadf537b83cc88fc107/raw/904c0952159c19df31079fcca627fa6576f03789/servers.json"
    
    fun fetchServers() {
        CompletableFuture.runAsync {
            try {
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVERS_JSON_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build()
                
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                
                if (response.statusCode() == 200) {
                    val json = Gson().fromJson(response.body(), JsonObject::class.java)
                    
                    if (json.has("NA")) naServers = json.getAsJsonArray("NA").map { it.asString }
                    if (json.has("EU")) euServers = json.getAsJsonArray("EU").map { it.asString }
                    if (json.has("SG")) sgServers = json.getAsJsonArray("SG").map { it.asString }
                    
                    Melinoe.logger.info("[RealmFetcher] Successfully updated server lists from GitHub.")
                } else {
                    Melinoe.logger.warn("[RealmFetcher] Failed to fetch server lists. HTTP Status: ${response.statusCode()}")
                }
            } catch (e: Exception) {
                Melinoe.logger.error("[RealmFetcher] Error fetching server lists from GitHub", e)
            }
        }
    }
}