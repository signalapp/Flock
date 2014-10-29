package org.anhonesteffort.flock.util;

/**
 * rhodey
 */
public class TimeUtil {

  public static long millisecondsToDays(long milliseconds) {
    return milliseconds / (1000 * 60 * 60 * 24);
  }

  public static long daysToMilliseconds(long days) {
    return days * 24 * 60 * 60 * 1000;
  }

}
