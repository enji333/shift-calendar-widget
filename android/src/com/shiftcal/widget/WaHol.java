package com.shiftcal.widget;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Western Australian public holidays (v3.22), worked out from their rules so no list goes out of date.
 *  A weekend New Year's Day, Australia Day or Anzac Day also gives the Monday after; Christmas and Boxing Day
 *  move to the next weekdays. The King's Birthday is the last Monday in September (WA's usual proclamation). */
final class WaHol {
    private static final Map<Integer, Set<LocalDate>> CACHE = new HashMap<Integer, Set<LocalDate>>();
    static synchronized boolean is(LocalDate d) {
        Set<LocalDate> s = CACHE.get(d.getYear());
        if (s == null) { s = year(d.getYear()); CACHE.put(d.getYear(), s); }
        return s.contains(d);
    }
    static Set<LocalDate> year(int y) {
        Set<LocalDate> s = new HashSet<LocalDate>();
        weekendToMonday(s, LocalDate.of(y, 1, 1));
        weekendToMonday(s, LocalDate.of(y, 1, 26));
        s.add(LocalDate.of(y, 3, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)));        // Labour Day
        LocalDate easter = easter(y);
        s.add(easter.minusDays(2)); s.add(easter); s.add(easter.plusDays(1));                         // Good Friday, Easter Sunday, Easter Monday
        weekendToMonday(s, LocalDate.of(y, 4, 25));                                                    // Anzac Day
        s.add(LocalDate.of(y, 6, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)));        // Western Australia Day
        s.add(LocalDate.of(y, 9, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)));         // King's Birthday
        LocalDate xmas = LocalDate.of(y, 12, 25), box = LocalDate.of(y, 12, 26);
        s.add(xmas); s.add(box);
        DayOfWeek x = xmas.getDayOfWeek();
        if (x == DayOfWeek.FRIDAY) s.add(LocalDate.of(y, 12, 28));                                    // Boxing Day on Saturday
        else if (x == DayOfWeek.SATURDAY) { s.add(LocalDate.of(y, 12, 27)); s.add(LocalDate.of(y, 12, 28)); }
        else if (x == DayOfWeek.SUNDAY) s.add(LocalDate.of(y, 12, 27));
        return s;
    }
    private static void weekendToMonday(Set<LocalDate> s, LocalDate d) {
        s.add(d);
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) s.add(d.plusDays(2));
        else if (d.getDayOfWeek() == DayOfWeek.SUNDAY) s.add(d.plusDays(1));
    }
    /** Western (Gregorian) Easter Sunday, anonymous Gregorian algorithm. */
    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }
}
