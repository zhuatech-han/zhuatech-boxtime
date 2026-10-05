// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { actions, payload, localDate } from "./domain.js";
test("customer has no internal actions even in active container state", () =>
  assert.deepEqual(
    actions({ status: "ACTIVE", gateOut: null }, [
      "extension.write",
      "bill.confirm",
    ]),
    ["extend"],
  ));
test("gate out must precede return and paid case cannot pay again", () => {
  assert.deepEqual(
    actions({ status: "ACTIVE", gateOut: "2026-01-01" }, ["shipment.write"]),
    ["return", "cancel"],
  );
  assert.deepEqual(actions({ status: "PAID" }, ["payment.write"]), []);
});
test("optional customer id stays null and money avoids floating conversion", () => {
  const result = payload(
    { customerId: "", amount: "12345678.91", version: "2" },
    [
      ["customerId", "", "", "id"],
      ["amount", "", "", "money"],
      ["version", "", "", "integer"],
    ],
  );
  assert.deepEqual(result, {
    customerId: null,
    amount: "12345678.91",
    version: 2,
  });
});
test("terminal local date differs around UTC midnight", () => {
  const now = new Date("2026-10-05T20:00:00Z");
  assert.equal(localDate("Asia/Shanghai", now), "2026-10-06");
  assert.equal(localDate("America/New_York", now), "2026-10-05");
});
