// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 单箱进口案件与完整结算状态。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "shipment")
public class Shipment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "bill_of_lading", nullable = false, length = 120)
  public String billOfLading;

  @Column(name = "container_no", nullable = false, length = 11)
  public String containerNo;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "agreement_id", nullable = false)
  public Long agreementId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "version", nullable = false)
  public long version;

  @Column(name = "arrival", nullable = false)
  public LocalDate arrival;

  @Column(name = "gate_out", nullable = true)
  public LocalDate gateOut;

  @Column(name = "empty_return", nullable = true)
  public LocalDate emptyReturn;

  @Column(name = "agreement_snapshot", nullable = false, columnDefinition = "longtext")
  public String agreementSnapshot;

  @Column(name = "calculation", nullable = false, columnDefinition = "longtext")
  public String calculation;

  @Column(name = "calculated_by", nullable = true)
  public Long calculatedBy;

  @Column(name = "reviewed_by", nullable = true)
  public Long reviewedBy;

  @Column(name = "bill_amount", nullable = true, precision = 14, scale = 2)
  public BigDecimal billAmount;

  @Column(name = "bill_reference", nullable = true, length = 120)
  public String billReference;

  @Column(name = "bill_note", nullable = true, length = 1000)
  public String billNote;

  @Column(name = "bill_by", nullable = true)
  public Long billBy;

  @Column(name = "acknowledgement", nullable = true, length = 1000)
  public String acknowledgement;
}
