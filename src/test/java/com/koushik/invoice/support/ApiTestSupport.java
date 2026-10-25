package com.koushik.invoice.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.koushik.invoice.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

	/** Reads response numbers as BigDecimal so assertions see the exact scale the server wrote (685.70). */
	protected static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.build();

	@LocalServerPort
	private int port;

	@Autowired
	protected JdbcTemplate jdbc;

	protected Api api;

	protected Fixtures fixtures;

	@BeforeEach
	void setUpClient() {
		api = new Api(port, JSON);
		fixtures = new Fixtures(JSON);
	}

	protected String create(String seedName) {
		Api.Response response = api.post("/transactions", fixtures.seed(seedName));
		assertThat(response.status()).as("create %s: %s", seedName, response.body()).isEqualTo(201);
		return response.id();
	}

	protected Api.Response invoice(String... transactionIds) {
		return api.post("/invoices", Map.of("transaction_ids", List.of(transactionIds)));
	}

	protected int rowsWithExternalId(String externalId) {
		return jdbc.queryForObject("select count(*) from transactions where external_id = ?", Integer.class,
				externalId);
	}

	protected static UUID uuid(String id) {
		return UUID.fromString(id);
	}

	/** Asserts value and scale: 220.00, not 220 or 220.0. */
	protected static void assertMoney(JsonNode node, String expected) {
		assertThat(node).as("money field").isNotNull();
		assertThat(node.decimalValue()).isEqualTo(new BigDecimal(expected));
	}

}
