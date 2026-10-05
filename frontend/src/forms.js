// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 业务与管理表单字段，不向操作人展示SQL或原始JSON。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const fields = {
  shipments: [
    ["reference", "案件编号", "Case reference"],
    ["billOfLading", "提单号", "Bill of lading"],
    ["containerNo", "箱号（4字母7数字）", "Container number"],
    ["agreementId", "批准约定", "Approved agreement", "id", "agreements"],
    ["arrival", "卸船本地日期", "Discharge local date", "date"],
  ],
  agreements: [
    ["reference", "约定版本编号", "Agreement reference"],
    ["customerId", "货主客户", "Customer", "id", "customers"],
    ["carrier", "承运人", "Carrier"],
    ["port", "目的港", "Discharge port"],
    ["containerType", "箱型", "Container type", "select", "containerTypes"],
    ["zone", "港口时区（IANA）", "Terminal timezone"],
    ["validFrom", "适用起日", "Valid from", "date"],
    ["validTo", "适用止日", "Valid until", "date"],
    ["mode", "计费方式", "Charge mode", "select", "mode"],
    ["calendar", "计数日历", "Counting calendar", "select", "calendar"],
    ["includeStart", "计入开始日", "Include start day", "boolean"],
    ["includeEnd", "计入结束日", "Include end day", "boolean"],
    ["holidays", "排除日期（逗号分隔）", "Excluded dates (comma separated)"],
    ["freeDemurrage", "码头内免费天数", "Demurrage free days", "integer"],
    ["freeDetention", "码头外免费天数", "Detention free days", "integer"],
    ["freeCombined", "合并免费天数", "Combined free days", "integer"],
    ["tierDays", "第一费率段天数", "First paid tier days", "integer"],
    [
      "demurrageFirst",
      "码头内第一段（日/元）",
      "Demurrage first (CNY/day)",
      "money",
    ],
    [
      "demurrageAfter",
      "码头内后续（日/元）",
      "Demurrage after (CNY/day)",
      "money",
    ],
    [
      "detentionFirst",
      "码头外第一段（日/元）",
      "Detention first (CNY/day)",
      "money",
    ],
    [
      "detentionAfter",
      "码头外后续（日/元）",
      "Detention after (CNY/day)",
      "money",
    ],
    [
      "combinedFirst",
      "合并第一段（日/元）",
      "Combined first (CNY/day)",
      "money",
    ],
    ["combinedAfter", "合并后续（日/元）", "Combined after (CNY/day)", "money"],
  ],
  customers: [
    ["name", "客户名称", "Customer name"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时留空保留）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "id", "roles"],
    ["departmentId", "部门", "Department", "id", "departments"],
    [
      "customerId",
      "绑定货主（内部人员留空）",
      "Customer binding (staff leave blank)",
      "id",
      "customers",
    ],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "select", "scope"],
    ["permissions", "接口权限", "API permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    [
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ],
    ["position", "排序", "Order", "integer"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission description"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};
/** 命令表单按动作显示必需业务证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function commandFields(action) {
  const note = ["note", "凭据／说明", "Evidence / note", "textarea"];
  if (["gate-out", "return"].includes(action))
    return [["date", "港口本地日期", "Terminal local date", "date"], note];
  if (action === "extend")
    return [
      ["phase", "延期阶段", "Extension phase", "select", "phase"],
      ["days", "增加免费天数", "Additional free days", "integer"],
      note,
    ];
  if (["bill", "revise-bill", "pay"].includes(action))
    return [
      ["amount", "金额（CNY）", "Amount (CNY)", "money"],
      ["reference", "账单／付款凭据编号", "Bill / payment reference"],
      note,
    ];
  return [note];
}
