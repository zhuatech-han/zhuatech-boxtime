// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.boxtime;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 日期与金额边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class ChargePolicyTest {
  ChargePolicy.Rule rule(
      int free, boolean start, boolean end, String calendar, Set<LocalDate> holidays) {
    return new ChargePolicy.Rule(
        "COMBINED",
        free,
        2,
        new BigDecimal("100.50"),
        new BigDecimal("200.75"),
        calendar,
        start,
        end,
        holidays);
  }

  LocalDate d(String s) {
    return LocalDate.parse(s);
  }

  @Test
  void exactFreeBoundary() {
    var r =
        ChargePolicy.calculate(
            d("2026-09-01"), d("2026-09-04"), rule(3, true, false, "CALENDAR", Set.of()));
    assertEquals(new BigDecimal("0.00"), r.total());
    assertEquals(d("2026-09-03"), r.lastFreeDate());
    assertEquals(3, r.countedDays());
  }

  @Test
  void tierBoundary() {
    var r =
        ChargePolicy.calculate(
            d("2026-09-01"), d("2026-09-08"), rule(3, true, false, "CALENDAR", Set.of()));
    assertEquals(new BigDecimal("602.50"), r.total());
    assertEquals(4, r.chargedDays());
  }

  @Test
  void workingAndHoliday() {
    var r =
        ChargePolicy.calculate(
            d("2026-09-04"),
            d("2026-09-10"),
            rule(2, true, false, "WORKDAY", Set.of(d("2026-09-07"))));
    assertEquals(d("2026-09-08"), r.lastFreeDate());
    assertEquals(3, r.countedDays());
    assertEquals(new BigDecimal("100.50"), r.total());
  }

  @Test
  void explicitBoundaries() {
    var r =
        ChargePolicy.calculate(
            d("2026-09-01"), d("2026-09-04"), rule(0, false, true, "CALENDAR", Set.of()));
    assertNull(r.lastFreeDate());
    assertEquals(d("2026-09-02"), r.days().getFirst().date());
    assertEquals(d("2026-09-04"), r.days().getLast().date());
    assertEquals(new BigDecimal("401.75"), r.total());
  }

  @Test
  void excludedSameDay() {
    assertEquals(
        0,
        ChargePolicy.calculate(
                d("2026-09-01"), d("2026-09-01"), rule(0, true, false, "CALENDAR", Set.of()))
            .countedDays());
  }

  @Test
  void includedSameDay() {
    assertEquals(
        1,
        ChargePolicy.calculate(
                d("2026-09-01"), d("2026-09-01"), rule(0, true, true, "CALENDAR", Set.of()))
            .countedDays());
  }

  @Test
  void cutoffSurvivesEarlyReturn() {
    assertEquals(
        d("2026-09-07"),
        ChargePolicy.calculate(
                d("2026-09-01"), d("2026-09-02"), rule(7, true, false, "CALENDAR", Set.of()))
            .lastFreeDate());
  }

  @Test
  void invalidDates() {
    assertThrows(
        Problem.class,
        () ->
            ChargePolicy.calculate(
                d("2026-09-02"), d("2026-09-01"), rule(0, true, false, "CALENDAR", Set.of())));
    assertThrows(
        Problem.class,
        () ->
            ChargePolicy.calculate(
                d("2020-01-01"), d("2026-09-01"), rule(0, true, false, "CALENDAR", Set.of())));
  }
}
