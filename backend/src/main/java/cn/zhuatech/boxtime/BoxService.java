// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 箱务、约定、延期、核算和结算的事务边界；客户绑定优先于角色范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class BoxService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper mapper;

  public BoxService(Store db, AccessService access, Clock clock, ObjectMapper mapper) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.mapper = mapper;
  }

  /** 合同草稿输入，不接受审批人或状态赋值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record AgreementInput(
      String requestKey,
      Long version,
      String reference,
      Long customerId,
      String carrier,
      String port,
      String containerType,
      String zone,
      String mode,
      String calendar,
      Boolean includeStart,
      Boolean includeEnd,
      String holidays,
      LocalDate validFrom,
      LocalDate validTo,
      Integer freeDemurrage,
      Integer freeDetention,
      Integer freeCombined,
      Integer tierDays,
      BigDecimal demurrageFirst,
      BigDecimal demurrageAfter,
      BigDecimal detentionFirst,
      BigDecimal detentionAfter,
      BigDecimal combinedFirst,
      BigDecimal combinedAfter) {}

  /** 单箱案件草稿，进口卸船日期按合同港口本地日填写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ShipmentInput(
      String requestKey,
      Long version,
      String reference,
      String billOfLading,
      String containerNo,
      Long agreementId,
      LocalDate arrival) {}

  /** 有界、版本化、UUID重试命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      String requestKey,
      Long version,
      String note,
      LocalDate date,
      String phase,
      Integer days,
      Long extensionId,
      BigDecimal amount,
      String reference,
      Long paymentId) {}

  /** 客户档案必须归属现存可见部门。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CustomerInput(String name, Long departmentId, Boolean enabled) {}

  /** 刷新授权后持有共享管理锁，角色变更和业务写入按同一顺序串行化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  /** 绑定客户即使拥有ALL管理员权限，也不得使用内部岗位功能。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void staff() {
    if (access.current().customerId != null) throw new Problem(403, "STAFF_ONLY");
  }

  private boolean visible(Long department, Long customer, Long creator) {
    var a = access.current();
    if (a.customerId != null)
      return Objects.equals(a.customerId, customer) && Objects.equals(a.departmentId, department);
    return access.visible(department)
        && (!access.role().scope.equals("SELF") || Objects.equals(a.id, creator));
  }

  private Shipment visibleShipment(Long id) {
    var s = db.get(Shipment.class, id);
    if (!visible(s.departmentId, s.customerId, s.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    return s;
  }

  private Agreement visibleAgreement(Long id) {
    var a = db.get(Agreement.class, id);
    if (!visible(a.departmentId, a.customerId, a.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    return a;
  }

  /** 页面选项不泄露其他客户账号或非可读合同。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    var actor = access.current();
    boolean staff = actor.customerId == null;
    return Map.of(
        "customers",
        db.all(Customer.class).stream()
            .filter(
                c ->
                    actor.customerId != null
                        ? Objects.equals(c.id, actor.customerId)
                        : access.visible(c.departmentId))
            .toList(),
        "agreements",
        access.role().permissions.contains("agreement.read")
            ? db.all(Agreement.class).stream()
                .filter(a -> visible(a.departmentId, a.customerId, a.createdBy))
                .toList()
            : List.of(),
        "departments",
        staff
            ? db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList()
            : List.of(),
        "containerTypes",
        db.all(DictionaryEntry.class).stream().filter(e -> e.type.equals("container")).toList(),
        "companyName",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "companyName")
            .getFirst()
            .value,
        "timezone",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "timezone")
            .getFirst()
            .value);
  }

  /** 客户档案增改；历史部门不可漂移，引用由外键保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object customer(Long id, CustomerInput v) {
    lock();
    access.require("catalog.write");
    staff();
    var d = db.get(Department.class, v.departmentId);
    access.department(d.id);
    var c = id == null ? new Customer() : db.get(Customer.class, id);
    if (id != null) {
      access.department(c.departmentId);
      if (!Objects.equals(c.departmentId, d.id)) throw new Problem(409, "IMMUTABLE_DEPARTMENT");
    }
    c.name = AdminService.text(v.name, 120);
    c.departmentId = d.id;
    c.enabled = Boolean.TRUE.equals(v.enabled);
    if (id == null) db.save(c);
    access.audit("CUSTOMER_SAVE", c.id, c.departmentId);
    return c;
  }

  /** 删除未被任何合同、账号引用的客户。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteCustomer(Long id) {
    lock();
    access.require("catalog.write");
    staff();
    var c = db.get(Customer.class, id);
    access.department(c.departmentId);
    db.delete(c);
    access.audit("CUSTOMER_DELETE", id, c.departmentId);
  }

  /** 合同列表真实筛选分页；排序字段是白名单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object agreements(String search, String status, int page, int size, String sort) {
    access.require("agreement.read");
    return page(
        db.all(Agreement.class).stream()
            .filter(a -> visible(a.departmentId, a.customerId, a.createdBy))
            .filter(a -> status.isBlank() || a.status.equals(status))
            .filter(a -> contains(search, a.reference, a.carrier, a.port))
            .sorted(
                sort.equals("reference")
                    ? Comparator.comparing(a -> a.reference)
                    : Comparator.comparing((Agreement a) -> a.id).reversed())
            .toList(),
        page,
        size,
        sort);
  }

  /** 合同详情不绕过可读权限和范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Agreement agreement(Long id) {
    access.require("agreement.read");
    return visibleAgreement(id);
  }

  /** 新约定或草稿编辑；批准后的版本只能另建新约定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveAgreement(Long id, AgreementInput v) {
    lock();
    access.require("agreement.write");
    staff();
    String fp = fingerprint("AGREEMENT_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return agreement(prior);
    var a = id == null ? new Agreement() : visibleAgreement(id);
    if (id != null) {
      version(a.version, v.version);
      state(a.status, "DRAFT");
    }
    var c = db.get(Customer.class, v.customerId);
    access.department(c.departmentId);
    if (!c.enabled) throw new Problem(409, "DISABLED_CUSTOMER");
    a.reference = AdminService.text(v.reference, 120);
    a.customerId = c.id;
    a.departmentId = c.departmentId;
    a.carrier = AdminService.text(v.carrier, 120);
    a.port = AdminService.text(v.port, 120);
    a.containerType = AdminService.text(v.containerType, 60);
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type=?1 and code=?2",
            "container",
            a.containerType)
        .isEmpty()) throw new Problem(400, "INVALID_CONTAINER_TYPE");
    a.zone = AdminService.text(v.zone, 80);
    ZoneId.of(a.zone);
    a.mode = choice(v.mode, "SEPARATE", "COMBINED");
    a.calendar = choice(v.calendar, "CALENDAR", "WORKDAY");
    if (v.includeStart == null || v.includeEnd == null) throw new Problem(400, "INVALID_RULE");
    a.includeStart = v.includeStart;
    a.includeEnd = v.includeEnd;
    a.holidays = v.holidays == null ? "" : v.holidays.trim();
    if (a.holidays.length() > 4000) throw new Problem(400, "INVALID_RULE");
    holidays(a.holidays);
    if (v.validFrom == null
        || v.validTo == null
        || v.validTo.isBefore(v.validFrom)
        || v.validTo.isAfter(v.validFrom.plusYears(5))) throw new Problem(400, "INVALID_DATE");
    a.validFrom = v.validFrom;
    a.validTo = v.validTo;
    a.freeDemurrage = days(v.freeDemurrage, 0, 730);
    a.freeDetention = days(v.freeDetention, 0, 730);
    a.freeCombined = days(v.freeCombined, 0, 730);
    a.tierDays = days(v.tierDays, 1, 730);
    a.demurrageFirst = money(v.demurrageFirst, false);
    a.demurrageAfter = money(v.demurrageAfter, false);
    a.detentionFirst = money(v.detentionFirst, false);
    a.detentionAfter = money(v.detentionAfter, false);
    a.combinedFirst = money(v.combinedFirst, false);
    a.combinedAfter = money(v.combinedAfter, false);
    a.status = "DRAFT";
    if (id == null) {
      a.createdBy = access.current().id;
      capacity(Agreement.class);
      db.save(a);
    }
    a.version++;
    event("AGREEMENT", a.id, "SAVE", "", a, a.departmentId);
    remember(v.requestKey, fp, a.id);
    return a;
  }

  /** 合同批准需不同操作人；未引用草稿可作废，批准版本永久保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object agreementCommand(Long id, String action, Command v) {
    lock();
    staff();
    access.require(action.equals("approve") ? "agreement.approve" : "agreement.write");
    var a = visibleAgreement(id);
    String fp = fingerprint("AGREEMENT_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return a;
    version(a.version, v.version);
    state(a.status, "DRAFT");
    if (action.equals("approve")) {
      independent(a.createdBy);
      a.approvedBy = access.current().id;
      a.status = "APPROVED";
    } else if (action.equals("cancel")) a.status = "CANCELLED";
    else throw new Problem(400, "INVALID_ACTION");
    a.version++;
    event("AGREEMENT", id, action, note(v.note), a, a.departmentId);
    remember(v.requestKey, fp, id);
    return a;
  }

  /** 可读案件用于列表与统计，SELF是创建人，客户绑定是所属货主。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Shipment> visibleShipments() {
    access.require("shipment.read");
    return db.all(Shipment.class).stream()
        .filter(s -> visible(s.departmentId, s.customerId, s.createdBy))
        .toList();
  }

  /** 单箱列表搜索、状态、分页、排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object shipments(String search, String status, int p, int size, String sort) {
    return page(
        visibleShipments().stream()
            .filter(s -> status.isBlank() || s.status.equals(status))
            .filter(s -> contains(search, s.reference, s.containerNo, s.billOfLading))
            .sorted(
                sort.equals("reference")
                    ? Comparator.comparing(s -> s.reference)
                    : Comparator.comparing((Shipment s) -> s.id).reversed())
            .toList(),
        p,
        size,
        sort);
  }

  /** 返回合同冻结快照、逐日费用、延期与款项，证据仅本案件可读。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    access.require("shipment.read");
    var s = visibleShipment(id);
    var result = new LinkedHashMap<String, Object>();
    result.put("shipment", s);
    result.put("agreement", mapper.readValue(s.agreementSnapshot, Agreement.class));
    result.put("extensions", extensions(id));
    result.put("payments", payments(id));
    result.put("paid", paid(id));
    result.put("calculation", mapper.readValue(s.calculation, Object.class));
    result.put(
        "events",
        db.query(
            BusinessEvent.class,
            "from BusinessEvent where objectType=?1 and objectId=?2 order by id",
            "SHIPMENT",
            id));
    return result;
  }

  /** 草稿引用已批准合同，校验本地日期及箱号格式，冻结完整费率。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveShipment(Long id, ShipmentInput v) {
    lock();
    staff();
    access.require("shipment.write");
    String fp = fingerprint("SHIPMENT_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return detail(prior);
    var s = id == null ? new Shipment() : visibleShipment(id);
    if (id != null) {
      version(s.version, v.version);
      state(s.status, "DRAFT");
    }
    access.require("agreement.read");
    var a = visibleAgreement(v.agreementId);
    state(a.status, "APPROVED");
    var customer = db.get(Customer.class, a.customerId);
    if (!customer.enabled) throw new Problem(409, "DISABLED_CUSTOMER");
    if (v.arrival == null
        || v.arrival.isBefore(a.validFrom)
        || v.arrival.isAfter(a.validTo)
        || v.arrival.isAfter(today(a.zone))) throw new Problem(400, "INVALID_DATE");
    s.reference = AdminService.text(v.reference, 120);
    s.billOfLading = AdminService.text(v.billOfLading, 120);
    s.containerNo = AdminService.text(v.containerNo, 11).toUpperCase(Locale.ROOT);
    if (!s.containerNo.matches("[A-Z]{4}[0-9]{7}")) throw new Problem(400, "INVALID_CONTAINER_NO");
    if (db
        .query(
            Shipment.class,
            "from Shipment where agreementId=?1 and billOfLading=?2 and containerNo=?3",
            a.id,
            s.billOfLading,
            s.containerNo)
        .stream()
        .anyMatch(x -> !Objects.equals(x.id, id) && !x.status.equals("CANCELLED")))
      throw new Problem(409, "DUPLICATE_CONTAINER_CASE");
    s.customerId = a.customerId;
    s.departmentId = a.departmentId;
    s.agreementId = a.id;
    s.arrival = v.arrival;
    s.agreementSnapshot = mapper.writeValueAsString(a);
    s.calculation = "{}";
    s.status = "DRAFT";
    if (id == null) {
      capacity(Shipment.class);
      s.createdBy = access.current().id;
      db.save(s);
    }
    s.version++;
    event("SHIPMENT", s.id, "SAVE", "", s, s.departmentId);
    remember(v.requestKey, fp, s.id);
    return detail(s.id);
  }

  /** 事务状态机：动态、延期、核算、复核、账单和付款均检查真实岗位与版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Command v) {
    lock();
    var permission =
        switch (action) {
          case "submit", "gate-out", "return", "cancel" -> "shipment.write";
          case "extend" -> "extension.write";
          case "approve-extension", "reject-extension" -> "extension.approve";
          case "calculate" -> "charge.calculate";
          case "review" -> "charge.review";
          case "bill", "revise-bill" -> "bill.write";
          case "confirm", "dispute" -> "bill.confirm";
          case "pay", "reverse" -> "payment.write";
          default -> throw new Problem(400, "INVALID_ACTION");
        };
    access.require(permission);
    if (!Set.of("extend", "confirm", "dispute").contains(action)) staff();
    var s = visibleShipment(id);
    String fp = fingerprint("SHIPMENT_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return detail(id);
    version(s.version, v.version);
    var a = mapper.readValue(s.agreementSnapshot, Agreement.class);
    var actor = access.current().id;
    String note = note(v.note);
    switch (action) {
      case "submit" -> {
        state(s.status, "DRAFT");
        if (!db.get(Customer.class, s.customerId).enabled)
          throw new Problem(409, "DISABLED_CUSTOMER");
        s.status = "ACTIVE";
      }
      case "gate-out" -> {
        state(s.status, "ACTIVE");
        if (s.gateOut != null) throw new Problem(409, "EVENT_EXISTS");
        date(v.date, s.arrival, a.zone);
        s.gateOut = v.date;
        AdminService.text(note, 1000);
      }
      case "return" -> {
        state(s.status, "ACTIVE");
        if (s.gateOut == null) throw new Problem(409, "MISSING_GATE_OUT");
        date(v.date, s.gateOut, a.zone);
        s.emptyReturn = v.date;
        s.status = "RETURNED";
        AdminService.text(note, 1000);
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "ACTIVE").contains(s.status)) throw new Problem(409, "INVALID_STATE");
        if (extensions(id).stream().anyMatch(e -> e.status.equals("PENDING")))
          throw new Problem(409, "PENDING_EXTENSION");
        AdminService.text(note, 1000);
        s.status = "CANCELLED";
      }
      case "extend" -> {
        if (!Set.of("ACTIVE", "RETURNED").contains(s.status))
          throw new Problem(409, "INVALID_STATE");
        String phase =
            choice(
                v.phase,
                a.mode.equals("COMBINED")
                    ? new String[] {"COMBINED"}
                    : new String[] {"DEMURRAGE", "DETENTION"});
        if (extensions(id).stream()
            .anyMatch(e -> e.phase.equals(phase) && !e.status.equals("REJECTED")))
          throw new Problem(409, "EXTENSION_EXISTS");
        var e = new Extension();
        e.shipmentId = id;
        e.phase = phase;
        e.days = days(v.days, 1, 365);
        int base =
            switch (phase) {
              case "DEMURRAGE" -> a.freeDemurrage;
              case "DETENTION" -> a.freeDetention;
              default -> a.freeCombined;
            };
        if (base + e.days > 730) throw new Problem(400, "INVALID_RULE");
        e.proof = AdminService.text(note, 1000);
        e.requestedBy = actor;
        e.status = "PENDING";
        db.save(e);
      }
      case "approve-extension", "reject-extension" -> {
        if (!Set.of("ACTIVE", "RETURNED").contains(s.status))
          throw new Problem(409, "INVALID_STATE");
        var e = db.get(Extension.class, v.extensionId);
        if (!Objects.equals(e.shipmentId, id)) throw new Problem(403, "OUT_OF_SCOPE");
        state(e.status, "PENDING");
        independent(e.requestedBy);
        AdminService.text(note, 1000);
        e.reviewedBy = actor;
        e.status = action.equals("approve-extension") ? "APPROVED" : "REJECTED";
      }
      case "calculate" -> {
        state(s.status, "RETURNED");
        if (extensions(id).stream().anyMatch(e -> e.status.equals("PENDING")))
          throw new Problem(409, "PENDING_EXTENSION");
        s.calculation = mapper.writeValueAsString(calculate(s, s.emptyReturn));
        s.calculatedBy = actor;
        s.status = "CALCULATED";
      }
      case "review" -> {
        state(s.status, "CALCULATED");
        independent(s.calculatedBy);
        AdminService.text(note, 1000);
        s.reviewedBy = actor;
        s.status = "REVIEWED";
      }
      case "bill", "revise-bill" -> {
        state(s.status, action.equals("bill") ? "REVIEWED" : "DISPUTED");
        s.billAmount = money(v.amount, false);
        s.billReference = AdminService.text(v.reference, 120);
        s.billNote = AdminService.text(note, 1000);
        s.billBy = actor;
        s.acknowledgement = null;
        s.status = "BILLED";
      }
      case "confirm", "dispute" -> {
        state(s.status, "BILLED");
        independent(s.billBy);
        s.acknowledgement = AdminService.text(note, 1000);
        s.status =
            action.equals("confirm")
                ? (s.billAmount.signum() == 0 ? "PAID" : "ACCEPTED")
                : "DISPUTED";
      }
      case "pay" -> {
        if (!Set.of("ACCEPTED", "PART_PAID").contains(s.status))
          throw new Problem(409, "INVALID_STATE");
        var amount = money(v.amount, true);
        if (amount.compareTo(s.billAmount.subtract(paid(id))) > 0)
          throw new Problem(409, "OVERPAYMENT");
        var p = new Payment();
        p.shipmentId = id;
        p.amount = amount;
        p.reference = AdminService.text(v.reference, 120);
        p.paidBy = actor;
        p.status = "POSTED";
        p.createdAt = BusinessTime.now(clock);
        db.save(p);
        db.flush();
        s.status = paid(id).compareTo(s.billAmount) == 0 ? "PAID" : "PART_PAID";
      }
      case "reverse" -> {
        if (!Set.of("PART_PAID", "PAID").contains(s.status))
          throw new Problem(409, "INVALID_STATE");
        var p = db.get(Payment.class, v.paymentId);
        if (!Objects.equals(p.shipmentId, id)) throw new Problem(403, "OUT_OF_SCOPE");
        state(p.status, "POSTED");
        independent(p.paidBy);
        p.reason = AdminService.text(note, 1000);
        p.reversedBy = actor;
        p.status = "REVERSED";
        db.flush();
        s.status = paid(id).signum() == 0 ? "ACCEPTED" : "PART_PAID";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    s.version++;
    event(
        "SHIPMENT",
        id,
        action,
        note,
        Map.of("shipment", s, "extensions", extensions(id), "payments", payments(id)),
        s.departmentId);
    remember(v.requestKey, fp, id);
    return detail(id);
  }

  /** 预估明确标注，未结束阶段仅计算至指定本地日，不生成财务账单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object preview(Long id, LocalDate asOf) {
    access.require("shipment.read");
    var s = visibleShipment(id);
    var a = mapper.readValue(s.agreementSnapshot, Agreement.class);
    if (asOf == null || asOf.isBefore(s.arrival) || asOf.isAfter(today(a.zone)))
      throw new Problem(400, "INVALID_DATE");
    if (s.gateOut != null && asOf.isBefore(s.gateOut)
        || s.emptyReturn != null && asOf.isBefore(s.emptyReturn))
      throw new Problem(400, "INVALID_DATE");
    return Map.of("final", s.emptyReturn != null, "asOf", asOf, "calculation", calculate(s, asOf));
  }

  private Object calculate(Shipment s, LocalDate asOf) {
    var a = mapper.readValue(s.agreementSnapshot, Agreement.class);
    List<ChargePolicy.Result> phases = new ArrayList<>();
    LocalDate end = s.emptyReturn == null ? asOf : s.emptyReturn;
    if (a.mode.equals("COMBINED"))
      phases.add(ChargePolicy.calculate(s.arrival, end, rule(a, "COMBINED", s.id)));
    else {
      phases.add(
          ChargePolicy.calculate(
              s.arrival, s.gateOut == null ? asOf : s.gateOut, rule(a, "DEMURRAGE", s.id)));
      if (s.gateOut != null)
        phases.add(ChargePolicy.calculate(s.gateOut, end, rule(a, "DETENTION", s.id)));
    }
    return Map.of(
        "currency",
        "CNY",
        "phases",
        phases,
        "total",
        phases.stream()
            .map(ChargePolicy.Result::total)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
  }

  private ChargePolicy.Rule rule(Agreement a, String phase, Long shipment) {
    int extra =
        extensions(shipment).stream()
            .filter(e -> e.status.equals("APPROVED") && e.phase.equals(phase))
            .mapToInt(e -> e.days)
            .sum();
    int free =
        switch (phase) {
          case "DEMURRAGE" -> a.freeDemurrage;
          case "DETENTION" -> a.freeDetention;
          default -> a.freeCombined;
        };
    BigDecimal first =
        switch (phase) {
          case "DEMURRAGE" -> a.demurrageFirst;
          case "DETENTION" -> a.detentionFirst;
          default -> a.combinedFirst;
        };
    BigDecimal after =
        switch (phase) {
          case "DEMURRAGE" -> a.demurrageAfter;
          case "DETENTION" -> a.detentionAfter;
          default -> a.combinedAfter;
        };
    return new ChargePolicy.Rule(
        phase,
        free + extra,
        a.tierDays,
        first,
        after,
        a.calendar,
        a.includeStart,
        a.includeEnd,
        holidays(a.holidays));
  }

  private List<Extension> extensions(Long id) {
    return db.query(Extension.class, "from Extension where shipmentId=?1 order by id", id);
  }

  private List<Payment> payments(Long id) {
    return db.query(Payment.class, "from Payment where shipmentId=?1 order by id", id);
  }

  /** 统计净已付，已冲正款项不参与余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal paid(Long id) {
    return payments(id).stream()
        .filter(p -> p.status.equals("POSTED"))
        .map(p -> p.amount)
        .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
  }

  private void capacity(Class<?> type) {
    int maximum =
        Integer.parseInt(
            db.query(SystemSetting.class, "from SystemSetting where code=?1", "maxShipments")
                .getFirst()
                .value);
    if (db.all(type).size() >= maximum) throw new Problem(409, "CAPACITY_LIMIT");
  }

  private void date(LocalDate d, LocalDate from, String zone) {
    if (d == null || d.isBefore(from) || d.isAfter(today(zone)) || d.isAfter(from.plusYears(5)))
      throw new Problem(400, "INVALID_DATE");
  }

  private LocalDate today(String zone) {
    return LocalDate.now(clock.withZone(ZoneId.of(zone)));
  }

  private Set<LocalDate> holidays(String value) {
    var dates = new HashSet<LocalDate>();
    if (!value.isBlank()) {
      for (String d : value.split(",", -1)) dates.add(LocalDate.parse(d.trim()));
      if (dates.size() > 200) throw new Problem(400, "INVALID_RULE");
    }
    return dates;
  }

  private int days(Integer n, int min, int max) {
    if (n == null || n < min || n > max) throw new Problem(400, "INVALID_RULE");
    return n;
  }

  private BigDecimal money(BigDecimal n, boolean positive) {
    if (n == null
        || n.signum() < 0
        || positive && n.signum() == 0
        || n.compareTo(new BigDecimal("100000000")) > 0) throw new Problem(400, "INVALID_AMOUNT");
    try {
      return n.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new Problem(400, "INVALID_AMOUNT");
    }
  }

  private String choice(String value, String... choices) {
    if (value == null || !Arrays.asList(choices).contains(value))
      throw new Problem(400, "INVALID_RULE");
    return value;
  }

  private String note(String value) {
    if (value == null) return "";
    if (value.length() > 1000) throw new Problem(400, "INVALID_INPUT");
    return value.trim();
  }

  private void state(String actual, String required) {
    if (!actual.equals(required)) throw new Problem(409, "INVALID_STATE");
  }

  private void independent(Long actor) {
    if (Objects.equals(actor, access.current().id))
      throw new Problem(409, "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void version(long current, Long supplied) {
    if (supplied == null || current != supplied) throw new Problem(409, "STALE_VERSION");
  }

  private boolean contains(String query, String... fields) {
    if (query.length() > 200) throw new Problem(400, "INVALID_INPUT");
    return Arrays.stream(fields)
        .anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));
  }

  private Object page(List<?> rows, int p, int size, String sort) {
    if (p < 0
        || p > 100000
        || size < 1
        || size > 100
        || !Set.of("newest", "reference").contains(sort)) throw new Problem(400, "INVALID_PAGE");
    int start = Math.min(rows.size(), p * size), end = Math.min(rows.size(), start + size);
    return Map.of("items", rows.subList(start, end), "total", rows.size(), "page", p, "size", size);
  }

  private String fingerprint(String action, Long id, Object body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (access.current().id
                              + "|"
                              + action
                              + "|"
                              + id
                              + "|"
                              + mapper.writeValueAsString(body))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Long retry(String key, String fp) {
    if (key == null
        || !key.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var previous =
        db.query(
            CommandRecord.class,
            "from CommandRecord where requestKey=?1",
            key.toLowerCase(Locale.ROOT));
    if (previous.isEmpty()) return null;
    if (!previous.getFirst().fingerprint.equals(fp)) throw new Problem(409, "REQUEST_KEY_REUSED");
    return previous.getFirst().resultId;
  }

  private void remember(String key, String fp, Long id) {
    var c = new CommandRecord();
    c.requestKey = key.toLowerCase(Locale.ROOT);
    c.fingerprint = fp;
    c.resultId = id;
    db.save(c);
  }

  private void event(
      String type, Long id, String action, String note, Object snapshot, Long department) {
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.snapshot = mapper.writeValueAsString(snapshot);
    e.createdAt = BusinessTime.now(clock);
    db.save(e);
    access.audit(type + "_" + action, id, department);
  }
}
