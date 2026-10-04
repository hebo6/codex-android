package com.hebo.codex.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class AppServerHistoryApiTest {
    @Test
    fun resumeAndOlderTurnsRequestSummariesWhileDetailsUseItemPagination() = runTest {
        val requests = mutableListOf<JsonObject>()
        val connection = AppServerConnection(
            webSocketFactory = AppServerWebSocketFactory { listener ->
                object : AppServerWebSocket {
                    override fun start() = listener.onOpen(this)

                    override fun setResponseTimeout(timeoutMillis: Long) = Unit

                    override fun send(text: String): Boolean {
                        val request = Json.parseToJsonElement(text).jsonObject
                        val result = when (request.requiredString("method")) {
                            "initialized" -> return true
                            "initialize" -> json(
                                """{"userAgent":"test","codexHome":"/tmp/codex","platformFamily":"unix","platformOs":"linux"}""",
                            )
                            else -> {
                                requests += request
                                json("{}")
                            }
                        }
                        listener.onMessage(this, buildJsonObject {
                            put("id", requireNotNull(request["id"]))
                            put("result", result)
                        }.toString())
                        return true
                    }

                    override fun close(code: Int, reason: String): Boolean = true

                    override fun cancel() = Unit
                }
            },
            json = Json,
            connectTimeoutMillis = 1_000,
            parentContext = coroutineContext,
        )

        try {
            connection.connect()
            val api = AppServerApi(connection)

            api.resumeThread("thread-1")
            api.readThread("thread-1")
            api.listThreadTurns("thread-1", cursor = "older-turns")
            api.listThreadItems("thread-1", "turn-1", cursor = "next-items")

            assertEquals(
                listOf("thread/resume", "thread/read", "thread/turns/list", "thread/items/list"),
                requests.map { it.requiredString("method") },
            )
            assertEquals(
                json("""{"threadId":"thread-1","excludeTurns":true,"initialTurnsPage":{"limit":25,"sortDirection":"desc","itemsView":"summary"}}"""),
                requests[0].requiredObject("params"),
            )
            assertEquals(
                json("""{"threadId":"thread-1","includeTurns":false}"""),
                requests[1].requiredObject("params"),
            )
            assertEquals(
                json("""{"threadId":"thread-1","cursor":"older-turns","limit":25,"sortDirection":"desc","itemsView":"summary"}"""),
                requests[2].requiredObject("params"),
            )
            assertEquals(
                json("""{"threadId":"thread-1","turnId":"turn-1","limit":100,"sortDirection":"asc","cursor":"next-items"}"""),
                requests[3].requiredObject("params"),
            )
        } finally {
            connection.close()
        }
    }

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
}
