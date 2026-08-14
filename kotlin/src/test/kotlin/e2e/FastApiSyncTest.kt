package e2e

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.zedpkg.opto_sync.OptoSyncClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastApiSyncTest {
    @Test
    fun `official Kotlin client crosses FastAPI and reaches the C core`() {
        val endpoint = System.getenv("OPTO_SYNC_ENDPOINT") ?: "http://127.0.0.1:8061"
        val client = OptoSyncClient(URI(endpoint), "e2e-token")
        val request = HttpRequest.newBuilder(client.baseUri.resolve("/merge"))
            .header("authorization", "Bearer ${client.bearerToken}")
            .header("content-type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                      "base": {
                        "id": "doc-1",
                        "profile": {"server": "kept"},
                        "items": [{"id": "a", "server": true}]
                      },
                      "incoming": {
                        "profile": {"client": "kept"},
                        "items": [{"id": "a", "client": true}]
                      }
                    }
                    """.trimIndent(),
                ),
            )
            .build()

        val httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()
        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())

        val body = jacksonObjectMapper().readTree(response.body())
        assertTrue(body["core_version"].asText().matches(Regex("^\\d+\\.\\d+\\.\\d+$")))
        assertEquals("kept", body["merged"]["profile"]["server"].asText())
        assertEquals("kept", body["merged"]["profile"]["client"].asText())
        assertTrue(body["merged"]["items"][0]["server"].asBoolean())
        assertTrue(body["merged"]["items"][0]["client"].asBoolean())
    }

    @Test
    fun `background worker multiplexes mobile and desktop lanes`() {
        val endpoint = System.getenv("OPTO_SYNC_ENDPOINT") ?: "http://127.0.0.1:8061"
        val worker = BackgroundSyncWorker(
            OptoSyncClient(URI(endpoint), "e2e-token"),
        )
        val responses = worker.drain(
            listOf(
                SyncLane(
                    name = "android-workmanager",
                    baseJson = """{"id":"doc-1","profile":{"server":"kept"}}""",
                    incomingJson = """{"profile":{"mobile":"background"}}""",
                ),
                SyncLane(
                    name = "desktop-scheduler",
                    baseJson = """{"id":"doc-2","profile":{"server":"kept"}}""",
                    incomingJson = """{"profile":{"desktop":"background"}}""",
                ),
            ),
        )

        assertEquals(
            listOf("android-workmanager", "desktop-scheduler"),
            responses.map { it.name },
        )
        val bodies = responses.associate { it.name to jacksonObjectMapper().readTree(it.body) }
        assertEquals("background", bodies.getValue("android-workmanager")["merged"]["profile"]["mobile"].asText())
        assertEquals("background", bodies.getValue("desktop-scheduler")["merged"]["profile"]["desktop"].asText())
        assertTrue(bodies.values.all { it["core_version"].asText().isNotEmpty() })
    }
}
