package com.koushik.invoice.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Test data builder over the brief's own {@code seed-payloads.json}. Each test gets a fresh suffix on
 * {@code external_id} so tests stay independent while sharing one database.
 */
public final class Fixtures {

	private final JsonNode seeds;

	private final JsonNode expectedInvoice;

	private final String suffix = UUID.randomUUID().toString().substring(0, 8);

	public Fixtures(JsonMapper json) {
		this.seeds = read(json, "/fixtures/task-b/seed-payloads.json");
		this.expectedInvoice = read(json, "/fixtures/task-b/expected-invoice.json");
	}

	/** A seed payload (flight, hotel, rail, trip_fee, agent_call_fee, duplicate_flight) with a unique id. */
	public ObjectNode seed(String name) {
		ObjectNode payload = (ObjectNode) seeds.get(name).deepCopy();
		payload.remove("comment");
		payload.put("external_id", payload.get("external_id").asString() + "#" + suffix);
		return payload;
	}

	public JsonNode expectedInvoice() {
		return expectedInvoice;
	}

	private static JsonNode read(JsonMapper json, String resource) {
		try (InputStream in = Fixtures.class.getResourceAsStream(resource)) {
			return json.readTree(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
