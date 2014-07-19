/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
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

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.anhonesteffort.flock.sync.calendar.CalendarCopiedListener;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.LocalEventCollection;

import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class CalendarCopyService extends Service implements CalendarCopiedListener {

  private static final String TAG = "org.anhonesteffort.flock.CalendarCopyService";

  protected static final String ACTION_QUEUE_ACCOUNT_FOR_COPY = "CalendarCopyService.ACTION_QUEUE_ACCOUNT_FOR_COPY";
  protected static final String ACTION_START_COPY             = "CalendarCopyService.ACTION_START_COPY";

  private static final String KEY_INTENT              = "CalendarCopyService.KEY_INTENT";
  private static final String KEY_ACCOUNT_WITH_ERROR  = "CalendarCopyService.KEY_ACCOUNT_WITH_ERROR";
  private static final String KEY_ACCOUNT_ERROR_COUNT = "CalendarCopyService.KEY_ACCOUNT_ERROR_COUNT";

  protected static final String KEY_FROM_ACCOUNT   = "CalendarCopyService.KEY_FROM_ACCOUNT";
  protected static final String KEY_TO_ACCOUNT     = "CalendarCopyService.KEY_TO_ACCOUNT";
  protected static final String KEY_CALENDAR_ID    = "CalendarCopyService.KEY_CALENDAR_ID";
  protected static final String KEY_CALENDAR_NAME  = "CalendarCopyService.KEY_CALENDAR_NAME";
  protected static final String KEY_CALENDAR_COLOR = "CalendarCopyService.KEY_CALENDAR_COLOR";
  protected static final String KEY_EVENT_COUNT    = "CalendarCopyService.KEY_EVENT_COUNT";

  private static final int ID_CALENDAR_COPY_NOTIFICATION = 1024;

  // lol, no regrets
  private final List<AccountForCopy>  accountsForCopy  = new LinkedList<AccountForCopy>();
  private final List<CalendarForCopy> calendarsForCopy = new LinkedList<CalendarForCopy>();

  private Looper                     serviceLooper;
  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;
  private List<Bundle>               accountErrors;

  private int countEventsToCopy      = 0;
  private int countEventsCopied      = 0;
  private int countEventCopiesFailed = 0;

  private void handleInitializeEventCopyNotification() {
    Log.d(TAG, "handleInitializeEventCopyNotification()");

    notificationBuilder.setContentTitle(getString(R.string.notification_calendar_import))
        .setContentText(getString(R.string.notification_importing_calendars))
        .setProgress(100, 1, false)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(ID_CALENDAR_COPY_NOTIFICATION, notificationBuilder.build());
  }

  private void handleCopyComplete() {
    Log.d(TAG, "handleCopyComplete()");

    new CalendarsSyncScheduler(getBaseContext()).requestSync();
    stopForeground(false);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");

    if (countEventCopiesFailed == 0) {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentText(getString(R.string.notification_import_complete_copied) +
                          " " + countEventsCopied + " " + getString(R.string.events) +
                          getString(R.string.period));
    }
    else {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentText(getString(R.string.notification_import_complete_copied) +
                          " " + countEventCopiesFailed + " " + getString(R.string.events) +
                          ", " + countEventCopiesFailed + " " + getString(R.string.failed) +
                          getString(R.string.period));
    }

    notifyManager.notify(ID_CALENDAR_COPY_NOTIFICATION, notificationBuilder.build());
  }

  private void handleEventCopied(Account fromAccount) {
    countEventsCopied++;
    Log.d(TAG, "handleEventCopied() events copied: " + countEventsCopied);

    notificationBuilder
        .setContentText(getString(R.string.notification_importing_events_from) +
                        " " + fromAccount.name)
        .setProgress(countEventsToCopy,
                     countEventsCopied + countEventCopiesFailed,
                     false);

    notifyManager.notify(ID_CALENDAR_COPY_NOTIFICATION, notificationBuilder.build());
  }

  private void handleEventCopyFailed(Account fromAccount) {
    countEventCopiesFailed++;
    Log.d(TAG, "handleEventCopyFailed() event copies failed: " + countEventCopiesFailed);

    boolean accountWasFound = false;
    for (Bundle accountError : accountErrors) {
      Account accountWithError = accountError.getParcelable(KEY_ACCOUNT_WITH_ERROR);
      Integer errorCount       = accountError.getInt(KEY_ACCOUNT_ERROR_COUNT, -1);

      if (accountWithError != null && errorCount > 0) {
        if (fromAccount.equals(accountWithError)) {
          accountError.putInt(KEY_ACCOUNT_ERROR_COUNT, errorCount + 1);
          accountWasFound = true;
          break;
        }
      }
    }

    if (!accountWasFound) {
      Bundle errorBundle = new Bundle();
      errorBundle.putParcelable(KEY_ACCOUNT_WITH_ERROR, fromAccount);
      errorBundle.putInt(KEY_ACCOUNT_ERROR_COUNT, 1);

      accountErrors.add(errorBundle);
    }

    notificationBuilder
        .setContentText(getString(R.string.notification_importing_events_from) +
                        " " + fromAccount.name)
        .setProgress(countEventsToCopy,
                     countEventsCopied + countEventCopiesFailed,
                     false);

    notifyManager.notify(ID_CALENDAR_COPY_NOTIFICATION, notificationBuilder.build());
  }

  private void handleQueueCalendarForCopy(Intent intent) {
    Log.d(TAG, "handleQueueCalendarForCopy()");

    Account fromAccount   = intent.getParcelableExtra(KEY_FROM_ACCOUNT);
    Account toAccount     = intent.getParcelableExtra(KEY_TO_ACCOUNT);
    Long    calendarId    = intent.getLongExtra(KEY_CALENDAR_ID, -1);
    String  calendarName  = intent.getStringExtra(KEY_CALENDAR_NAME);
    Integer calendarColor = intent.getIntExtra(KEY_CALENDAR_COLOR, 0);
    Integer eventCount    = intent.getIntExtra(KEY_EVENT_COUNT,  -1);

    if (fromAccount == null  || toAccount == null || calendarId < 0 ||
        calendarName == null || eventCount < 0)
    {
      Log.e(TAG, "failed to parse to account, from account, calendar id, calendar " +
                  "label, calendar color or event count from intent extras.");
      return;
    }

    accountsForCopy.add(new AccountForCopy(fromAccount, toAccount, calendarId, calendarName, calendarColor));
    calendarsForCopy.add(new CalendarForCopy(fromAccount, calendarId, eventCount));
    countEventsToCopy += eventCount;

    Log.d(TAG, "events to copy: " + countEventsToCopy);
  }

  private void handleStartCopy() {
    Log.d(TAG, "handleStartCopy()");

    handleInitializeEventCopyNotification();

    ContentProviderClient client = getBaseContext().getContentResolver()
        .acquireContentProviderClient(CalendarsSyncScheduler.CONTENT_AUTHORITY);

    for (AccountForCopy accountForCopy : accountsForCopy) {
      Account fromAccount   = accountForCopy.fromAccount;
      Account toAccount     = accountForCopy.toAccount;
      Long    calendarId    = accountForCopy.calendarId;
      String  calendarName  = accountForCopy.calendarName;
      int     calendarColor = accountForCopy.calendarColor;

      LocalEventCollection eventCollection =
          new LocalEventCollection(client, fromAccount, calendarId, "hack");

      try {

        eventCollection.copyToAccount(toAccount, calendarName, calendarColor, this);

      } catch (RemoteException e) {
        ErrorToaster.handleShowError(getBaseContext(), e);
        return;
      }
    }

    handleCopyComplete();
  }

  @Override
  public void onCalendarCopied(Account fromAccount, Account toAccount, Long calendarId) {
    Log.d(TAG, "onCalendarCopied() from " + fromAccount + ", " + calendarId +
               " to " + toAccount);
  }

  @Override
  public void onCalendarCopyFailed(Exception e,
                                   Account   fromAccount,
                                   Account   toAccount,
                                   Long      localId)
  {
    Log.e(TAG, "onCalendarCopyFailed() from " + fromAccount + ", " + localId +
                " to " + toAccount, e);

    int     failedEvents     = 0;
    boolean calendarWasFound = false;

    for (CalendarForCopy calendarForCopy : calendarsForCopy) {
      Account calendarAccount = calendarForCopy.fromAccount;
      Long    calendarId      = calendarForCopy.calendarId;
      Integer eventCount      = calendarForCopy.eventCount;

      if (calendarAccount.equals(fromAccount) && calendarId.equals(localId)) {
        failedEvents     = eventCount;
        calendarWasFound = true;
        break;
      }
    }

    if (calendarWasFound) {
      for (int i = 0; i < failedEvents; i++)
        handleEventCopyFailed(fromAccount);
    }
  }

  @Override
  public void onEventCopied(Account fromAccount, Account toAccount, Long calendarId) {
    Log.d(TAG, "onEventCopied() from " + fromAccount + ", " + calendarId +
                " to " + toAccount);
    handleEventCopied(fromAccount);
  }

  @Override
  public void onEventCopyFailed(Exception e,
                                Account   fromAccount,
                                Account   toAccount,
                                Long      calendarId)
  {
    Log.e(TAG, "onEventCopyFailed() from " + fromAccount + ", " + calendarId +
               " to " + toAccount, e);
    handleEventCopyFailed(fromAccount);
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("CalendarCopyService", HandlerThread.NORM_PRIORITY);
    thread.start();

    serviceLooper  = thread.getLooper();
    serviceHandler = new ServiceHandler(serviceLooper);

    notifyManager       = (NotificationManager)getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);
    notificationBuilder = new NotificationCompat.Builder(getBaseContext());
    accountErrors       = new LinkedList<Bundle>();
  }

  private final class ServiceHandler extends Handler {

    public ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      Log.d(TAG, "handleMessage()");
      Intent intent = msg.getData().getParcelable(KEY_INTENT);

      if (intent != null && intent.getAction() != null) {
        if (intent.getAction().equals(ACTION_QUEUE_ACCOUNT_FOR_COPY))
          handleQueueCalendarForCopy(intent);
        else if (intent.getAction().equals(ACTION_START_COPY))
          handleStartCopy();
        else
          Log.e(TAG, "received intent with unknown action");
      }
      else
        Log.e(TAG, "received message with null intent or intent action");
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Message msg = serviceHandler.obtainMessage();
    msg.getData().putParcelable(KEY_INTENT, intent);
    serviceHandler.sendMessage(msg);

    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "onBind()");
    return null;
  }

  private static class AccountForCopy {

    public Account fromAccount;
    public Account toAccount;
    public long    calendarId;
    public String  calendarName;
    public int     calendarColor;

    public AccountForCopy(Account fromAccount,
                          Account toAccount,
                          long    calendarId,
                          String  calendarName,
                          int     calendarColor)
    {
      this.fromAccount   = fromAccount;
      this.toAccount     = toAccount;
      this.calendarId    = calendarId;
      this.calendarName  = calendarName;
      this.calendarColor = calendarColor;
    }

  }

  private static class CalendarForCopy {

    public Account fromAccount;
    public long    calendarId;
    public int     eventCount;

    public CalendarForCopy(Account fromAccount,
                          long     calendarId,
                          int      eventCount)
    {
      this.fromAccount  = fromAccount;
      this.calendarId   = calendarId;
      this.eventCount   = eventCount;
    }

  }

}
