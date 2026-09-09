package com.staffops.util;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
public final class TimeUtil {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withLocale(Locale.US).withZone(ZoneId.systemDefault());
    private TimeUtil() {}
    public static long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("permanent") || raw.equals("0")) return 0L;
        String s = raw.trim().toLowerCase(Locale.ROOT); long mult;
        if (s.endsWith("s")) mult=1000L; else if (s.endsWith("m")) mult=60_000L; else if (s.endsWith("h")) mult=3_600_000L; else if (s.endsWith("d")) mult=86_400_000L; else if (s.endsWith("w")) mult=604_800_000L; else throw new IllegalArgumentException("Invalid duration: "+raw);
        return Math.multiplyExact(Long.parseLong(s.substring(0,s.length()-1)), mult);
    }
    public static String formatDate(long millis) { return DATE.format(Instant.ofEpochMilli(millis)); }
    public static String compactDuration(long millis) {
        if (millis <= 0) return "permanent";
        Duration d=Duration.ofMillis(millis); long days=d.toDays(); if(days>0)return days+"d "+d.minusDays(days).toHours()+"h"; long h=d.toHours(); if(h>0)return h+"h "+d.minusHours(h).toMinutes()+"m"; long m=d.toMinutes(); if(m>0)return m+"m"; return Math.max(1,d.toSeconds())+"s";
    }
}
