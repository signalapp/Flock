/*
 * *
 *  Copyright (C) 2015 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */
package org.anhonesteffort.flock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * rhodey
 */
public class EolNotifier extends BroadcastReceiver {

  public static final String INTENT_ALARM_24_HOURS = "org.anhonesteffort.flock.INTENT_ALARM_24_HOURS";
  public static final String KEY_TIME_LAST_ALARM   = "KEY_TIME_LAST_ALARM";

  private static final String TAG = EolNotifier.class.getSimpleName();

  private Long getMsSinceLastAlarm(Context context) {
    SharedPreferences preferences   = PreferenceManager.getDefaultSharedPreferences(context);
    Long              timeLastAlarm = preferences.getLong(KEY_TIME_LAST_ALARM, -1);

    if (timeLastAlarm < 0 || timeLastAlarm > System.currentTimeMillis())
      return AlarmManager.INTERVAL_DAY;

    return System.currentTimeMillis() - timeLastAlarm;
  }

  private void handleDeviceBooted(Context context) {
    Long msSinceLastAlarm = getMsSinceLastAlarm(context);
    Long msTillNextAlarm  = AlarmManager.INTERVAL_DAY - msSinceLastAlarm;

    if (msTillNextAlarm < 0)
      msTillNextAlarm = 0L;

    Intent        alarmIntent  = new Intent(INTENT_ALARM_24_HOURS);
    PendingIntent pendingAlarm = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);
    AlarmManager  alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

    alarmManager.setInexactRepeating(
        AlarmManager.RTC,
        System.currentTimeMillis() + msTillNextAlarm,
        AlarmManager.INTERVAL_DAY,
        pendingAlarm
    );

    Log.d(TAG, "scheduled 24 hour alarm to begin firing repeatedly in " + msTillNextAlarm + "ms");
  }

  private void handleAlarmFired(Context context) {
    Log.d(TAG, "EOL alarm fired");
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    preferences.edit().putLong(KEY_TIME_LAST_ALARM, System.currentTimeMillis()).apply();
    NotificationDrawer.handleNotifyEol(context);
  }

  private void scheduleAlarmIfNotExists(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (preferences.getLong(KEY_TIME_LAST_ALARM, -1) == -1L)
      handleDeviceBooted(context);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
      Intent nextIntent = new Intent(context, EolActivity.class);
      nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      nextIntent.putExtra(EolActivity.EXTRA_BACK_DISABLED, true);
      context.startActivity(nextIntent);
      scheduleAlarmIfNotExists(context);
    }
    else if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
      handleDeviceBooted(context);
    else if (intent.getAction().equals(INTENT_ALARM_24_HOURS))
      handleAlarmFired(context);
    else
      Log.e(TAG, "received broadcast intent with unknown action " + intent.getAction());
  }

}
