package e2e

import io.zedpkg.opto_sync.OptoSyncClient
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

data class SyncLane(
    val name: String,
    val baseJson: String,
    val incomingJson: String,
)

data class SyncLaneResponse(
    val name: String,
    val body: String,
)

/**
 * Process-safe worker core for Android WorkManager, desktop schedulers, or a
 * JVM service. One wake multiplexes independent lanes with async HTTP and
 * retries the immutable batch when any lane loses its response.
 */
class BackgroundSyncWorker(
    private val client: OptoSyncClient,
    private val maxAttempts: Int = 8,
    private val retryDelay: Duration = Duration.ofMillis(100),
    private val http: HttpClient = HttpClient.newHttpClient(),
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }

    fun drain(lanes: List<SyncLane>): List<SyncLaneResponse> {
        if (lanes.isEmpty()) return emptyList()
        require(lanes.map { it.name }.toSet().size == lanes.size) {
            "lane names must be unique"
        }

        var lastFailure: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return lanes
                    .map(::send)
                    .let { pending ->
                        CompletableFuture.allOf(*pending.toTypedArray()).join()
                        pending.map { it.join() }.sortedBy { it.name }
                    }
            } catch (failure: Throwable) {
                lastFailure = failure
                if (attempt + 1 < maxAttempts) Thread.sleep(retryDelay.toMillis())
            }
        }
        error("background sync exhausted $maxAttempts attempts: $lastFailure")
    }

    private fun send(lane: SyncLane): CompletableFuture<SyncLaneResponse> {
        val request = HttpRequest.newBuilder(client.baseUri.resolve("/merge"))
            .timeout(Duration.ofSeconds(5))
            .header("content-type", "application/json")
            .apply {
                client.bearerToken?.let { header("authorization", "Bearer $it") }
            }
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """{"base":${lane.baseJson},"incoming":${lane.incomingJson}}""",
                ),
            )
            .build()
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                check(response.statusCode() == 200) {
                    "lane ${lane.name} failed with HTTP ${response.statusCode()}"
                }
                SyncLaneResponse(lane.name, response.body())
            }
    }
}
