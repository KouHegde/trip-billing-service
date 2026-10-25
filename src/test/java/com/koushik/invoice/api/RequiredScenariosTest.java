package com.koushik.invoice.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import com.koushik.invoice.support.Api;
import com.koushik.invoice.support.ApiTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** The ten scenarios the brief lists as required, numbered as in the brief. */
class RequiredScenariosTest extends ApiTestSupport {

	@Test
	@DisplayName("1. Create flight, hotel, rail and both Navan fee kinds; GET returns their type-specific data")
	void createsEveryTypeAndRoundTripsMetadata() {
		JsonNode flight = api.get("/transactions/" + create("flight")).body();
		assertThat(flight.get("type").asString()).isEqualTo("flight");
		JsonNode coupon = flight.at("/metadata/coupons/0");
		assertThat(coupon.get("flight_number").asString()).isEqualTo("BA982");
		assertThat(coupon.get("from").asString()).isEqualTo("TXL");
		assertThat(flight.at("/metadata/origin").asString()).isEqualTo("TXL");
		assertMoney(flight.at("/tax_lines/0/amount"), "15.00");
		assertThat(flight.at("/fee_lines/0/name").asString()).isEqualTo("YQ");

		JsonNode hotel = api.get("/transactions/" + create("hotel")).body();
		assertThat(hotel.at("/metadata/name").asString()).isEqualTo("Hotel Mitte London");
		assertThat(hotel.at("/metadata/address/street").asString()).isEqualTo("12 Kingsway");
		assertThat(hotel.at("/metadata/address/country").asString()).isEqualTo("GB");
		assertThat(hotel.at("/tax_lines/0/rate").decimalValue()).isEqualByComparingTo("0.07");

		JsonNode rail = api.get("/transactions/" + create("rail")).body();
		assertThat(rail.at("/metadata/legs")).hasSize(2);
		assertThat(rail.at("/metadata/legs/1/origin").asString()).isEqualTo("Lille Europe");
		assertThat(rail.at("/metadata/legs/0/class").asString()).isEqualTo("standard");

		JsonNode tripFee = api.get("/transactions/" + create("trip_fee")).body();
		assertThat(tripFee.at("/metadata/fee_kind").asString()).isEqualTo("trip_fee");
		JsonNode agentFee = api.get("/transactions/" + create("agent_call_fee")).body();
		assertThat(agentFee.at("/metadata/fee_kind").asString()).isEqualTo("agent_call_fee");
	}

	@Test
	@DisplayName("2. Duplicate external_id: second POST returns 409 with the existing id; one row stored")
	void duplicateExternalIdIsRejected() {
		ObjectNode flight = fixtures.seed("flight");
		Api.Response first = api.post("/transactions", flight);
		assertThat(first.status()).isEqualTo(201);

		Api.Response second = api.post("/transactions", fixtures.seed("duplicate_flight"));

		assertThat(second.status()).isEqualTo(409);
		assertThat(second.body().get("existing_id").asString()).isEqualTo(first.id());
		assertThat(rowsWithExternalId(flight.get("external_id").asString())).isEqualTo(1);
	}

	@Test
	@DisplayName("3. Trip invoice of flight + hotel + rail + both Navan fees matches expected-invoice.json")
	void tripInvoiceMatchesExpectedTotals() {
		JsonNode expected = fixtures.expectedInvoice();
		Api.Response response = invoice(create("flight"), create("hotel"), create("rail"), create("trip_fee"),
				create("agent_call_fee"));

		assertThat(response.status()).isEqualTo(201);
		JsonNode invoice = response.body();
		assertThat(invoice.get("currency").asString()).isEqualTo(expected.get("currency").asString());
		assertThat(invoice.get("lines")).hasSizeGreaterThanOrEqualTo(expected.get("line_count_minimum").asInt());
		assertMoney(invoice.at("/totals/grand_total"), "685.70");
		assertMoney(invoice.at("/totals/by_type/flight"), "220.00");
		assertMoney(invoice.at("/totals/by_type/hotel"), "331.70");
		assertMoney(invoice.at("/totals/by_type/rail"), "94.00");
		assertMoney(invoice.at("/totals/by_fee_kind/trip_fee"), "25.00");
		assertMoney(invoice.at("/totals/by_fee_kind/agent_call_fee"), "15.00");

		assertThat(invoice.get("tax_rollup")).hasSize(2);
		assertThat(invoice.at("/tax_rollup/0/name").asString()).isEqualTo("Airport tax");
		assertMoney(invoice.at("/tax_rollup/0/amount"), "15.00");
		assertThat(invoice.at("/tax_rollup/1/name").asString()).isEqualTo("VAT");
		assertThat(invoice.at("/tax_rollup/1/rate").decimalValue()).isEqualByComparingTo("0.07");
		assertMoney(invoice.at("/tax_rollup/1/amount"), "21.70");
		assertThat(invoice.get("fee_rollup")).hasSize(2);
		assertThat(invoice.at("/fee_rollup/0/name").asString()).isEqualTo("YQ");
		assertMoney(invoice.at("/fee_rollup/0/amount"), "25.00");
		assertThat(invoice.at("/fee_rollup/1/name").asString()).isEqualTo("Booking fee");
		assertMoney(invoice.at("/fee_rollup/1/amount"), "4.00");

		assertThat(invoice.at("/lines/0/metadata/coupons/0/flight_number").asString()).isEqualTo("BA982");
		assertThat(invoice.at("/lines/1/metadata/address/city").asString()).isEqualTo("London");
	}

	@Test
	@DisplayName("4. Snapshot: changing the stored flight total does not change an existing invoice")
	void invoiceIsAFrozenSnapshot() {
		String flightId = create("flight");
		String invoiceId = invoice(flightId, create("hotel"), create("rail"), create("trip_fee"),
				create("agent_call_fee"))
			.id();

		// PATCH is refused once invoiced (test 7), so change the stored row directly.
		jdbc.update("update transactions set total = 999.99 where id = ?", uuid(flightId));
		assertMoney(api.get("/transactions/" + flightId).body().get("total"), "999.99");

		JsonNode invoice = api.get("/invoices/" + invoiceId).body();
		assertMoney(invoice.at("/lines/0/total"), "220.00");
		assertMoney(invoice.at("/totals/by_type/flight"), "220.00");
		assertMoney(invoice.at("/totals/grand_total"), "685.70");
	}

	@Test
	@DisplayName("5. Mixed currency: invoicing EUR + USD returns 409 and stores nothing")
	void mixedCurrencyIsRejected() {
		ObjectNode usdHotel = fixtures.seed("hotel");
		usdHotel.put("currency", "USD");
		String usdId = api.post("/transactions", usdHotel).id();
		int invoicesBefore = jdbc.queryForObject("select count(*) from invoices", Integer.class);

		Api.Response response = invoice(create("flight"), usdId);

		assertThat(response.status()).isEqualTo(409);
		assertThat(jdbc.queryForObject("select count(*) from invoices", Integer.class)).isEqualTo(invoicesBefore);
	}

	@Test
	@DisplayName("6. Concurrent duplicate create: overlapping POSTs with one external_id leave exactly one row")
	void concurrentDuplicateCreatesOneRow() throws Exception {
		int threads = 8;
		ObjectNode flight = fixtures.seed("flight");
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
			Callable<Integer> post = () -> {
				start.await();
				return api.post("/transactions", flight).status();
			};
			for (int i = 0; i < threads; i++) {
				statuses.add(pool.submit(post));
			}
			start.countDown();
			List<Integer> codes = new ArrayList<>();
			for (Future<Integer> status : statuses) {
				codes.add(status.get());
			}
			assertThat(codes).containsOnly(201, 409);
			assertThat(codes).filteredOn(code -> code == 201).hasSize(1);
		}
		assertThat(rowsWithExternalId(flight.get("external_id").asString())).isEqualTo(1);
	}

	@Test
	@DisplayName("7. PATCH of an invoiced transaction returns 409; invoice totals unchanged")
	void patchOfInvoicedTransactionIsRejected() {
		String flightId = create("flight");
		String invoiceId = invoice(flightId).id();

		Api.Response patch = api.patch("/transactions/" + flightId, Map.of("total", new BigDecimal("500.00")));

		assertThat(patch.status()).isEqualTo(409);
		assertMoney(api.get("/transactions/" + flightId).body().get("total"), "220.00");
		assertMoney(api.get("/invoices/" + invoiceId).body().at("/totals/grand_total"), "220.00");
	}

	@Test
	@DisplayName("8. DELETE of an invoiced transaction returns 409; the row still exists")
	void deleteOfInvoicedTransactionIsRejected() {
		String flightId = create("flight");
		invoice(flightId);

		assertThat(api.delete("/transactions/" + flightId).status()).isEqualTo(409);
		assertThat(api.get("/transactions/" + flightId).status()).isEqualTo(200);
	}

	@Test
	@DisplayName("9. DELETE invoice removes it; its transactions can still be fetched")
	void deleteInvoiceKeepsTransactions() {
		String flightId = create("flight");
		String hotelId = create("hotel");
		String invoiceId = invoice(flightId, hotelId).id();

		assertThat(api.delete("/invoices/" + invoiceId).status()).isEqualTo(204);

		assertThat(api.get("/invoices/" + invoiceId).status()).isEqualTo(404);
		assertThat(api.get("/transactions/" + flightId).status()).isEqualTo(200);
		assertThat(api.get("/transactions/" + hotelId).status()).isEqualTo(200);
	}

	@Test
	@DisplayName("10. GET /transactions is bounded: default page of 50, limit=1000 rejected")
	void transactionListIsBounded() {
		for (int i = 0; i < 51; i++) {
			ObjectNode fee = fixtures.seed("trip_fee");
			fee.put("external_id", fee.get("external_id").asString() + "-" + i);
			assertThat(api.post("/transactions", fee).status()).isEqualTo(201);
		}

		JsonNode page = api.get("/transactions").body();
		assertThat(page.get("items")).hasSize(50);
		assertThat(page.get("limit").asInt()).isEqualTo(50);
		assertThat(page.get("next_cursor").asString()).isNotBlank();

		assertThat(api.get("/transactions?limit=1000").status()).isEqualTo(400);
		assertThat(api.get("/transactions?limit=100").body().get("items").size()).isLessThanOrEqualTo(100);
	}

}
