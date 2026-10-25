package com.koushik.invoice.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import com.koushik.invoice.support.Api;
import com.koushik.invoice.support.ApiTestSupport;
import com.koushik.invoice.support.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationAndLifecycleTest extends ApiTestSupport {

	@Test
	void unknownTypeIsBadRequest() {
		ObjectNode body = fixtures.seed("flight");
		body.put("type", "cruise");
		assertThat(api.post("/transactions", body).status()).isEqualTo(400);
	}

	@Test
	void unknownFeeKindIsBadRequest() {
		ObjectNode body = fixtures.seed("trip_fee");
		((ObjectNode) body.get("metadata")).put("fee_kind", "lounge_fee");
		assertThat(api.post("/transactions", body).status()).isEqualTo(400);
	}

	@Test
	void emptyMetadataIsBadRequest() {
		ObjectNode body = fixtures.seed("hotel");
		body.putObject("metadata");
		Api.Response response = api.post("/transactions", body);
		assertThat(response.status()).isEqualTo(400);
		assertThat(response.body().get("errors").toString()).contains("metadata.name", "metadata.address");
	}

	@Test
	void newFlightNeedsAtLeastOneCoupon() {
		Api.Response response = api.post("/transactions", fixtures.seed("duplicate_flight"));
		assertThat(response.status()).isEqualTo(400);
		assertThat(rowsWithExternalId(fixtures.seed("flight").get("external_id").asString())).isZero();
	}

	@Test
	void moreThanTwoDecimalsIsBadRequest() {
		ObjectNode body = fixtures.seed("rail");
		body.put("total", new BigDecimal("94.005"));
		assertThat(api.post("/transactions", body).status()).isEqualTo(400);
	}

	@Test
	void hotelNightsMustMatchDates() {
		ObjectNode body = fixtures.seed("hotel");
		((ObjectNode) body.get("metadata")).put("nights", 3);
		assertThat(api.post("/transactions", body).status()).isEqualTo(400);
	}

	@Test
	void invalidCurrencyIsBadRequest() {
		ObjectNode body = fixtures.seed("rail");
		body.put("currency", "EURO");
		assertThat(api.post("/transactions", body).status()).isEqualTo(400);
	}

	@Test
	void timestampsNeedAnOffsetAndAreStoredAsUtc() {
		ObjectNode noOffset = fixtures.seed("rail");
		noOffset.put("occurred_at", "2026-03-13T09:00:00");
		assertThat(api.post("/transactions", noOffset).status()).isEqualTo(400);

		ObjectNode withOffset = fixtures.seed("rail");
		withOffset.put("occurred_at", "2026-03-13T10:00:00+01:00");
		Api.Response created = api.post("/transactions", withOffset);
		assertThat(created.status()).isEqualTo(201);
		assertThat(created.body().get("occurred_at").asString()).isEqualTo("2026-03-13T09:00:00Z");
	}

	@Test
	void missingIdsAreNotFound() {
		assertThat(api.get("/transactions/0192f000-0000-7000-8000-000000000000").status()).isEqualTo(404);
		assertThat(api.get("/transactions/txn_123").status()).isEqualTo(404);
		assertThat(api.get("/invoices/inv_123").status()).isEqualTo(404);
		assertThat(api.post("/invoices", Map.of("transaction_id", "txn_123")).status()).isEqualTo(404);
		Api.Response partlyMissing = invoice(create("flight"), "0192f000-0000-7000-8000-000000000000");
		assertThat(partlyMissing.status()).isEqualTo(404);
	}

	@Test
	void sameTransactionTwiceOnOneInvoiceIsConflict() {
		String flightId = create("flight");
		assertThat(invoice(flightId, flightId).status()).isEqualTo(409);
	}

	@Test
	void singleTransactionIdBodyIsSupported() {
		Api.Response response = api.post("/invoices", Map.of("transaction_id", create("rail")));
		assertThat(response.status()).isEqualTo(201);
		assertMoney(response.body().at("/totals/grand_total"), "94.00");
	}

	@Test
	void patchReplacesListsOfAnUninvoicedTransaction() {
		String hotelId = create("hotel");
		Api.Response patched = api.patch("/transactions/" + hotelId,
				Map.of("total", 340, "tax_lines", List.of(Map.of("name", "VAT", "rate", 0.07, "amount", 22.4),
						Map.of("name", "City tax", "amount", 7.6))));

		assertThat(patched.status()).isEqualTo(200);
		JsonNode stored = api.get("/transactions/" + hotelId).body();
		assertMoney(stored.get("total"), "340.00");
		assertThat(stored.get("tax_lines")).hasSize(2);
		assertThat(stored.at("/tax_lines/1/name").asString()).isEqualTo("City tax");
		assertThat(stored.at("/metadata/name").asString()).isEqualTo("Hotel Mitte London");
	}

	@Test
	void patchCannotChangeIdentity() {
		String railId = create("rail");
		assertThat(api.patch("/transactions/" + railId, Map.of("currency", "USD")).status()).isEqualTo(400);
		assertThat(api.patch("/transactions/" + railId, Map.of("type", "flight")).status()).isEqualTo(400);
	}

	@Test
	void deleteOfUninvoicedTransactionRemovesIt() {
		String railId = create("rail");
		assertThat(api.delete("/transactions/" + railId).status()).isEqualTo(204);
		assertThat(api.get("/transactions/" + railId).status()).isEqualTo(404);
		assertThat(api.delete("/transactions/" + railId).status()).isEqualTo(404);
	}

	@Test
	void invoicePatchRebuildsSnapshotFromCurrentData() {
		String flightId = create("flight");
		String hotelId = create("hotel");
		String invoiceId = invoice(flightId).id();

		Api.Response patched = api.patch("/invoices/" + invoiceId, Map.of("transaction_ids", List.of(hotelId, flightId)));

		assertThat(patched.status()).isEqualTo(200);
		JsonNode invoice = api.get("/invoices/" + invoiceId).body();
		assertThat(invoice.get("lines")).hasSize(2);
		assertThat(invoice.at("/lines/0/type").asString()).isEqualTo("hotel");
		assertMoney(invoice.at("/totals/grand_total"), "551.70");
	}

	@Test
	void invoicePatchWithMixedCurrencyLeavesInvoiceUnchanged() {
		String flightId = create("flight");
		String invoiceId = invoice(flightId).id();
		ObjectNode usdRail = fixtures.seed("rail");
		usdRail.put("currency", "USD");
		String usdId = api.post("/transactions", usdRail).id();

		Api.Response patched = api.patch("/invoices/" + invoiceId, Map.of("transaction_ids", List.of(flightId, usdId)));

		assertThat(patched.status()).isEqualTo(409);
		JsonNode invoice = api.get("/invoices/" + invoiceId).body();
		assertThat(invoice.get("lines")).hasSize(1);
		assertMoney(invoice.at("/totals/grand_total"), "220.00");
	}

	@Test
	void cursorWalksEveryInvoiceWithoutGapsOrRepeats() {
		List<String> created = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			created.add(api.post("/invoices", Map.of("transaction_id", create("trip_fee"))).id());
			fixtures = new Fixtures(JSON);
		}
		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			JsonNode page = api.get("/invoices?limit=2" + (cursor == null ? "" : "&cursor=" + cursor)).body();
			assertThat(page.get("items").size()).isLessThanOrEqualTo(2);
			page.get("items").forEach(item -> seen.add(item.get("id").asString()));
			cursor = page.hasNonNull("next_cursor") ? page.get("next_cursor").asString() : null;
		}
		while (cursor != null);

		assertThat(seen).doesNotHaveDuplicates().containsAll(created);
	}

	@Test
	void invalidCursorIsBadRequest() {
		assertThat(api.get("/transactions?cursor=not-a-cursor").status()).isEqualTo(400);
		assertThat(api.get("/invoices?limit=0").status()).isEqualTo(400);
	}

}
