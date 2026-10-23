package com.example.onlineshop.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("docker") @Testcontainers
class OrderApiTest {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired DataSource dataSource;

  @BeforeEach void clean() {
    new JdbcTemplate(dataSource).update("truncate outbox_events, order_idempotency, order_items, orders");
  }

  @Test void createReplayListDetailConfirmAndCancelExposeOnlyPublicIds() throws Exception {
    String body = validOrder("SKU-1");
    String created = mvc.perform(post("/api/v1/customers/customer-1/orders")
        .header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.total.amount").value("199.80"))
        .andExpect(jsonPath("$.internalId").doesNotExist()).andReturn().getResponse().getContentAsString();
    JsonNode order = json.readTree(created);
    String id = order.get("id").asText();

    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "key-1")
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    assertThat(new JdbcTemplate(dataSource).queryForObject("select count(*) from orders", Integer.class)).isOne();
    assertThat(new JdbcTemplate(dataSource).queryForObject("select count(*) from outbox_events", Integer.class)).isOne();

    mvc.perform(get("/api/v1/customers/customer-1/orders"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
    mvc.perform(get("/api/v1/customers/customer-1/orders/{id}", id))
        .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value("customer-1"));
    mvc.perform(post("/api/v1/customers/customer-1/orders/{id}/confirmation", id).header("If-Match", "\"0\""))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.version").value(1));
    mvc.perform(post("/api/v1/customers/customer-1/orders/{id}/cancellation", id).header("If-Match", "\"1\"")
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed mind\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test void distinguishesParseAndSemanticValidationAndRejectsDuplicateSku() throws Exception {
    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "key")
        .contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "key")
        .contentType(MediaType.APPLICATION_JSON).content(validOrder("")))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_VALUE"));
    String duplicate = validOrder("SKU-1").replace("]", ",{\"productId\":\"product-2\",\"sku\":\"SKU-1\",\"name\":\"Case\",\"unitPrice\":{\"amount\":\"10.00\",\"currency\":\"NZD\"},\"quantity\":1}]");
    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "duplicate")
        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
  }

  @Test void hidesMissingAndOtherCustomersAndReportsIdempotencyAndVersionConflicts() throws Exception {
    String first = mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "key")
        .contentType(MediaType.APPLICATION_JSON).content(validOrder("SKU-1"))).andReturn().getResponse().getContentAsString();
    String id = json.readTree(first).get("id").asText();
    mvc.perform(get("/api/v1/customers/customer-2/orders/{id}", id)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/customers/customer-1/orders/{id}", "11111111-1111-1111-1111-111111111111")).andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "key")
        .contentType(MediaType.APPLICATION_JSON).content(validOrder("SKU-2")))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    mvc.perform(post("/api/v1/customers/customer-1/orders/{id}/confirmation", id).header("If-Match", "\"9\""))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
  }

  @Test void publishesOpenApiForApprovedVersionedEndpoints() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/orders']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/orders/{orderId}/confirmation']").exists())
        .andExpect(jsonPath("$.info.description").value(org.hamcrest.Matchers.containsString("development and controlled UAT")))
        .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/orders'].post.responses['201'].headers.Location").exists())
        .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/orders'].post.responses['400'].content['application/problem+json'].schema['$ref']").value(org.hamcrest.Matchers.endsWith("/ProblemDetail")))
        .andExpect(jsonPath("$.components.schemas.CancelRequest.required[0]").value("reason"));
  }

  @Test void malformedHeadersAndFrameworkErrorsUseProblemDetails() throws Exception {
    String id = create("header-errors", validOrder("SKU-1"));
    mvc.perform(post("/api/v1/customers/customer-1/orders/{id}/confirmation", id).header("If-Match", "abc"))
        .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    mvc.perform(put("/api/v1/customers/customer-1/orders/{id}", id))
        .andExpect(status().isMethodNotAllowed()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "media").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test void equivalentMoneyScalesReplayAndValidationIncludesViolationsAndStableType() throws Exception {
    String id = create("money-scale", validOrder("SKU-1"));
    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "money-scale")
        .contentType(MediaType.APPLICATION_JSON).content(validOrder("SKU-1").replace("99.90", "99.9")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key", "invalid")
        .contentType(MediaType.APPLICATION_JSON).content(validOrder("")))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.violations[0].message").isNotEmpty())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.startsWith("https://github.com/exedeehuashan/online-shop/")));
  }

  private String create(String key, String body) throws Exception {
    String response=mvc.perform(post("/api/v1/customers/customer-1/orders").header("Idempotency-Key",key)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).get("id").asText();
  }

  private static String validOrder(String sku) {
    return """
      {"customerName":"Ada","items":[{"productId":"product-1","sku":"%s","name":"Phone","unitPrice":{"amount":"99.90","currency":"NZD"},"quantity":2}],
       "shippingAddress":{"recipient":"Ada","line1":"1 Queen St","city":"Auckland","postalCode":"1010","countryCode":"NZ"}}
      """.formatted(sku);
  }
}
