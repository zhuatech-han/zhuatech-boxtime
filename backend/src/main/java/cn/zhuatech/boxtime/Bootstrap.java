// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库只创建权限和管理员，不创建客户、船公司合同或箱务事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${boxtime.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 仅首次建立注册权限、导航和角色。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    String[][] perms = {
      {"shipment.read", "查看箱务"},
      {"shipment.write", "编制箱务与箱动态"},
      {"agreement.read", "查看计费约定"},
      {"agreement.write", "编制计费约定"},
      {"agreement.approve", "独立批准约定"},
      {"extension.write", "申请延期"},
      {"extension.approve", "独立审批延期"},
      {"charge.calculate", "核算费用"},
      {"charge.review", "独立复核费用"},
      {"bill.write", "录入承运账单"},
      {"bill.confirm", "客户确认与争议"},
      {"payment.write", "线下付款及独立冲正"},
      {"catalog.write", "客户档案"},
      {"dashboard", "经营统计"},
      {"export", "业务导出"},
      {"audit", "操作审计"},
      {"admin", "系统管理"}
    };
    Set<String> all = new HashSet<>();
    for (var row : perms) {
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
      all.add(p.code);
    }
    var admin = role("管理员", "ALL", all);
    role(
        "箱务运营",
        "DEPARTMENT",
        Set.of(
            "shipment.read",
            "shipment.write",
            "agreement.read",
            "agreement.write",
            "extension.write",
            "charge.calculate",
            "bill.write",
            "dashboard",
            "export",
            "audit"));
    role(
        "独立复核",
        "DEPARTMENT",
        Set.of(
            "shipment.read",
            "agreement.read",
            "agreement.approve",
            "extension.approve",
            "charge.review",
            "dashboard",
            "export",
            "audit"));
    role(
        "财务",
        "DEPARTMENT",
        Set.of(
            "shipment.read",
            "agreement.read",
            "bill.write",
            "payment.write",
            "catalog.write",
            "dashboard",
            "export",
            "audit"));
    role(
        "货主客户",
        "SELF",
        Set.of("shipment.read", "extension.write", "bill.confirm", "dashboard", "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.enabled = true;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    String[][] menus = {
      {"shipments", "箱务工作台", "Container desk", "shipment.read"},
      {"agreements", "计费约定", "Agreements", "agreement.read"},
      {"customers", "客户档案", "Customers", "catalog.write"},
      {"dashboard", "费用统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "箱型字典", "Container types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "BoxTime 集装箱费用对账", "maxShipments", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    for (String code : List.of("20GP", "40GP", "40HC")) {
      var e = new DictionaryEntry();
      e.type = "container";
      e.code = code;
      e.name = code;
      e.nameEn = code;
      db.save(e);
    }
  }

  private AccessRole role(String name, String scope, Set<String> perms) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(perms);
    return db.save(r);
  }
}
