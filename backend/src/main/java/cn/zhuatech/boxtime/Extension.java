// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import jakarta.persistence.*;
import java.time.*;

/** 免箱期延期申请与独立审批证据。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "free_extension")
public class Extension {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "shipment_id", nullable = false)
  public Long shipmentId;

  @Column(name = "phase", nullable = false, length = 20)
  public String phase;

  @Column(name = "days", nullable = false)
  public int days;

  @Column(name = "proof", nullable = false, length = 1000)
  public String proof;

  @Column(name = "requested_by", nullable = false)
  public Long requestedBy;

  @Column(name = "reviewed_by", nullable = true)
  public Long reviewedBy;

  @Column(name = "status", nullable = false, length = 20)
  public String status;
}
