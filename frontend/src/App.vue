<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Anchor,
  Boxes,
  FileCheck2,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Search,
  Plus,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  ExternalLink,
  Clock3,
  CheckCircle2,
  AlertCircle,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { states, actions, payload, localDate } from "./domain.js";
import { fields, commandFields } from "./forms.js";
const lang = ref(localStorage.getItem("boxtime-language") || "zh"),
  me = ref(null),
  view = ref("shipments"),
  busy = ref(false),
  notice = ref(""),
  error = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  selected = ref(null),
  detail = ref(null),
  stats = ref({ states: {} }),
  modal = ref(null),
  form = ref({}),
  directories = ref({}),
  preview = ref(null),
  asOf = ref(""),
  contact = ref(false),
  dayPage = ref(0);
const t = (zh, en) => (lang.value === "zh" ? zh : en);
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("boxtime-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
const permissions = computed(() => me.value?.permissions || []),
  can = (p) => permissions.value.includes(p);
const adminTypes = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const labels = {
  shipments: ["箱务工作台", "Container desk"],
  agreements: ["计费约定", "Agreements"],
  customers: ["客户档案", "Customers"],
  dashboard: ["费用统计", "Statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["箱型字典", "Container types"],
  settings: ["系统参数", "Settings"],
};
const title = computed(() =>
  t(...(labels[view.value] || ["BoxTime", "BoxTime"])),
);
const icons = {
  shipments: Boxes,
  agreements: FileCheck2,
  customers: Users,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
  departments: Users,
  menus: Settings,
  permissions: ShieldCheck,
  dictionaries: Boxes,
  settings: Settings,
};
const statusName = (s) => (states[s] ? t(...states[s]) : s);
const actionNames = {
  submit: ["启用箱务", "Activate"],
  cancel: ["作废", "Cancel"],
  "gate-out": ["登记提箱", "Record gate out"],
  return: ["登记还箱", "Record empty return"],
  extend: ["申请免箱延期", "Request extension"],
  calculate: ["生成最终核算", "Calculate final charges"],
  review: ["独立复核", "Independent review"],
  bill: ["录入承运账单", "Record carrier bill"],
  "revise-bill": ["修订争议账单", "Revise disputed bill"],
  confirm: ["确认账单", "Accept bill"],
  dispute: ["提出争议", "Dispute"],
  pay: ["记录线下付款", "Record offline payment"],
  reverse: ["独立冲正", "Reverse payment"],
  approve: ["批准约定", "Approve agreement"],
  "approve-extension": ["批准延期", "Approve extension"],
  "reject-extension": ["驳回延期", "Reject extension"],
};
const commandName = (a) => t(...(actionNames[a] || [a, a]));
const money = (n) =>
  new Intl.NumberFormat(lang.value === "zh" ? "zh-CN" : "en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(n || 0);
const phaseName = (p) =>
  ({
    DEMURRAGE: t("码头内", "Demurrage"),
    DETENTION: t("码头外", "Detention"),
    COMBINED: t("合并计费", "Combined"),
  })[p] || p;
const failures = {
  OUT_OF_SCOPE: ["超出当前账号的数据范围", "Outside your data scope"],
  STAFF_ONLY: ["此操作限内部岗位", "Staff only"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired. Sign in again"],
  STALE_VERSION: [
    "记录已变化，请刷新后重试",
    "Record changed. Refresh before retrying",
  ],
  INDEPENDENT_REVIEW_REQUIRED: [
    "须由另一位授权人员完成",
    "A different authorized person must perform this action",
  ],
  INVALID_STATE: [
    "当前状态不允许此操作",
    "Action is unavailable in this state",
  ],
  INVALID_DATE: [
    "日期超出合同范围、晚于今天或顺序不正确",
    "Date is out of contract range, future, or out of sequence",
  ],
  INVALID_RULE: ["计费约定或天数无效", "Invalid charging rule or days"],
  INVALID_AMOUNT: [
    "金额须非负、最多两位小数且不超过一亿元",
    "Amount must be nonnegative with at most two decimal places",
  ],
  OVERPAYMENT: ["付款超过未付余额", "Payment exceeds outstanding balance"],
  WEAK_PASSWORD: [
    "密码至少12位并含大小写字母和数字",
    "Use at least 12 characters with upper/lowercase letters and digits",
  ],
  LAST_ADMIN: [
    "须保留一个未绑定客户的启用管理员",
    "Keep an enabled administrator without a customer binding",
  ],
  PENDING_EXTENSION: [
    "先处理待审批的延期申请",
    "Resolve pending extensions first",
  ],
  EXTENSION_EXISTS: [
    "此阶段已有延期申请或批准记录",
    "An extension already exists for this phase",
  ],
  CONFLICT: [
    "编号重复或记录正在被引用",
    "Duplicate reference or referenced record",
  ],
  INVALID_CONTAINER_NO: [
    "箱号须为4位字母和7位数字",
    "Container number requires four letters and seven digits",
  ],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect username or password"],
  INVALID_INPUT: [
    "请检查必填项及输入格式",
    "Check required fields and formats",
  ],
  LOGIN_THROTTLED: [
    "登录尝试过多，稍后重试",
    "Too many sign-in attempts. Try later",
  ],
};
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") {
      me.value = null;
      detail.value = null;
      rows.value = [];
      modal.value = null;
    }
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  options.value = await api("/options");
  if (can("admin") && !me.value.customerId) {
    for (const k of ["roles", "permissions", "departments"])
      directories.value[k] = await api("/admin/" + k);
  }
}
async function load() {
  detail.value = null;
  selected.value = null;
  preview.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (["shipments", "agreements"].includes(view.value)) {
    const result = await api(
      "/" +
        view.value +
        "?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = result.items;
    total.value = result.total;
  } else {
    let all =
      view.value === "customers"
        ? options.value.customers
        : await api(view.value === "audit" ? "/audit" : "/admin/" + view.value);
    all = all.filter((v) =>
      Object.values(v).some(
        (x) =>
          typeof x === "string" &&
          x.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort(
      sort.value === "reference"
        ? (a, b) =>
            String(a.name || a.username || a.code).localeCompare(
              String(b.name || b.username || b.code),
            )
        : (a, b) => b.id - a.id,
    );
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  await run(load);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    me.value = null;
    detail.value = null;
    directories.value = {};
    options.value = {};
    rows.value = [];
    resetCsrf();
  });
}
async function open(row) {
  await run(async () => {
    selected.value = row.id;
    detail.value = await api("/" + view.value + "/" + row.id);
    preview.value = null;
    dayPage.value = 0;
    if (view.value === "shipments")
      asOf.value = localDate(detail.value.agreement.zone);
  });
}
const currentShipment = computed(() => detail.value?.shipment);
const currentActions = computed(() =>
  currentShipment.value
    ? actions(currentShipment.value, permissions.value)
    : [],
);
const calculated = computed(
  () => preview.value?.calculation || detail.value?.calculation,
);
const dailyRows = computed(() =>
  (calculated.value?.phases || [])
    .flatMap((p) => p.days.map((d) => ({ ...d, phase: p.phase })))
    .slice(dayPage.value * 30, dayPage.value * 30 + 30),
);
const dailyTotal = computed(() =>
  (calculated.value?.phases || []).reduce((n, p) => n + p.days.length, 0),
);
const modalFields = computed(() =>
  modal.value?.kind === "command"
    ? commandFields(modal.value.action)
    : modal.value?.kind === "password"
      ? [
          ["oldPassword", "原密码", "Current password", "password"],
          ["newPassword", "新密码", "New password", "password"],
        ]
      : fields[modal.value?.type] || [],
);
function choices(key) {
  if (key === "mode")
    return [
      { value: "SEPARATE", label: t("码头内外分开", "Separate") },
      { value: "COMBINED", label: t("合并计费", "Combined") },
    ];
  if (key === "calendar")
    return [
      { value: "CALENDAR", label: t("日历日", "Calendar days") },
      { value: "WORKDAY", label: t("周一至周五", "Monday to Friday") },
    ];
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((v) => ({
      value: v,
      label: {
        ALL: t("全部", "All"),
        DEPARTMENT: t("本部门", "Department"),
        SELF: t("本人创建", "Created by self"),
      }[v],
    }));
  if (key === "phase")
    return (
      detail.value?.agreement?.mode === "COMBINED"
        ? ["COMBINED"]
        : ["DEMURRAGE", "DETENTION"]
    ).map((v) => ({ value: v, label: phaseName(v) }));
  let records = directories.value[key] || options.value[key] || [];
  if (key === "agreements")
    records = records.filter((a) => a.status === "APPROVED");
  return records.map((v) => ({
    value: key === "permissions" || key === "containerTypes" ? v.code : v.id,
    label:
      key === "agreements"
        ? v.reference + " · " + v.carrier
        : lang.value === "en" && v.nameEn
          ? v.nameEn
          : v.name || v.displayName || v.code,
  }));
}
function edit(type, row = null) {
  error.value = "";
  modal.value = { kind: "edit", type, id: row?.id };
  form.value = { ...row };
  if (!row) {
    form.value = {
      enabled: true,
      departmentId: me.value.departmentId,
      scope: "DEPARTMENT",
      permissions: [],
      type: "container",
      zone: "Asia/Shanghai",
      mode: "SEPARATE",
      calendar: "CALENDAR",
      includeStart: true,
      includeEnd: false,
      freeDemurrage: 0,
      freeDetention: 0,
      freeCombined: 0,
      tierDays: 1,
      demurrageFirst: "0.00",
      demurrageAfter: "0.00",
      detentionFirst: "0.00",
      detentionAfter: "0.00",
      combinedFirst: "0.00",
      combinedAfter: "0.00",
    };
  }
  if (type === "users") form.value.password = "";
}
function command(action, extra = {}) {
  error.value = "";
  modal.value = {
    kind: "command",
    type: view.value,
    action,
    id: selected.value,
    extra,
  };
  form.value = {
    note: "",
    date: localDate(detail.value.agreement?.zone || "Asia/Shanghai"),
    phase:
      detail.value.agreement?.mode === "COMBINED" ? "COMBINED" : "DEMURRAGE",
    days: 1,
    amount: currentShipment.value?.billAmount ?? calculated.value?.total ?? "",
    reference: "",
  };
}
function remove(type, row) {
  modal.value = { kind: "delete", type, id: row.id };
  form.value = {};
}
async function save() {
  await run(async () => {
    const m = modal.value;
    let body = payload(form.value, modalFields.value);
    if (m.kind === "password") {
      await api("/auth/password", "POST", body);
      me.value = null;
      modal.value = null;
      return;
    }
    if (m.kind === "delete") {
      await api(
        "/" +
          (adminTypes.includes(m.type) ? "admin/" : "") +
          m.type +
          "/" +
          m.id,
        "DELETE",
        {},
      );
    } else if (m.kind === "command") {
      body = {
        ...body,
        ...m.extra,
        requestKey: crypto.randomUUID(),
        version: currentShipment.value?.version ?? detail.value.version,
      };
      detail.value = await api(
        "/" + m.type + "/" + m.id + "/commands/" + m.action,
        "POST",
        body,
      );
    } else {
      if (["shipments", "agreements"].includes(m.type))
        body = {
          ...body,
          requestKey: crypto.randomUUID(),
          version: form.value.version ?? null,
        };
      await api(
        "/" +
          (adminTypes.includes(m.type) ? "admin/" : "") +
          m.type +
          (m.id ? "/" + m.id : ""),
        m.id ? "PUT" : "POST",
        body,
      );
    }
    modal.value = null;
    notice.value = t("已保存", "Saved");
    await loadOptions();
    if (m.kind === "command") {
      if (view.value === "shipments")
        detail.value = await api("/shipments/" + m.id);
      else detail.value = await api("/agreements/" + m.id);
    } else await load();
  });
}
async function forecast() {
  await run(async () => {
    preview.value = await api(
      "/shipments/" + selected.value + "/preview?asOf=" + asOf.value,
    );
    dayPage.value = 0;
  });
}
function formatTime(value) {
  return new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
    timeZone: options.value.timezone || "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date(value));
}
function fieldValue(row, key) {
  if (key === "createdAt") return formatTime(row[key]);
  if (key === "status") return statusName(row[key]);
  if (["scope", "mode", "calendar"].includes(key))
    return choices(key).find((c) => c.value === row[key])?.label || row[key];
  if (key === "enabled")
    return row[key] ? t("启用", "Enabled") : t("停用", "Disabled");
  if (key === "permissions")
    return row.permissions.length + t(" 项权限", " permissions");
  if (key === "roleId")
    return (
      directories.value.roles?.find((r) => r.id === row[key])?.name || row[key]
    );
  if (key === "customerId")
    return options.value.customers?.find((r) => r.id === row[key])?.name || "—";
  if (key === "departmentId")
    return (
      directories.value.departments?.find((r) => r.id === row[key])?.name ||
      row[key]
    );
  return row[key] ?? "—";
}
const columns = computed(
  () =>
    ({
      shipments: [
        ["reference", "案件编号", "Reference"],
        ["containerNo", "箱号", "Container"],
        ["billOfLading", "提单号", "Bill of lading"],
        ["arrival", "卸船日期", "Discharge"],
        ["status", "进度", "Status"],
      ],
      agreements: [
        ["reference", "约定版本", "Agreement"],
        ["carrier", "承运人", "Carrier"],
        ["port", "目的港", "Port"],
        ["containerType", "箱型", "Type"],
        ["status", "状态", "Status"],
      ],
      audit: [
        ["createdAt", "操作时间", "Time"],
        ["actor", "操作人", "Actor"],
        ["action", "操作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || [])
      .filter((f) => !["password", "newPassword", "oldPassword"].includes(f[0]))
      .slice(0, 5),
);
const canCreate = computed(() =>
  view.value === "shipments"
    ? can("shipment.write") && !me.value?.customerId
    : view.value === "agreements"
      ? can("agreement.write") && !me.value?.customerId
      : view.value === "customers"
        ? can("catalog.write")
        : can("admin") &&
          ["users", "roles", "departments", "dictionaries"].includes(
            view.value,
          ),
);
const canEdit = computed(
  () =>
    (can("admin") && adminTypes.includes(view.value)) ||
    (view.value === "customers" && can("catalog.write")),
);
onMounted(async () => {
  await run(async () => {
    try {
      me.value = await api("/auth/me");
    } catch (e) {
      if (e.message === "UNAUTHENTICATED") return;
      throw e;
    }
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
});
</script>
<template>
  <div v-if="!me" class="login-shell">
    <section class="login-story">
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技 ZhuaTech" class="brand-logo"
      /></a>
      <div class="story-copy">
        <p class="eyebrow">BOXTIME / IMPORT CONTAINERS</p>
        <h1>
          {{ t("集装箱免箱期", "Container free time") }}<br />{{
            t("与费用对账", "& reconciliation")
          }}
        </h1>
        <p>
          {{
            t(
              "免箱期 · 箱动态 · 费用对账",
              "Free time · Container events · Reconciliation",
            )
          }}
        </p>
        <div class="container-art" aria-hidden="true">
          <span>BOX / 20</span>
          <div></div>
          <span>BOX / 40</span>
        </div>
        <div class="story-footer">
          <Anchor :size="18" />
          {{
            t(
              "集装箱免箱期与超期费用对账",
              "Container free time and overdue charges",
            )
          }}
        </div>
      </div>
    </section>
    <section class="login-panel">
      <button class="language" @click="language">
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
      <form class="login-form" @submit.prevent="signIn">
        <span class="small-mark">BOXTIME</span>
        <h2>{{ t("登录工作台", "Sign in") }}</h2>
        <p class="muted">
          {{
            t("使用已开通的岗位账号继续", "Continue with your assigned account")
          }}
        </p>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            name="username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="primary wide" :disabled="busy">
          {{ busy ? t("正在登录…", "Signing in…") : t("登录", "Sign in")
          }}<ArrowRight :size="18" />
        </button>
        <p class="license">
          {{
            t(
              "公开源码学习版 · 非商业授权",
              "Public source learning edition · Noncommercial",
            )
          }}
        </p>
      </form>
      <footer class="login-contact">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技</a
        ><button class="text-button" @click="contact = true">
          {{ t("商业咨询", "Commercial enquiries") }}
        </button>
      </footer>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技 ZhuaTech"
      /></a>
      <div class="product">
        <Anchor :size="21" /><strong>BoxTime</strong><span>0.1</span>
      </div>
      <nav aria-label="Navigation">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: view === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || Settings" :size="18" /><span>{{
            lang === "zh" ? m.name : m.nameEn
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <span>{{ t("公开源码学习版", "Source learning edition") }}</span
        ><button @click="contact = true">
          {{ t("知华商业咨询", "ZhuaTech enquiries") }}
          <ExternalLink :size="13" />
        </button>
      </div>
    </aside>
    <main>
      <header class="topbar">
        <div>
          <span class="eyebrow"
            >{{ options.companyName }} / {{ view.toUpperCase() }}</span
          >
          <h1>{{ title }}</h1>
        </div>
        <div class="account">
          <button class="language" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button
            class="profile"
            @click="
              modal = { kind: 'password' };
              form = {};
            "
          >
            <span class="avatar">{{ me.displayName.slice(0, 1) }}</span
            ><span
              >{{ me.displayName }}<small>{{ me.role }}</small></span
            ></button
          ><button
            class="icon-button"
            :aria-label="t('退出登录', 'Sign out')"
            @click="signOut"
          >
            <LogOut :size="19" />
          </button>
        </div>
      </header>
      <section class="workspace">
        <p v-if="error && !modal" class="error" role="alert">{{ error }}</p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <template v-if="view === 'dashboard'"
          ><div class="section-heading">
            <div>
              <h2>
                {{ t("费用与箱务概览", "Charges and container overview") }}
              </h2>
              <p class="muted">
                {{ t("当前账号可见范围 · CNY", "Your visible records · CNY") }}
              </p>
            </div>
            <button @click="run(load)">
              <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}
            </button>
          </div>
          <div class="metric-grid">
            <article>
              <span>{{ t("箱务案件", "Container cases") }}</span
              ><strong>{{ stats.shipments || 0 }}</strong
              ><small
                >{{ stats.open || 0 }}
                {{ t("箱正在使用", "containers in use") }}</small
              >
            </article>
            <article>
              <span>{{ t("已确认应付", "Confirmed payable") }}</span
              ><strong>¥ {{ money(stats.confirmed) }}</strong
              ><small
                >{{ stats.disputed || 0 }}
                {{ t("笔争议待处理", "disputes unresolved") }}</small
              >
            </article>
            <article>
              <span>{{ t("净已付", "Net paid") }}</span
              ><strong>¥ {{ money(stats.paid) }}</strong
              ><small>{{
                t("扣除已冲正款项", "Excludes reversed payments")
              }}</small>
            </article>
            <article class="accent">
              <span>{{ t("未付余额", "Outstanding") }}</span
              ><strong>¥ {{ money(stats.balance) }}</strong
              ><small>{{
                t("仅计入客户已确认账单", "Customer accepted bills only")
              }}</small>
            </article>
          </div>
          <section class="panel">
            <h3>{{ t("案件状态分布", "Cases by status") }}</h3>
            <div v-for="(n, s) in stats.states" :key="s" class="stat-row">
              <span>{{ statusName(s) }}</span>
              <div class="bar-track">
                <div
                  :style="{
                    width:
                      Math.max(2, (n / (stats.shipments || 1)) * 100) + '%',
                  }"
                ></div>
              </div>
              <strong>{{ n }}</strong>
            </div>
            <p v-if="!stats.shipments" class="empty">
              {{ t("还没有箱务记录", "No container cases yet") }}
            </p>
          </section></template
        >
        <template v-else-if="detail && view === 'shipments'"
          ><button class="back text-button" @click="run(load)">
            <ChevronLeft :size="18" />{{
              t("返回箱务列表", "Back to containers")
            }}
          </button>
          <section class="case-heading">
            <div>
              <span class="eyebrow">{{ currentShipment.reference }}</span>
              <h2>{{ currentShipment.containerNo }}</h2>
              <p class="muted">
                {{ detail.agreement.carrier }} · {{ detail.agreement.port }} ·
                {{ detail.agreement.containerType }}
              </p>
            </div>
            <span :class="['badge', currentShipment.status]">{{
              statusName(currentShipment.status)
            }}</span>
          </section>
          <div class="event-strip">
            <article>
              <span>01 {{ t("卸船", "Discharge") }}</span
              ><strong>{{ currentShipment.arrival }}</strong>
            </article>
            <ArrowRight :size="19" />
            <article>
              <span>02 {{ t("提箱", "Gate out") }}</span
              ><strong>{{ currentShipment.gateOut || "—" }}</strong>
            </article>
            <ArrowRight :size="19" />
            <article>
              <span>03 {{ t("还空箱", "Empty return") }}</span
              ><strong>{{ currentShipment.emptyReturn || "—" }}</strong>
            </article>
            <div class="event-reference">
              <small>{{ t("提单号", "Bill of lading") }}</small
              ><strong>{{ currentShipment.billOfLading }}</strong>
            </div>
          </div>
          <div class="actionbar">
            <button
              v-for="a in currentActions"
              :key="a"
              :class="{ primary: !['cancel', 'dispute'].includes(a) }"
              :disabled="busy"
              @click="command(a)"
            >
              {{ commandName(a) }}</button
            ><button
              v-if="
                currentShipment.status === 'DRAFT' &&
                can('shipment.write') &&
                !me.customerId
              "
              @click="edit('shipments', currentShipment)"
            >
              {{ t("编辑草稿", "Edit draft") }}</button
            ><a
              v-if="can('export')"
              class="button"
              :href="'/api/shipments/' + selected + '/report.json'"
              download
              ><Download :size="15" />{{ t("导出证据", "Export evidence") }}</a
            >
          </div>
          <div class="detail-grid">
            <section class="panel">
              <div class="section-heading">
                <h3>{{ t("计费约定", "Charging agreement") }}</h3>
                <span class="badge APPROVED">{{
                  detail.agreement.reference
                }}</span>
              </div>
              <dl class="facts">
                <div>
                  <dt>{{ t("计费方式", "Charge mode") }}</dt>
                  <dd>
                    {{
                      detail.agreement.mode === "COMBINED"
                        ? phaseName("COMBINED")
                        : t("码头内外分开", "Separate")
                    }}
                  </dd>
                </div>
                <div>
                  <dt>{{ t("日历与时区", "Calendar / timezone") }}</dt>
                  <dd>
                    {{
                      detail.agreement.calendar === "CALENDAR"
                        ? t("日历日", "Calendar days")
                        : t("周一至周五", "Monday to Friday")
                    }}
                    · {{ detail.agreement.zone }}
                  </dd>
                </div>
                <div>
                  <dt>{{ t("起止日计入", "Boundary inclusion") }}</dt>
                  <dd>
                    {{ t("开始日", "Start") }}
                    {{
                      detail.agreement.includeStart
                        ? t("计入", "included")
                        : t("不计", "excluded")
                    }}
                    / {{ t("结束日", "End") }}
                    {{
                      detail.agreement.includeEnd
                        ? t("计入", "included")
                        : t("不计", "excluded")
                    }}
                  </dd>
                </div>
                <div>
                  <dt>{{ t("排除日期", "Excluded dates") }}</dt>
                  <dd>{{ detail.agreement.holidays || t("无", "None") }}</dd>
                </div>
              </dl>
              <div class="forecast">
                <label
                  >{{ t("核算截至日", "As of")
                  }}<input v-model="asOf" type="date" /></label
                ><button :disabled="busy" @click="forecast">
                  {{ t("查看日费明细", "View daily charges") }}
                </button>
              </div>
              <p v-if="preview" class="muted">
                {{
                  preview.final
                    ? t(
                        "已还箱，按实际还箱日核算",
                        "Returned: calculated to actual return date",
                      )
                    : t(
                        "预估费用，尚未还箱",
                        "Estimated charges: container not returned",
                      )
                }}
              </p>
            </section>
            <section class="panel settlement">
              <h3>{{ t("账单与付款", "Bill and payments") }}</h3>
              <div class="amount-row">
                <span>{{ t("核算费用", "Calculated") }}</span
                ><strong>¥ {{ money(detail.calculation?.total) }}</strong>
              </div>
              <div class="amount-row">
                <span>{{ t("承运账单", "Carrier bill") }}</span
                ><strong>{{
                  currentShipment.billAmount == null
                    ? "—"
                    : "¥ " + money(currentShipment.billAmount)
                }}</strong>
              </div>
              <div class="amount-row">
                <span>{{ t("账单差额", "Bill difference") }}</span
                ><strong>{{
                  currentShipment.billAmount == null
                    ? "—"
                    : "¥ " +
                      money(
                        currentShipment.billAmount -
                          (detail.calculation?.total || 0),
                      )
                }}</strong>
              </div>
              <div class="amount-row">
                <span>{{ t("净已付", "Net paid") }}</span
                ><strong>¥ {{ money(detail.paid) }}</strong>
              </div>
              <div class="balance">
                <span>{{ t("未付余额", "Outstanding") }}</span
                ><strong
                  >¥
                  {{
                    money((currentShipment.billAmount || 0) - detail.paid)
                  }}</strong
                >
              </div>
              <p class="muted">
                {{
                  currentShipment.billReference ||
                  t("尚未录入承运账单", "Carrier bill not recorded")
                }}
              </p>
              <p v-if="currentShipment.billNote">
                {{ currentShipment.billNote }}
              </p>
              <p v-if="currentShipment.acknowledgement" class="ack">
                <CheckCircle2 :size="15" />{{ currentShipment.acknowledgement }}
              </p>
            </section>
          </div>
          <section v-if="calculated?.phases" class="panel">
            <div class="section-heading">
              <h3>{{ t("逐日费用依据", "Daily charge evidence") }}</h3>
              <strong>¥ {{ money(calculated.total) }}</strong>
            </div>
            <div class="phase-grid">
              <article v-for="p in calculated.phases" :key="p.phase">
                <span>{{ phaseName(p.phase) }}</span
                ><strong>¥ {{ money(p.total) }}</strong
                ><small
                  >{{ t("计数", "Counted") }} {{ p.countedDays }} ·
                  {{ t("付费", "Charged") }} {{ p.chargedDays }} ·
                  {{ t("免费截止", "Last free") }}
                  {{ p.lastFreeDate || "—" }}</small
                >
              </article>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("阶段", "Phase") }}</th>
                    <th>{{ t("本地日期", "Local date") }}</th>
                    <th>{{ t("累计计数日", "Counted day") }}</th>
                    <th>{{ t("费率段", "Tier") }}</th>
                    <th class="numeric">
                      {{ t("日费用 CNY", "Daily charge CNY") }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="d in dailyRows" :key="d.phase + d.date">
                    <td>{{ phaseName(d.phase) }}</td>
                    <td>{{ d.date }}</td>
                    <td>{{ d.countedDay }}</td>
                    <td>
                      {{
                        {
                          FREE: t("免费", "Free"),
                          FIRST: t("第一段", "First"),
                          AFTER: t("后续", "After"),
                        }[d.tier]
                      }}
                    </td>
                    <td class="numeric">{{ money(d.amount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ dailyTotal }} {{ t("个计数日", "counted days") }}</span
              ><button :disabled="dayPage === 0" @click="dayPage--">
                <ChevronLeft :size="16" /></button
              ><span>{{ dayPage + 1 }}</span
              ><button
                :disabled="(dayPage + 1) * 30 >= dailyTotal"
                @click="dayPage++"
              >
                <ChevronRight :size="16" />
              </button>
            </div>
          </section>
          <section class="panel">
            <h3>{{ t("免箱延期申请", "Free time extensions") }}</h3>
            <div v-for="e in detail.extensions" :key="e.id" class="record-row">
              <div>
                <strong
                  >{{ phaseName(e.phase) }} +{{ e.days }}
                  {{ t("天", "days") }}</strong
                >
                <p>{{ e.proof }}</p>
              </div>
              <span :class="['badge', e.status]">{{
                statusName(e.status)
              }}</span
              ><template
                v-if="
                  e.status === 'PENDING' &&
                  can('extension.approve') &&
                  !me.customerId
                "
                ><button
                  @click="command('approve-extension', { extensionId: e.id })"
                >
                  {{ t("批准", "Approve") }}</button
                ><button
                  @click="command('reject-extension', { extensionId: e.id })"
                >
                  {{ t("驳回", "Reject") }}
                </button></template
              >
            </div>
            <p v-if="!detail.extensions.length" class="empty compact">
              {{ t("暂无延期记录", "No extensions") }}
            </p>
          </section>
          <section class="panel">
            <h3>{{ t("线下付款记录", "Offline payment records") }}</h3>
            <div v-for="p in detail.payments" :key="p.id" class="record-row">
              <div>
                <strong>¥ {{ money(p.amount) }} · {{ p.reference }}</strong>
                <p>{{ p.reason || formatTime(p.createdAt) }}</p>
              </div>
              <span :class="['badge', p.status]">{{
                statusName(p.status)
              }}</span
              ><button
                v-if="
                  p.status === 'POSTED' &&
                  can('payment.write') &&
                  !me.customerId
                "
                @click="command('reverse', { paymentId: p.id })"
              >
                {{ t("独立冲正", "Independent reversal") }}
              </button>
            </div>
            <p v-if="!detail.payments.length" class="empty compact">
              {{ t("暂无付款记录", "No payments") }}
            </p>
          </section>
          <section class="panel">
            <h3>{{ t("案件操作轨迹", "Case activity") }}</h3>
            <ol class="timeline">
              <li v-for="e in [...detail.events].reverse()" :key="e.id">
                <span class="timeline-dot"></span>
                <div>
                  <strong>{{
                    e.action === "SAVE"
                      ? t("保存草稿", "Draft saved")
                      : commandName(e.action)
                  }}</strong>
                  <p>{{ e.note || "—" }}</p>
                  <small
                    >{{ formatTime(e.createdAt) }} ·
                    {{ t("操作人", "Actor") }} #{{ e.actorId }}</small
                  >
                </div>
              </li>
            </ol>
          </section>
        </template>
        <template v-else-if="detail && view === 'agreements'"
          ><button class="back text-button" @click="run(load)">
            <ChevronLeft :size="18" />{{
              t("返回计费约定", "Back to agreements")
            }}
          </button>
          <div class="case-heading">
            <div>
              <span class="eyebrow"
                >{{ detail.carrier }} / {{ detail.port }}</span
              >
              <h2>{{ detail.reference }}</h2>
            </div>
            <span :class="['badge', detail.status]">{{
              statusName(detail.status)
            }}</span>
          </div>
          <div class="actionbar">
            <button
              v-if="
                detail.status === 'DRAFT' &&
                can('agreement.approve') &&
                !me.customerId
              "
              class="primary"
              @click="command('approve')"
            >
              {{ t("独立批准", "Independent approval") }}</button
            ><button
              v-if="
                detail.status === 'DRAFT' &&
                can('agreement.write') &&
                !me.customerId
              "
              @click="edit('agreements', detail)"
            >
              {{ t("编辑草稿", "Edit draft") }}</button
            ><button
              v-if="
                detail.status === 'DRAFT' &&
                can('agreement.write') &&
                !me.customerId
              "
              @click="command('cancel')"
            >
              {{ t("作废", "Cancel") }}</button
            ><a
              v-if="can('export')"
              class="button"
              :href="'/api/agreements/' + selected + '/report.json'"
              download
              ><Download :size="15" />{{ t("导出约定", "Export agreement") }}</a
            >
          </div>
          <section class="panel">
            <dl class="facts agreement-facts">
              <div v-for="f in fields.agreements" :key="f[0]">
                <dt>{{ t(f[1], f[2]) }}</dt>
                <dd>
                  {{
                    f[3] === "boolean"
                      ? detail[f[0]]
                        ? t("是", "Yes")
                        : t("否", "No")
                      : fieldValue(detail, f[0])
                  }}
                </dd>
              </div>
            </dl>
          </section></template
        >
        <template v-else
          ><div class="section-heading">
            <div>
              <h2>{{ title }}</h2>
              <p class="muted">
                {{ t("当前范围内的记录", "Records within your scope") }}
              </p>
            </div>
            <button v-if="canCreate" class="primary" @click="edit(view)">
              <Plus :size="17" />{{ t("新建", "New") }}
            </button>
          </div>
          <form
            class="filters"
            @submit.prevent="
              page = 0;
              run(load);
            "
          >
            <div class="search">
              <Search :size="17" /><input
                v-model="search"
                :placeholder="
                  t(
                    '搜索编号、箱号或名称',
                    'Search reference, container or name',
                  )
                "
                :aria-label="t('搜索', 'Search')"
              />
            </div>
            <select
              v-if="['shipments', 'agreements'].includes(view)"
              v-model="filter"
              :aria-label="t('状态筛选', 'Status filter')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option
                v-for="s in view === 'agreements'
                  ? ['DRAFT', 'APPROVED', 'CANCELLED']
                  : Object.keys(states).filter(
                      (s) =>
                        ![
                          'APPROVED',
                          'PENDING',
                          'REJECTED',
                          'POSTED',
                          'REVERSED',
                        ].includes(s),
                    )"
                :key="s"
                :value="s"
              >
                {{ statusName(s) }}
              </option></select
            ><select v-model="sort" :aria-label="t('排序', 'Sort')">
              <option value="newest">
                {{ t("最新在前", "Newest first") }}
              </option>
              <option value="reference">
                {{ t("编号／名称", "Reference / name") }}
              </option></select
            ><button>{{ t("查询", "Search") }}</button
            ><button
              type="button"
              class="icon-button"
              :aria-label="t('刷新', 'Refresh')"
              @click="
                run(async () => {
                  await loadOptions();
                  await load();
                })
              "
            >
              <RefreshCw :size="17" />
            </button>
          </form>
          <section class="panel list-panel">
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th v-for="c in columns" :key="c[0]">
                      {{ t(c[1], c[2]) }}
                    </th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in rows" :key="row.id">
                    <td v-for="c in columns" :key="c[0]">
                      <span
                        v-if="c[0] === 'status'"
                        :class="['badge', row.status]"
                        >{{ statusName(row.status) }}</span
                      ><span v-else>{{ fieldValue(row, c[0]) }}</span>
                    </td>
                    <td class="table-actions">
                      <button
                        v-if="['shipments', 'agreements'].includes(view)"
                        class="text-button"
                        @click="open(row)"
                      >
                        {{ t("查看", "Open") }}<ArrowRight :size="14" /></button
                      ><template v-if="canEdit"
                        ><button class="text-button" @click="edit(view, row)">
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          v-if="
                            [
                              'users',
                              'roles',
                              'departments',
                              'dictionaries',
                              'customers',
                            ].includes(view)
                          "
                          class="text-button danger"
                          @click="remove(view, row)"
                        >
                          {{ t("删除", "Delete") }}
                        </button></template
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="!rows.length" class="empty">
              <Boxes :size="32" /><strong>{{
                t("没有匹配记录", "No matching records")
              }}</strong>
              <p>
                {{
                  t(
                    "创建记录或调整查询条件",
                    "Create a record or change your filters",
                  )
                }}
              </p>
            </div>
            <div class="pagination">
              <span>{{ total }} {{ t("条记录", "records") }}</span
              ><button
                :disabled="page === 0 || busy"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="16" /></button
              ><span>{{ page + 1 }}</span
              ><button
                :disabled="(page + 1) * 12 >= total || busy"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="16" />
              </button>
            </div></section
        ></template>
      </section>
      <footer class="app-footer">
        <span
          >© 2026
          {{
            t(
              "知华科技 · 非商业源码学习版",
              "ZhuaTech · Noncommercial source edition",
            )
          }}</span
        ><button class="text-button" @click="contact = true">
          {{ t("商业授权与系统集成", "Commercial licensing & integration") }}
        </button>
      </footer>
    </main>
  </div>
  <div v-if="modal" class="overlay" @click.self="!busy && (modal = null)">
    <section
      class="dialog"
      :class="{ large: modal.type === 'agreements' }"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dialog-title"
    >
      <header>
        <div>
          <span class="eyebrow">BOXTIME</span>
          <h2 id="dialog-title">
            {{
              modal.kind === "command"
                ? commandName(modal.action)
                : modal.kind === "delete"
                  ? t("确认删除", "Confirm deletion")
                  : modal.kind === "password"
                    ? t("修改密码", "Change password")
                    : t(
                        modal.id ? "编辑记录" : "新建记录",
                        modal.id ? "Edit record" : "New record",
                      )
            }}
          </h2>
        </div>
        <button
          class="icon-button"
          :disabled="busy"
          :aria-label="t('关闭', 'Close')"
          @click="modal = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="save">
        <p v-if="modal.kind === 'delete'" class="muted">
          {{
            t(
              "仅可删除未被业务引用的记录。",
              "Only records without references can be deleted.",
            )
          }}
        </p>
        <p
          v-if="
            modal.kind === 'command' &&
            ['pay', 'reverse'].includes(modal.action)
          "
          class="muted"
        >
          {{
            t(
              "此处记录已完成的线下款项，不发起资金转账。",
              "Record completed offline payments; no money is transferred.",
            )
          }}
        </p>
        <div v-if="modal.kind !== 'delete'" class="form-grid">
          <div
            class="form-field"
            v-for="f in modalFields"
            :key="f[0]"
            :class="{
              full: f[3] === 'permissions' || f[3] === 'textarea',
              check: f[3] === 'boolean',
            }"
          >
            <template v-if="f[3] === 'boolean'"
              ><input
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                type="checkbox"
              /><label :for="'field-' + f[0]">{{
                t(f[1], f[2])
              }}</label></template
            ><template v-else
              ><span v-if="f[3] === 'permissions'">{{ t(f[1], f[2]) }}</span
              ><label v-else :for="'field-' + f[0]">{{ t(f[1], f[2]) }}</label>
              <div v-if="f[3] === 'permissions'" class="permission-grid">
                <label v-for="p in directories.permissions" :key="p.code"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ lang === "zh" ? p.name : p.code }}</label
                >
              </div>
              <select
                v-else-if="f[3] === 'select' || f[3] === 'id'"
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                :required="f[0] !== 'customerId' || modal.type !== 'users'"
              >
                <option value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="c in choices(f[4])"
                  :key="c.value"
                  :value="c.value"
                >
                  {{ c.label }}
                </option></select
              ><textarea
                v-else-if="f[3] === 'textarea'"
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                required
                maxlength="1000"
                rows="3"
              ></textarea
              ><input
                v-else
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                :type="
                  f[3] === 'integer'
                    ? 'number'
                    : f[3] === 'money'
                      ? 'text'
                      : f[3] || 'text'
                "
                :inputmode="f[3] === 'money' ? 'decimal' : undefined"
                :min="f[3] === 'integer' ? 0 : undefined"
                :max="f[3] === 'integer' ? 1000 : undefined"
                :step="f[3] === 'integer' ? 1 : undefined"
                :required="
                  !['holidays', 'password'].includes(f[0]) ||
                  (f[0] === 'password' && !modal.id)
                "
                :maxlength="
                  f[3] === 'password' ? 128 : f[0] === 'holidays' ? 4000 : 200
                "
                :autocomplete="f[3] === 'password' ? 'new-password' : 'off'"
            /></template>
          </div>
        </div>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <footer>
          <button type="button" :disabled="busy" @click="modal = null">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("正在保存…", "Saving…") : t("确认保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="contact" class="overlay" @click.self="contact = false">
    <section
      class="dialog contact-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="contact-title"
    >
      <header>
        <h2 id="contact-title">{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="20" />
        </button>
      </header>
      <img src="/brand/logo.jpg" alt="知华科技 ZhuaTech" class="brand-logo" />
      <p>上海如静知华信息科技有限公司</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >www.zhuatech.cn</a
      >
      <p class="muted">
        {{
          t(
            "商业授权、定制开发、部署与系统集成",
            "Commercial licensing, custom development, deployment and integration",
          )
        }}
      </p>
      <div class="qr-grid">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
          <figcaption>微信 zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
          <figcaption>微信 zhuatech2</figcaption>
        </figure>
      </div>
      <p class="license">
        {{
          t(
            "公开源码学习版。商业使用需另行取得书面授权。",
            "Source learning edition. Commercial use requires separate written authorization.",
          )
        }}
      </p>
    </section>
  </div>
</template>
