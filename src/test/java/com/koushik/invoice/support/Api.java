package com.koushik.invoice.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal HTTP client for the tests: real requests against the running server, JSON in and out. */
public final class Api {

	private final HttpClient http = HttpClient.newHttpClient();

	private final String baseUrl;

	private final JsonMapper json;

	public Api(int port, JsonMapper json) {
		this.baseUrl = "http://localhost:" + port;
		this.json = json;
	}

	public Response get(String path) {
		return send("GET", path, null);
	}

	public Response post(String path, Object body) {
		return send("POST", path, body);
	}

	public Response patch(String path, Object body) {
		return send("PATCH", path, body);
	}

	public Response delete(String path) {
		return send("DELETE", path, null);
	}

	private Response send(String method, String path, Object body) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.header("Content-Type", "application/json")
			.method(method, body == null ? BodyPublishers.noBody()
					: BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body)));
		try {
			var response = http.send(request.build(), BodyHandlers.ofString());
			JsonNode node = response.body().isEmpty() ? null : json.readTree(response.body());
			return new Response(response.statusCode(), node);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	public record Response(int status, JsonNode body) {

		public String id() {
			return body.get("id").asString();
		}

	}

}
