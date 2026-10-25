package com.koushik.invoice.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import com.koushik.invoice.support.Api;
import com.koushik.invoice.support.ApiTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Boundary values and unusual-but-legal inputs. Each test says which failure it guards against. */
class DataEdgeCaseTest extends ApiTestSupport {

	private static final String MAX_AMOUNT = "99999999999999999.99";

	/**
	 * Why: the classic float bug. In double arithmetic 0.10 + 0.20 = 0.30000000000000004. The brief forbids
	 * float addition for invoice totals; this proves rollups and the grand total are exact.
	 */
	@Test
	@DisplayName("0.10 + 0.20 is exactly 0.30 in totals and rollups")
	void decimalSumsAreExact() {
		String first = createFee("0.10", "0.10");
		String second = createFee("0.20", "0.20");

		JsonNode invoice = invoice(first, second).body();

		assertMoney(invoice.at("/totals/grand_total"), "0.30");
		assertMoney(invoice.at("/fee_rollup/0/amount"), "0.30");
		assertThat(invoice.at("/totals/grand_total").decimalValue().toPlainString()).isEqualTo("0.30");
	}

	/**
	 * Why: the largest value NUMERIC(19,2) can hold must round-trip digit for digit (a double would print
	 * 1.0E17), and one digit more must be a 400, not a database error.
	 */
	@Test
	@DisplayName("Largest storable amount round-trips exactly; one digit more is 400")
	void largestAmountRoundTripsAndOverflowIsRejected() {
		ObjectNode max = fixtures.seed("trip_fee");
		max.put("total", new BigDecimal(MAX_AMOUNT));
		Api.Response created = api.post("/transactions", max);
		assertThat(created.status()).isEqualTo(201);
		assertThat(created.body().get("total").decimalValue().toPlainString()).isEqualTo(MAX_AMOUNT);

		ObjectNode tooBig = fixtures.seed("agent_call_fee");
		tooBig.put("total", new BigDecimal("100000000000000000.00"));
		assertThat(api.post("/transactions", tooBig).status()).isEqualTo(400);
	}

	/**
	 * Why: every amount is validated on its own, but an invoice adds them up. Two maximum-size transactions sum
	 * to more than NUMERIC(19,2) can store; that must be a clear 400, not a 500 or a vague database conflict.
	 */
	@Test
	@DisplayName("An invoice whose total would overflow the money column is rejected with 400")
	void invoiceTotalOverflowIsRejected() {
		ObjectNode first = fixtures.seed("trip_fee");
		first.put("total", new BigDecimal(MAX_AMOUNT));
		ObjectNode second = fixtures.seed("agent_call_fee");
		second.put("total", new BigDecimal(MAX_AMOUNT));

		Api.Response response = invoice(api.post("/transactions", first).id(), api.post("/transactions", second).id());

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.body().get("detail").asString()).contains("exceeds");
	}

	/**
	 * Why: booking references come from many systems. Non-ASCII ids must be stored, returned and deduplicated
	 * byte for byte, and ids differing only in case are different bookings (no silent case folding).
	 */
	@Test
	@DisplayName("Unicode external_id round-trips and deduplicates; case matters")
	void unicodeAndCaseSensitiveExternalIds() {
		String suffix = fixtures.seed("rail").get("external_id").asString();
		ObjectNode unicode = fixtures.seed("rail");
		unicode.put("external_id", "✈-Zürich-予約-" + suffix);
		Api.Response created = api.post("/transactions", unicode);
		assertThat(created.status()).isEqualTo(201);
		assertThat(created.body().get("external_id").asString()).isEqualTo("✈-Zürich-予約-" + suffix);
		assertThat(api.post("/transactions", unicode).status()).isEqualTo(409);

		ObjectNode lower = fixtures.seed("rail");
		lower.put("external_id", "pnr-abc-" + suffix);
		ObjectNode upper = fixtures.seed("rail");
		upper.put("external_id", "PNR-ABC-" + suffix);
		assertThat(api.post("/transactions", lower).status()).isEqualTo(201);
		assertThat(api.post("/transactions", upper).status()).isEqualTo(201);
	}

	/**
	 * Why: the column is VARCHAR(128). Exactly 128 characters must work; 129 must be a validation 400, not a
	 * database "value too long" error surfacing as 409 or 500.
	 */
	@Test
	@DisplayName("external_id of 128 characters is accepted, 129 is 400")
	void externalIdLengthBoundary() {
		ObjectNode atLimit = fixtures.seed("rail");
		atLimit.put("external_id", fixtures.seed("rail").get("external_id").asString() + "x".repeat(128)
			.substring(0, 128 - atLimit.get("external_id").asString().length()));
		assertThat(atLimit.get("external_id").asString()).hasSize(128);
		assertThat(api.post("/transactions", atLimit).status()).isEqualTo(201);

		ObjectNode overLimit = fixtures.seed("rail");
		overLimit.put("external_id", "y".repeat(129));
		assertThat(api.post("/transactions", overLimit).status()).isEqualTo(400);
	}

	/**
	 * Why: broken clients send broken JSON. Every malformed shape must be a 400 problem response, never a 500 or
	 * a stored row with an empty metadata object.
	 */
	@Test
	@DisplayName("Malformed bodies and wrongly shaped metadata are 400, never 500")
	void malformedBodiesAreBadRequests() {
		assertThat(api.post("/transactions", "{\"type\": \"flight\", ").status()).isEqualTo(400);
		assertThat(api.post("/transactions", "[]").status()).isEqualTo(400);

		ObjectNode arrayMetadata = fixtures.seed("flight");
		arrayMetadata.putArray("metadata");
		assertThat(api.post("/transactions", arrayMetadata).status()).isEqualTo(400);

		ObjectNode stringMetadata = fixtures.seed("flight");
		stringMetadata.put("metadata", "BA982");
		assertThat(api.post("/transactions", stringMetadata).status()).isEqualTo(400);

		ObjectNode wrongFieldType = fixtures.seed("hotel");
		((ObjectNode) wrongFieldType.get("metadata")).put("nights", "two");
		assertThat(api.post("/transactions", wrongFieldType).status()).isEqualTo(400);
	}

	/**
	 * Why: invoice bodies have two shapes, so their edge cases differ from transaction bodies: nothing to bill,
	 * both shapes at once, or a null inside the list must all be rejected before any row is locked.
	 */
	@Test
	@DisplayName("Empty, ambiguous or null-containing invoice bodies are 400")
	void invalidInvoiceBodies() {
		String flightId = create("flight");
		assertThat(api.post("/invoices", Map.of()).status()).isEqualTo(400);
		assertThat(api.post("/invoices", Map.of("transaction_ids", List.of())).status()).isEqualTo(400);
		assertThat(api.post("/invoices", Map.of("transaction_id", flightId, "transaction_ids", List.of(flightId)))
			.status()).isEqualTo(400);
		assertThat(api.post("/invoices", "{\"transaction_ids\": [null]}").status()).isEqualTo(400);
	}

	/**
	 * Why: zero nights. check_out equal to check_in is caught by the handler (and by a DB CHECK as a backstop),
	 * so a stay with no nights cannot be stored.
	 */
	@Test
	@DisplayName("Hotel with check_out equal to check_in is 400")
	void zeroNightStayIsRejected() {
		ObjectNode hotel = fixtures.seed("hotel");
		ObjectNode metadata = (ObjectNode) hotel.get("metadata");
		metadata.put("check_out", "2026-03-11");
		metadata.put("nights", 0);
		assertThat(api.post("/transactions", hotel).status()).isEqualTo(400);
	}

	/**
	 * Why: test 4 of the brief proves the snapshot freezes the total. The snapshot must also freeze descriptive
	 * data: a coupon changed in the database after billing must not appear on the invoice.
	 */
	@Test
	@DisplayName("Snapshot freezes metadata too, not only totals")
	void snapshotFreezesMetadata() {
		String flightId = create("flight");
		String invoiceId = invoice(flightId).id();

		jdbc.update("update flight_coupons set flight_number = 'XX999' where transaction_id = ?", uuid(flightId));

		assertThat(api.get("/transactions/" + flightId).body().at("/metadata/coupons/0/flight_number").asString())
			.isEqualTo("XX999");
		assertThat(api.get("/invoices/" + invoiceId).body().at("/lines/0/metadata/coupons/0/flight_number")
			.asString()).isEqualTo("BA982");
	}

	/**
	 * Why: a transaction can sit on several invoices, and "on any invoice" must hold until the last one is gone.
	 * Deleting one of two invoices must not unfreeze the transaction.
	 */
	@Test
	@DisplayName("A transaction stays frozen until every invoice holding it is deleted")
	void frozenUntilLastInvoiceIsDeleted() {
		String flightId = create("flight");
		String first = invoice(flightId).id();
		String second = invoice(flightId).id();
		Map<String, Object> change = Map.of("total", 230);

		api.delete("/invoices/" + first);
		assertThat(api.patch("/transactions/" + flightId, change).status()).isEqualTo(409);

		api.delete("/invoices/" + second);
		assertThat(api.patch("/transactions/" + flightId, change).status()).isEqualTo(200);
	}

	/**
	 * Why: reordering lines on PATCH briefly gives two rows the same (invoice_id, transaction_id) inside one DB
	 * transaction. The unique constraint is DEFERRABLE so this succeeds; a plain constraint would fail here.
	 */
	@Test
	@DisplayName("Invoice PATCH that swaps line order succeeds (deferred unique constraint)")
	void invoicePatchCanSwapLineOrder() {
		String flightId = create("flight");
		String hotelId = create("hotel");
		String invoiceId = invoice(flightId, hotelId).id();

		Api.Response swapped = api.patch("/invoices/" + invoiceId, Map.of("transaction_ids", List.of(hotelId, flightId)));

		assertThat(swapped.status()).isEqualTo(200);
		assertThat(swapped.body().at("/lines/0/transaction_id").asString()).isEqualTo(hotelId);
		assertThat(swapped.body().at("/lines/1/transaction_id").asString()).isEqualTo(flightId);
		assertMoney(swapped.body().at("/totals/grand_total"), "551.70");
	}

	/**
	 * Why: a trip can have many items. A 100-line invoice checks that locking, snapshotting and rollups do not
	 * degrade into per-line round trips that time out, and that the total stays exact at scale.
	 */
	@Test
	@DisplayName("Invoice with 100 transactions is created with an exact total")
	void hundredLineInvoice() {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			ObjectNode fee = fixtures.seed("trip_fee");
			fee.put("external_id", fee.get("external_id").asString() + "-bulk-" + i);
			ids.add(api.post("/transactions", fee).id());
		}

		Api.Response response = invoice(ids.toArray(String[]::new));

		assertThat(response.status()).isEqualTo(201);
		assertThat(response.body().get("lines")).hasSize(100);
		assertMoney(response.body().at("/totals/grand_total"), "2500.00");
	}

	/**
	 * Why: offset pagination skips or repeats rows when rows are inserted mid-walk. Keyset pagination must
	 * return every pre-existing row exactly once even while new rows are being created.
	 */
	@Test
	@DisplayName("Cursor pagination neither skips nor repeats rows when inserts happen mid-walk")
	void paginationIsStableUnderInserts() {
		Set<String> originals = new HashSet<>();
		for (int i = 0; i < 6; i++) {
			originals.add(createFee("1.00", "0.00"));
		}
		Map<String, Integer> seenCount = new HashMap<>();
		String cursor = null;
		int pages = 0;
		do {
			JsonNode page = api.get("/transactions?limit=2" + (cursor == null ? "" : "&cursor=" + cursor)).body();
			page.get("items").forEach(item -> seenCount.merge(item.get("id").asString(), 1, Integer::sum));
			if (++pages == 1) {
				createFee("1.00", "0.00");
				createFee("1.00", "0.00");
			}
			cursor = page.hasNonNull("next_cursor") ? page.get("next_cursor").asString() : null;
		}
		while (cursor != null && !seenCount.keySet().containsAll(originals));

		originals.forEach(id -> assertThat(seenCount.get(id)).as("times %s was returned", id).isEqualTo(1));
	}

	private String createFee(String total, String feeAmount) {
		ObjectNode fee = fixtures.seed("trip_fee");
		fee.put("external_id", fee.get("external_id").asString() + "-" + Arrays.hashCode(new Object[] { total,
				feeAmount, System.nanoTime() }));
		fee.put("total", new BigDecimal(total));
		fee.putArray("fee_lines").addObject().put("name", "Service").put("amount", new BigDecimal(feeAmount));
		Api.Response response = api.post("/transactions", fee);
		assertThat(response.status()).as("%s", response.body()).isEqualTo(201);
		return response.id();
	}

}
