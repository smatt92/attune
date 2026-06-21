package com.attune.core

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse as JdkHttpResponse
import java.time.Duration

/**
 * Minimal HTTP seam so the intent parser can be exercised with a fake transport in fast JVM
 * tests, and with a real client (and the live Claude API) in the eval harness. Keeping the
 * network behind an interface is what lets the LLM layer be tested like a model (golden eval)
 * AND like code (deterministic unit tests) — see ARCHITECTURE.md.
 */
interface HttpTransport {
    /** POST [body] as JSON with [headers]; return status + raw response body. */
    fun postJson(url: String, headers: Map<String, String>, body: String): HttpResponse
}

data class HttpResponse(val statusCode: Int, val body: String)

/** Real transport over the JDK's built-in HttpClient (no third-party HTTP dependency). */
class JdkHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build(),
    private val requestTimeout: Duration = Duration.ofSeconds(60),
) : HttpTransport {
    override fun postJson(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = client.send(builder.build(), JdkHttpResponse.BodyHandlers.ofString())
        return HttpResponse(response.statusCode(), response.body())
    }
}
