package com.koushik.invoice.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import com.koushik.invoice.support.Api;
import com.koushik.invoice.support.ApiTestSupport;
import com.koushik.invoice.support.Race;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Races between different operations. The required tests only race two identical POSTs; these cover the
 * interleavings that the row locks in ARCHITECTURE.md exist for. Each runs several times because a race that
 * passes once may simply not have overlapped.
 */
class ConcurrencyEdgeCaseTest extends ApiTestSupport {

	/**
	 * Why: the most dangerous race in the brief. If the invoice copied the flight while a PATCH was half-applied,
	 * the invoice would bill a total the stored transaction never had. Invariant: whatever order the database
	 * picks, the invoice line equals the stored total (PATCH first: both 300; invoice first: PATCH is 409, both 220).
	 */
	@RepeatedTest(10)
	@DisplayName("Invoice create racing a PATCH of the same transaction never snapshots a half-applied change")
	void invoiceCreateVersusPatch() throws Exception {
		String flightId = create("flight");

		List<Api.Response> results = Race.run(
				() -> api.patch("/transactions/" + flightId, Map.of("total", 300)),
				() -> invoice(flightId));
		Api.Response patch = results.get(0);
		Api.Response invoice = results.get(1);

		assertThat(invoice.status()).isEqualTo(201);
		assertThat(patch.status()).isIn(200, 409);
		JsonNode stored = api.get("/transactions/" + flightId).body();
		JsonNode billed = api.get("/invoices/" + invoice.id()).body();
		assertThat(billed.at("/lines/0/total").decimalValue()).isEqualByComparingTo(stored.get("total").decimalValue());
		assertMoney(stored.get("total"), patch.status() == 200 ? "300.00" : "220.00");
	}

	/**
	 * Why: without a lock, DELETE could pass its "not invoiced" check while an invoice inserts a line for the
	 * same row. Exactly one of the two may win: (204, 404) or (409, 201), never both successes.
	 */
	@RepeatedTest(10)
	@DisplayName("DELETE racing an invoice create: exactly one of them succeeds")
	void deleteVersusInvoiceCreate() throws Exception {
		String railId = create("rail");

		List<Api.Response> results = Race.run(() -> api.delete("/transactions/" + railId), () -> invoice(railId));
		int delete = results.get(0).status();
		int invoice = results.get(1).status();

		assertThat(List.of(delete, invoice)).isIn(List.of(204, 404), List.of(409, 201));
		assertThat(api.get("/transactions/" + railId).status()).isEqualTo(delete == 204 ? 404 : 200);
	}

	/**
	 * Why: the brief says "a torn tax_lines list is not" acceptable. Eight PATCHes each send a different list
	 * (writer i sends i + 1 lines named "W{i}-n"). The stored list must be exactly one writer's list, never a mix.
	 */
	@Test
	@DisplayName("Eight concurrent PATCHes replacing tax_lines leave exactly one writer's complete list")
	void concurrentPatchesNeverTearALineList() throws Exception {
		String hotelId = create("hotel");
		List<Callable<Api.Response>> writers = new ArrayList<>();
		for (int writer = 0; writer < 8; writer++) {
			List<Map<String, Object>> lines = new ArrayList<>();
			for (int n = 0; n <= writer; n++) {
				lines.add(Map.of("name", "W" + writer + "-" + n, "amount", 1));
			}
			writers.add(() -> api.patch("/transactions/" + hotelId, Map.of("tax_lines", lines)));
		}

		List<Api.Response> results = Race.run(writers.toArray(Callable[]::new));

		assertThat(results).extracting(Api.Response::status).containsOnly(200);
		JsonNode taxLines = api.get("/transactions/" + hotelId).body().get("tax_lines");
		String winner = taxLines.get(0).get("name").asString().split("-")[0];
		int expectedSize = Integer.parseInt(winner.substring(1)) + 1;
		assertThat(taxLines).hasSize(expectedSize);
		taxLines.forEach(line -> assertThat(line.get("name").asString()).startsWith(winner + "-"));
	}

	/**
	 * Why: two invoices that list the same transactions in opposite orders would deadlock if each locked rows in
	 * request order (Postgres would then abort one). Locks are taken in id order, so both must succeed.
	 */
	@RepeatedTest(5)
	@DisplayName("Two invoices locking the same transactions in opposite order do not deadlock")
	void oppositeOrderInvoicesDoNotDeadlock() throws Exception {
		String a = create("flight");
		String b = create("hotel");
		String c = create("rail");
		String d = create("trip_fee");

		List<Api.Response> results = Race.run(() -> invoice(a, b, c, d), () -> invoice(d, c, b, a));

		assertThat(results).extracting(Api.Response::status).containsOnly(201);
	}

	/**
	 * Why: invoice PATCH rewrites every line and rollup; a DELETE interleaving with it could leave orphaned lines
	 * or a half-rebuilt invoice. DELETE always wins in the end, and no snapshot rows may remain.
	 */
	@RepeatedTest(5)
	@DisplayName("Invoice PATCH racing invoice DELETE leaves no half-rebuilt invoice")
	void invoicePatchVersusDelete() throws Exception {
		String flightId = create("flight");
		String hotelId = create("hotel");
		String invoiceId = invoice(flightId).id();

		List<Api.Response> results = Race.run(
				() -> api.patch("/invoices/" + invoiceId, Map.of("transaction_ids", List.of(flightId, hotelId))),
				() -> api.delete("/invoices/" + invoiceId));

		assertThat(results.get(0).status()).isIn(200, 404);
		assertThat(results.get(1).status()).isEqualTo(204);
		assertThat(api.get("/invoices/" + invoiceId).status()).isEqualTo(404);
		assertThat(jdbc.queryForObject("select count(*) from invoice_lines where invoice_id = ?", Integer.class,
				uuid(invoiceId)))
			.isZero();
	}

}
