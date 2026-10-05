// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 界面状态与操作对应服务端业务，不把按钮隐藏作为权限检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  APPROVED: ["已批准", "Approved"],
  ACTIVE: ["在用箱", "In use"],
  RETURNED: ["已还箱", "Returned"],
  CALCULATED: ["待独立复核", "Awaiting review"],
  REVIEWED: ["核算已复核", "Reviewed"],
  BILLED: ["待客户确认", "Awaiting confirmation"],
  DISPUTED: ["账单争议", "Disputed"],
  ACCEPTED: ["已确认待付", "Accepted"],
  PART_PAID: ["部分付款", "Part paid"],
  PAID: ["已结清", "Paid"],
  CANCELLED: ["已作废", "Cancelled"],
  PENDING: ["待审批", "Pending"],
  REJECTED: ["已驳回", "Rejected"],
  POSTED: ["已记账", "Posted"],
  REVERSED: ["已冲正", "Reversed"],
};
/** 案件的下一步操作，独立性还由服务端核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(s, permissions) {
  const available = {
    DRAFT: [
      ["submit", "shipment.write"],
      ["cancel", "shipment.write"],
    ],
    ACTIVE: [
      ...(!s.gateOut
        ? [["gate-out", "shipment.write"]]
        : [["return", "shipment.write"]]),
      ["extend", "extension.write"],
      ["cancel", "shipment.write"],
    ],
    RETURNED: [
      ["extend", "extension.write"],
      ["calculate", "charge.calculate"],
    ],
    CALCULATED: [["review", "charge.review"]],
    REVIEWED: [["bill", "bill.write"]],
    BILLED: [
      ["confirm", "bill.confirm"],
      ["dispute", "bill.confirm"],
    ],
    DISPUTED: [["revise-bill", "bill.write"]],
    ACCEPTED: [["pay", "payment.write"]],
    PART_PAID: [["pay", "payment.write"]],
    PAID: [],
    CANCELLED: [],
  };
  return (available[s.status] || [])
    .filter(([, p]) => permissions.includes(p))
    .map(([a]) => a);
}
/** 表单空选项转null，整数保留类型，钱以十进制字符串传输。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, fields) {
  return Object.fromEntries(
    fields.map(([k, , , type]) => [
      k,
      type === "integer" || type === "id"
        ? form[k] === "" || form[k] == null
          ? null
          : Number(form[k])
        : type === "boolean"
          ? Boolean(form[k])
          : type === "permissions"
            ? [...(form[k] || [])]
            : (form[k] ?? ""),
    ]),
  );
}
/** 港口日期用于预估日期初值，与浏览器时区分离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localDate(zone, now = new Date()) {
  const p = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  return ["year", "month", "day"]
    .map((k) => p.find((v) => v.type === k).value)
    .join("-");
}
