// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** BoxTime 有权限、数据范围与业务状态检查的HTTP入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final BoxService boxes;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(BoxService boxes, AdminService admin, AccessService access, Store db) {
    this.boxes = boxes;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 当前范围的表单选项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return boxes.options();
  }

  /** 客户创建或更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/customers")
  public Object customer(@RequestBody BoxService.CustomerInput v) {
    return boxes.customer(null, v);
  }

  /** 客户修改保留引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/customers/{id}")
  public Object customer(@PathVariable Long id, @RequestBody BoxService.CustomerInput v) {
    return boxes.customer(id, v);
  }

  /** 客户删除遵守外键限制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/customers/{id}")
  public Object deleteCustomer(@PathVariable Long id) {
    boxes.deleteCustomer(id);
    return Map.of("ok", true);
  }

  /** 约定分页查询。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/agreements")
  public Object agreements(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return boxes.agreements(search, status, page, size, sort);
  }

  /** 合同详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/agreements/{id}")
  public Object agreement(@PathVariable Long id) {
    return boxes.agreement(id);
  }

  /** 创建草稿约定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/agreements")
  public Object agreement(@RequestBody BoxService.AgreementInput v) {
    return boxes.saveAgreement(null, v);
  }

  /** 仅编辑草稿约定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/agreements/{id}")
  public Object agreement(@PathVariable Long id, @RequestBody BoxService.AgreementInput v) {
    return boxes.saveAgreement(id, v);
  }

  /** 独立批准或草稿作废。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/agreements/{id}/commands/{action}")
  public Object agreementCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody BoxService.Command v) {
    return boxes.agreementCommand(id, action, v);
  }

  /** 箱务分页查询。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/shipments")
  public Object shipments(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return boxes.shipments(search, status, page, size, sort);
  }

  /** 案件完整证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/shipments/{id}")
  public Object shipment(@PathVariable Long id) {
    return boxes.detail(id);
  }

  /** 创建箱务草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/shipments")
  public Object shipment(@RequestBody BoxService.ShipmentInput v) {
    return boxes.saveShipment(null, v);
  }

  /** 草稿编辑并验证乐观版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/shipments/{id}")
  public Object shipment(@PathVariable Long id, @RequestBody BoxService.ShipmentInput v) {
    return boxes.saveShipment(id, v);
  }

  /** 草稿删除保留审计，采用作废状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/shipments/{id}")
  public Object deleteShipment(@PathVariable Long id, @RequestBody BoxService.Command v) {
    return boxes.command(id, "cancel", v);
  }

  /** 完整业务状态机。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/shipments/{id}/commands/{action}")
  public Object shipmentCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody BoxService.Command v) {
    return boxes.command(id, action, v);
  }

  /** 未结束箱务仅提供预估。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/shipments/{id}/preview")
  public Object preview(@PathVariable Long id, @RequestParam LocalDate asOf) {
    return boxes.preview(id, asOf);
  }

  /** 受权限和数据范围双重约束的无广告业务导出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type}/{id}/report.json")
  public ResponseEntity<Object> export(@PathVariable String type, @PathVariable Long id) {
    access.require("export");
    Object result =
        switch (type) {
          case "shipments" -> boxes.detail(id);
          case "agreements" -> boxes.agreement(id);
          default -> throw new Problem(404, "NOT_FOUND");
        };
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(result);
  }

  /** 指标以真实可见案件为基础，争议账单不计入已确认应付。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows =
        access.role().permissions.contains("shipment.read")
            ? boxes.visibleShipments()
            : List.<Shipment>of();
    Map<String, Long> states = new TreeMap<>();
    BigDecimal billed = BigDecimal.ZERO, paid = BigDecimal.ZERO;
    for (var s : rows) {
      states.merge(s.status, 1L, Long::sum);
      if (Set.of("ACCEPTED", "PART_PAID", "PAID").contains(s.status)) {
        billed = billed.add(s.billAmount);
        paid = paid.add(boxes.paid(s.id));
      }
    }
    return Map.of(
        "shipments",
        rows.size(),
        "states",
        states,
        "confirmed",
        billed,
        "paid",
        paid,
        "balance",
        billed.subtract(paid),
        "open",
        rows.stream().filter(s -> s.status.equals("ACTIVE")).count(),
        "disputed",
        rows.stream().filter(s -> s.status.equals("DISPUTED")).count());
  }

  /** 内部岗位操作审计，SELF只读本人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    boxes.staff();
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .limit(500)
        .toList();
  }

  /** 管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminSave(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 保护引用和最后未绑定客户的管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
