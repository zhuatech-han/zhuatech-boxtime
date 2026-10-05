// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP/JPA验证真实业务与权限事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoxIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("boxtime.admin-password", () -> password);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, ops, review, finance, finance2, customer, other, boundAll, outside;
  long customerId, otherCustomer, agreementId, id;
  Map<String, Long> roles = new HashMap<>();
  String suffix;

  String key() {
    return UUID.randomUUID().toString();
  }

  MockHttpSession login(String name) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  MvcResult request(MockHttpSession who, String method, String path, Object body) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    b.session(who).with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object body) throws Exception {
    var r = request(who, method, path, body);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object body, int status, String code)
      throws Exception {
    var r = request(who, method, path, body);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  void user(String name, String role, long dep, Long binding) throws Exception {
    var data =
        new HashMap<String, Object>(
            Map.of(
                "username",
                name + suffix,
                "displayName",
                "TEST " + name,
                "password",
                password,
                "roleId",
                roles.get(role),
                "departmentId",
                dep,
                "enabled",
                true));
    if (binding != null) data.put("customerId", binding);
    ok(admin, "POST", "/admin/users", data);
  }

  @BeforeAll
  void setup() throws Exception {
    suffix = key().substring(0, 8);
    admin = login("admin");
    for (var r : ok(admin, "GET", "/admin/roles", null))
      roles.put(r.path("name").asString(), r.path("id").asLong());
    customerId =
        ok(
                admin,
                "POST",
                "/customers",
                Map.of("name", "TEST importer " + suffix, "departmentId", 1, "enabled", true))
            .path("id")
            .asLong();
    otherCustomer =
        ok(
                admin,
                "POST",
                "/customers",
                Map.of("name", "TEST other " + suffix, "departmentId", 1, "enabled", true))
            .path("id")
            .asLong();
    var dep =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST outside " + suffix))
            .path("id")
            .asLong();
    user("ops", "箱务运营", 1, null);
    user("review", "独立复核", 1, null);
    user("finance", "财务", 1, null);
    user("finance2", "财务", 1, null);
    user("customer", "货主客户", 1, customerId);
    user("other", "货主客户", 1, otherCustomer);
    user("bound", "管理员", 1, otherCustomer);
    user("outside", "箱务运营", dep, null);
    ops = login("ops" + suffix);
    review = login("review" + suffix);
    finance = login("finance" + suffix);
    finance2 = login("finance2" + suffix);
    customer = login("customer" + suffix);
    other = login("other" + suffix);
    boundAll = login("bound" + suffix);
    outside = login("outside" + suffix);
  }

  Map<String, Object> agreementInput() {
    var v = new HashMap<String, Object>();
    v.put("requestKey", key());
    v.put("reference", "TEST AG " + key());
    v.put("customerId", customerId);
    v.put("carrier", "TEST carrier");
    v.put("port", "TEST Shanghai");
    v.put("containerType", "20GP");
    v.put("zone", "Asia/Shanghai");
    v.put("mode", "SEPARATE");
    v.put("calendar", "CALENDAR");
    v.put("includeStart", true);
    v.put("includeEnd", false);
    v.put("holidays", "");
    v.put("validFrom", "2026-01-01");
    v.put("validTo", "2026-12-31");
    v.put("freeDemurrage", 3);
    v.put("freeDetention", 2);
    v.put("freeCombined", 5);
    v.put("tierDays", 2);
    v.put("demurrageFirst", "100.00");
    v.put("demurrageAfter", "200.00");
    v.put("detentionFirst", "50.00");
    v.put("detentionAfter", "100.00");
    v.put("combinedFirst", "100.00");
    v.put("combinedAfter", "200.00");
    return v;
  }

  Map<String, Object> shipmentInput() {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "reference",
            "TEST CASE " + key(),
            "billOfLading",
            "TEST BL " + key(),
            "containerNo",
            "TEST1234567",
            "agreementId",
            agreementId,
            "arrival",
            "2026-09-01"));
  }

  @BeforeEach
  void fresh() throws Exception {
    agreementId = ok(ops, "POST", "/agreements", agreementInput()).path("id").asLong();
    ok(
        review,
        "POST",
        "/agreements/" + agreementId + "/commands/approve",
        Map.of("requestKey", key(), "version", 1, "note", "TEST contract independently verified"));
    id = ok(ops, "POST", "/shipments", shipmentInput()).path("shipment").path("id").asLong();
  }

  JsonNode detail() throws Exception {
    return ok(ops, "GET", "/shipments/" + id, null);
  }

  Map<String, Object> cmd() throws Exception {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "version",
            detail().path("shipment").path("version").asLong(),
            "note",
            "TEST evidence"));
  }

  JsonNode act(MockHttpSession who, String action, Map<String, Object> extra) throws Exception {
    var v = cmd();
    v.putAll(extra);
    return ok(who, "POST", "/shipments/" + id + "/commands/" + action, v);
  }

  void returned() throws Exception {
    act(ops, "submit", Map.of());
    act(ops, "gate-out", Map.of("date", "2026-09-06"));
    act(ops, "return", Map.of("date", "2026-09-10"));
  }

  void billed() throws Exception {
    returned();
    act(ops, "calculate", Map.of());
    act(review, "review", Map.of());
    act(finance, "bill", Map.of("amount", "300.00", "reference", "TEST BILL " + key()));
  }

  @Test
  void lifecycleAndReversal() throws Exception {
    billed();
    assertEquals(300, detail().path("calculation").path("total").asDouble());
    act(customer, "confirm", Map.of());
    act(finance, "pay", Map.of("amount", "100.00", "reference", "TEST PAY " + key()));
    var paid = act(finance, "pay", Map.of("amount", "200.00", "reference", "TEST PAY " + key()));
    assertEquals("PAID", paid.path("shipment").path("status").asString());
    var r =
        act(
            finance2,
            "reverse",
            Map.of("paymentId", paid.path("payments").get(0).path("id").asLong()));
    assertEquals("PART_PAID", r.path("shipment").path("status").asString());
    assertEquals(200, r.path("paid").asDouble());
  }

  @Test
  void approvedAgreementFrozen() throws Exception {
    var v = agreementInput();
    v.put("version", 2);
    fail(ops, "PUT", "/agreements/" + agreementId, v, 409, "INVALID_STATE");
    assertEquals(100, detail().path("agreement").path("demurrageFirst").asDouble());
  }

  @Test
  void agreementCannotSelfApprove() throws Exception {
    var d = ok(admin, "POST", "/agreements", agreementInput());
    fail(
        admin,
        "POST",
        "/agreements/" + d.path("id").asLong() + "/commands/approve",
        Map.of("requestKey", key(), "version", 1, "note", "TEST"),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void datesMustBeSequentialAndPast() throws Exception {
    act(ops, "submit", Map.of());
    fail(ops, "POST", "/shipments/" + id + "/commands/return", cmd(), 409, "MISSING_GATE_OUT");
    var bad = cmd();
    bad.put("date", "2026-08-31");
    fail(ops, "POST", "/shipments/" + id + "/commands/gate-out", bad, 400, "INVALID_DATE");
    bad = cmd();
    bad.put("date", "2099-01-01");
    fail(ops, "POST", "/shipments/" + id + "/commands/gate-out", bad, 400, "INVALID_DATE");
  }

  @Test
  void extensionChangesChargesAfterApproval() throws Exception {
    returned();
    act(customer, "extend", Map.of("phase", "DEMURRAGE", "days", 2));
    fail(ops, "POST", "/shipments/" + id + "/commands/calculate", cmd(), 409, "PENDING_EXTENSION");
    act(
        review,
        "approve-extension",
        Map.of("extensionId", detail().path("extensions").get(0).path("id").asLong()));
    assertEquals(100, act(ops, "calculate", Map.of()).path("calculation").path("total").asDouble());
  }

  @Test
  void extensionAndChargeCannotSelfReview() throws Exception {
    returned();
    act(admin, "extend", Map.of("phase", "DETENTION", "days", 1));
    var v = cmd();
    v.put("extensionId", detail().path("extensions").get(0).path("id").asLong());
    fail(
        admin,
        "POST",
        "/shipments/" + id + "/commands/approve-extension",
        v,
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
    act(review, "reject-extension", Map.of("extensionId", v.get("extensionId")));
    act(admin, "calculate", Map.of());
    fail(
        admin,
        "POST",
        "/shipments/" + id + "/commands/review",
        cmd(),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void exactRetryAndPayloadReuse() throws Exception {
    var v = cmd();
    var first = ok(ops, "POST", "/shipments/" + id + "/commands/submit", v);
    assertEquals(first, ok(ops, "POST", "/shipments/" + id + "/commands/submit", v));
    v.put("note", "changed");
    fail(ops, "POST", "/shipments/" + id + "/commands/submit", v, 409, "REQUEST_KEY_REUSED");
  }

  @Test
  void staleWriteRollsBack() throws Exception {
    var v = cmd();
    act(ops, "submit", Map.of());
    var before = detail();
    fail(ops, "POST", "/shipments/" + id + "/commands/cancel", v, 409, "STALE_VERSION");
    assertEquals(before, detail());
  }

  @Test
  void boundAllCannotEscalate() throws Exception {
    fail(boundAll, "GET", "/shipments/" + id, null, 403, "OUT_OF_SCOPE");
    fail(boundAll, "GET", "/admin/users", null, 403, "STAFF_ONLY");
    fail(boundAll, "GET", "/audit", null, 403, "STAFF_ONLY");
    assertEquals(0, ok(boundAll, "GET", "/shipments", null).path("total").asInt());
  }

  @Test
  void scopesAlsoProtectExportAndStats() throws Exception {
    fail(outside, "GET", "/shipments/" + id, null, 403, "OUT_OF_SCOPE");
    fail(other, "GET", "/shipments/" + id + "/report.json", null, 403, "OUT_OF_SCOPE");
    assertEquals(0, ok(other, "GET", "/dashboard", null).path("shipments").asInt());
  }

  @Test
  void customerCannotOperateInternalTasks() throws Exception {
    fail(customer, "POST", "/shipments/" + id + "/commands/submit", cmd(), 403, "FORBIDDEN");
    fail(customer, "GET", "/agreements/" + agreementId, null, 403, "FORBIDDEN");
  }

  @Test
  void sessionAndCsrfRequired() throws Exception {
    assertEquals(401, mvc.perform(get("/api/shipments")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/shipments")
                    .session(ops)
                    .contentType("application/json")
                    .content(json.writeValueAsString(shipmentInput())))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void liveDuplicateButCancelledCanReopen() throws Exception {
    var v = shipmentInput();
    v.put("billOfLading", detail().path("shipment").path("billOfLading").asString());
    fail(ops, "POST", "/shipments", v, 409, "DUPLICATE_CONTAINER_CASE");
    act(ops, "cancel", Map.of());
    ok(ops, "POST", "/shipments", v);
  }

  @Test
  void decimalAndIntegerValidation() throws Exception {
    var v = agreementInput();
    v.put("demurrageFirst", "0.001");
    fail(ops, "POST", "/agreements", v, 400, "INVALID_AMOUNT");
    v = agreementInput();
    v.put("freeDemurrage", 1.5);
    fail(ops, "POST", "/agreements", v, 400, "INVALID_INPUT");
  }

  @Test
  void disputeMustBeRevisedBeforeAccept() throws Exception {
    billed();
    act(customer, "dispute", Map.of());
    fail(finance, "POST", "/shipments/" + id + "/commands/pay", cmd(), 409, "INVALID_STATE");
    act(admin, "revise-bill", Map.of("amount", "280.00", "reference", "TEST adjusted " + key()));
    fail(
        admin,
        "POST",
        "/shipments/" + id + "/commands/confirm",
        cmd(),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
    act(customer, "confirm", Map.of());
    assertEquals("ACCEPTED", detail().path("shipment").path("status").asString());
  }

  @Test
  void overpayAndSelfReversalRejected() throws Exception {
    billed();
    act(customer, "confirm", Map.of());
    var bad = cmd();
    bad.put("amount", "300.01");
    bad.put("reference", key());
    fail(finance, "POST", "/shipments/" + id + "/commands/pay", bad, 409, "OVERPAYMENT");
    var result = act(finance, "pay", Map.of("amount", "300.00", "reference", key()));
    var v = cmd();
    v.put("paymentId", result.path("payments").get(0).path("id").asLong());
    fail(
        finance,
        "POST",
        "/shipments/" + id + "/commands/reverse",
        v,
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void concurrentPaymentCommitsOnce() throws Exception {
    billed();
    act(customer, "confirm", Map.of());
    var one = cmd();
    one.putAll(Map.of("amount", "300.00", "reference", key()));
    var two = new HashMap<String, Object>(one);
    two.put("requestKey", key());
    two.put("reference", key());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                start.await();
                return request(finance, "POST", "/shipments/" + id + "/commands/pay", one)
                    .getResponse()
                    .getStatus();
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return request(finance2, "POST", "/shipments/" + id + "/commands/pay", two)
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      var results = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
      assertTrue(results.contains(200));
      assertTrue(results.contains(409));
    }
    assertEquals(1, detail().path("payments").size());
  }

  @Test
  void lastUnboundAdminProtected() throws Exception {
    JsonNode current = null;
    for (var a : ok(admin, "GET", "/admin/users", null))
      if (a.path("username").asString().equals("admin")) current = a;
    var v = json.convertValue(current, Map.class);
    v.put("customerId", customerId);
    fail(admin, "PUT", "/admin/users/" + current.path("id").asLong(), v, 409, "LAST_ADMIN");
    assertEquals("admin", ok(admin, "GET", "/auth/me", null).path("username").asString());
  }

  @Test
  void previewDoesNotBill() throws Exception {
    act(ops, "submit", Map.of());
    assertFalse(
        ok(customer, "GET", "/shipments/" + id + "/preview?asOf=2026-09-10", null)
            .path("final")
            .asBoolean());
    assertEquals("ACTIVE", detail().path("shipment").path("status").asString());
    fail(ops, "GET", "/shipments?page=-1", null, 400, "INVALID_PAGE");
  }

  @Test
  void referencesProtected() throws Exception {
    fail(admin, "DELETE", "/customers/" + customerId, null, 409, "CONFLICT");
    var d = ok(admin, "GET", "/admin/dictionaries", null).get(0);
    fail(admin, "DELETE", "/admin/dictionaries/" + d.path("id").asLong(), null, 409, "CONFLICT");
  }
}
