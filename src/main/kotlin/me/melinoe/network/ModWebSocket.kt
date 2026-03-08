package me.melinoe.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.melinoe.Melinoe
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

object ModWebSocket : WebSocket.Listener {
    private var webSocket: WebSocket? = null
    private val httpClient = HttpClient.newHttpClient()
    
    val activeModUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    
    fun connect(name: String) {
        // Build and connect asynchronously
        httpClient.newWebSocketBuilder()
            .buildAsync(URI.create("ws://fig.magnetite.dev:3000"), this)
            .thenAccept { ws ->
                this.webSocket = ws
                
                // Send join payload
                val joinMessage = JsonObject().apply {
                    addProperty("action", "join")
                    addProperty("name", name)
                }
                ws.sendText(joinMessage.toString(), true)
            }.exceptionally {
                Melinoe.logger.error("Failed to connect to Mod WebSocket: ${it.message}")
                null
            }
    }
    
    fun disconnect() {
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting")
        webSocket = null
        activeModUsers.clear()
    }
    
    // Called whenever the server sends us a message
    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        val json = JsonParser.parseString(data.toString()).asJsonObject
        
        when (json.get("action").asString) {
            "sync" -> {
                activeModUsers.clear()
                json.getAsJsonArray("names").forEach {
                    activeModUsers.add(it.asString)
                }
            }
            "add" -> {
                val name = json.get("name").asString
                activeModUsers.add(name)
            }
            "remove" -> {
                val name = json.get("name").asString
                activeModUsers.remove(name)
            }
        }
        
        // Let the WebSocket know we are ready for the next message
        return super.onText(webSocket, data, last)
    }
}