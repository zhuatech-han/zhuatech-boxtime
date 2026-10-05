// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 客户约定版本；批准后不可修改。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "agreement")
public class Agreement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "carrier", nullable = false, length = 120)
  public String carrier;

  @Column(name = "port", nullable = false, length = 120)
  public String port;

  @Column(name = "container_type", nullable = false, length = 60)
  public String containerType;

  @Column(name = "zone", nullable = false, length = 80)
  public String zone;

  @Column(name = "mode", nullable = false, length = 20)
  public String mode;

  @Column(name = "calendar", nullable = false, length = 20)
  public String calendar;

  @Column(name = "include_start", nullable = false)
  public boolean includeStart;

  @Column(name = "include_end", nullable = false)
  public boolean includeEnd;

  @Column(name = "holidays", nullable = false, length = 4000)
  public String holidays;

  @Column(name = "valid_from", nullable = false)
  public LocalDate validFrom;

  @Column(name = "valid_to", nullable = false)
  public LocalDate validTo;

  @Column(name = "free_demurrage", nullable = false)
  public int freeDemurrage;

  @Column(name = "free_detention", nullable = false)
  public int freeDetention;

  @Column(name = "free_combined", nullable = false)
  public int freeCombined;

  @Column(name = "tier_days", nullable = false)
  public int tierDays;

  @Column(name = "demurrage_first", nullable = false, precision = 14, scale = 2)
  public BigDecimal demurrageFirst;

  @Column(name = "demurrage_after", nullable = false, precision = 14, scale = 2)
  public BigDecimal demurrageAfter;

  @Column(name = "detention_first", nullable = false, precision = 14, scale = 2)
  public BigDecimal detentionFirst;

  @Column(name = "detention_after", nullable = false, precision = 14, scale = 2)
  public BigDecimal detentionAfter;

  @Column(name = "combined_first", nullable = false, precision = 14, scale = 2)
  public BigDecimal combinedFirst;

  @Column(name = "combined_after", nullable = false, precision = 14, scale = 2)
  public BigDecimal combinedAfter;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "approved_by", nullable = true)
  public Long approvedBy;

  @Column(name = "version", nullable = false)
  public long version;
}
