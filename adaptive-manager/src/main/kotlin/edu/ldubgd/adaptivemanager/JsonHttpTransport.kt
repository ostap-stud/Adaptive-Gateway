package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Bounded JSON transport. Redirects are never followed, including authenticated writes. */
@Component
class JsonHttpTransport(json: ObjectMapper, private val settings: ManagerProperties) {
    private val parser = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(settings.timeoutMs))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    fun request(method: String, uri: URI, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): JsonNode {
        require(method in setOf("GET", "PUT", "POST"))
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(settings.timeoutMs))
            .header("Accept", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofByteArray) ?: HttpRequest.BodyPublishers.noBody())
        if (body != null) request.header("Content-Type", "application/json")
        headers.forEach { (name, value) -> request.header(name, value) }
        val pending = http.sendAsync(request.build(), HttpResponse.BodyHandler { LimitedBody(settings.maxResponseBytes) })
        try {
            val response = pending.get(settings.timeoutMs, TimeUnit.MILLISECONDS)
            if (response.statusCode() != 200) throw ObservationFailure("HTTP_${response.statusCode()}")
            return try { parser.readTree(response.body()) ?: throw ObservationFailure("INVALID_JSON") }
            catch (_: com.fasterxml.jackson.core.JacksonException) { throw ObservationFailure("INVALID_JSON") }
        } catch (_: TimeoutException) { throw ObservationFailure("TIMEOUT") }
        catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw ObservationFailure("INTERRUPTED") }
        catch (e: ExecutionException) {
            var cause: Throwable? = e.cause
            while (cause != null) {
                if (cause is ObservationFailure) throw cause
                if (cause is java.net.http.HttpTimeoutException) throw ObservationFailure("TIMEOUT")
                cause = cause.cause
            }
            throw ObservationFailure("NETWORK_ERROR")
        } finally { pending.cancel(true) }
    }

    private class LimitedBody(private val maximum: Int) : HttpResponse.BodySubscriber<ByteArray> {
        private val result = CompletableFuture<ByteArray>()
        private val bytes = ByteArrayOutputStream()
        private lateinit var subscription: Flow.Subscription
        override fun getBody(): CompletionStage<ByteArray> = result
        override fun onSubscribe(value: Flow.Subscription) { subscription = value; value.request(1) }
        override fun onNext(items: List<ByteBuffer>) {
            for (item in items) {
                if (item.remaining() > maximum - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(ObservationFailure("RESPONSE_TOO_LARGE")); return
                }
                val chunk = ByteArray(item.remaining()); item.get(chunk); bytes.write(chunk)
            }
            subscription.request(1)
        }
        override fun onError(error: Throwable) { result.completeExceptionally(error) }
        override fun onComplete() { result.complete(bytes.toByteArray()) }
    }
}
