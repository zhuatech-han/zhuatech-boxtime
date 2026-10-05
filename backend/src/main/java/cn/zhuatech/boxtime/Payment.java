// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 线下付款凭据及独立冲正，非资金转账。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "payment")
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "shipment_id", nullable = false)
  public Long shipmentId;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  public BigDecimal amount;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "paid_by", nullable = false)
  public Long paidBy;

  @Column(name = "reversed_by", nullable = true)
  public Long reversedBy;

  @Column(name = "reason", nullable = true, length = 1000)
  public String reason;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
