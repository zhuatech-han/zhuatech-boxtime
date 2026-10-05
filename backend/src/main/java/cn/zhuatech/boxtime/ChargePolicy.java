// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** 按合同本地日期逐日计算，免费与付费日均遵循同一日历。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class ChargePolicy {
  private ChargePolicy() {}

  /** 合同参数为配置值，金额均为CNY，不内置船公司的官方费率。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Rule(
      String phase,
      int freeDays,
      int tierDays,
      BigDecimal firstRate,
      BigDecimal afterRate,
      String calendar,
      boolean includeStart,
      boolean includeEnd,
      Set<LocalDate> holidays) {}

  /** 每个计费日包括免费/分段依据，可与承运人账单逐日核对。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Day(LocalDate date, int countedDay, String tier, BigDecimal amount) {}

  /** 免费截止日仅在免费天数大于零时提供；total为该阶段费用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Result(
      String phase,
      LocalDate start,
      LocalDate end,
      LocalDate lastFreeDate,
      int countedDays,
      int chargedDays,
      BigDecimal total,
      List<Day> days) {}

  /** 有界逐日核算，不使用浮点金额或把未还箱的预估当作最终结算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Result calculate(LocalDate start, LocalDate end, Rule r) {
    if (start == null
        || end == null
        || end.isBefore(start)
        || end.isAfter(start.plusYears(5))
        || r.freeDays < 0
        || r.freeDays > 730
        || r.tierDays < 1
        || r.tierDays > 730
        || !Set.of("CALENDAR", "WORKDAY").contains(r.calendar)
        || r.firstRate == null
        || r.afterRate == null
        || r.firstRate.signum() < 0
        || r.afterRate.signum() < 0) throw new Problem(400, "INVALID_RULE");
    LocalDate from = r.includeStart ? start : start.plusDays(1),
        to = r.includeEnd ? end : end.minusDays(1);
    List<Day> days = new ArrayList<>();
    int counted = 0, charged = 0;
    BigDecimal total = BigDecimal.ZERO.setScale(2);
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      if (!eligible(date, r)) continue;
      counted++;
      String tier = "FREE";
      BigDecimal amount = BigDecimal.ZERO.setScale(2);
      if (counted > r.freeDays) {
        charged++;
        tier = charged <= r.tierDays ? "FIRST" : "AFTER";
        amount = charged <= r.tierDays ? r.firstRate : r.afterRate;
        total = total.add(amount);
      }
      days.add(new Day(date, counted, tier, amount));
    }
    LocalDate last = null;
    // Compute the contractual cutoff even when the box is returned within free time.
    if (r.freeDays > 0) {
      int n = 0;
      for (LocalDate date = from; n < r.freeDays; date = date.plusDays(1)) {
        if (date.isAfter(from.plusYears(10))) throw new Problem(400, "INVALID_RULE");
        if (eligible(date, r)) {
          n++;
          last = date;
        }
      }
    }
    return new Result(r.phase, start, end, last, counted, charged, total, List.copyOf(days));
  }

  private static boolean eligible(LocalDate d, Rule r) {
    return !r.holidays.contains(d)
        && (!r.calendar.equals("WORKDAY")
            || (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY));
  }
}
