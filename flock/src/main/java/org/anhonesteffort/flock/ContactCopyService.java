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
import android.util.Pair;

import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.ContactCopiedListener;
import org.anhonesteffort.flock.sync.addressbook.LocalContactCollection;

import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class ContactCopyService extends Service implements ContactCopiedListener {

  private static final String TAG = "org.anhonesteffort.flock.ContactCopyService";

  protected static final String ACTION_QUEUE_ACCOUNT_FOR_COPY = "ContactCopyService.ACTION_QUEUE_ACCOUNT_FOR_COPY";
  protected static final String ACTION_START_COPY             = "ContactCopyService.ACTION_START_COPY";

  private static final String KEY_INTENT              = "ContactCopyService.KEY_INTENT";
  private static final String KEY_ACCOUNT_WITH_ERROR  = "ContactCopyService.KEY_ACCOUNT_WITH_ERROR";
  private static final String KEY_ACCOUNT_ERROR_COUNT = "ContactCopyService.KEY_ACCOUNT_ERROR_COUNT";

  protected static final String KEY_FROM_ACCOUNT  = "ContactCopyService.KEY_FROM_ACCOUNT";
  protected static final String KEY_TO_ACCOUNT    = "ContactCopyService.KEY_TO_ACCOUNT";
  protected static final String KEY_CONTACT_COUNT = "ContactCopyService.KEY_CONTACT_COUNT";

  private final List<Pair<Account, Account>> accountsForCopy = new LinkedList<Pair<Account, Account>>();

  private Looper                     serviceLooper;
  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;
  private List<Bundle>               accountErrors;

  private int countContactsToCopy      = 0;
  private int countContactsCopied      = 0;
  private int countContactCopiesFailed = 0;

  private void handleInitializeNotification() {
    Log.d(TAG, "handleInitializeNotification()");

    notificationBuilder.setContentTitle(getString(R.string.notification_contact_import))
        .setContentText(getString(R.string.notification_importing_contacts))
        .setProgress(100, 1, false)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(1023, notificationBuilder.build());
  }

  private void handleContactCopied(Account fromAccount) {
    countContactsCopied++;
    Log.d(TAG, "handleContactCopied() contacts copied: " + countContactsCopied);

    notificationBuilder
        .setContentText(getString(R.string.notification_importing_contacts_from,
                                 ((fromAccount != null) ? fromAccount.name : getString(R.string.local_storage))))
        .setProgress(countContactsToCopy, countContactsCopied + countContactCopiesFailed, false);

    notifyManager.notify(1023, notificationBuilder.build());
  }

  private void handleContactCopyFailed(Account fromAccount) {
    countContactCopiesFailed++;
    Log.d(TAG, "handleContactCopyFailed() contact copies failed: " + countContactCopiesFailed);

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
        .setContentText(getString(R.string.notification_importing_contacts_from, fromAccount.name))
        .setProgress(countContactsToCopy, countContactsCopied + countContactCopiesFailed, false);

    notifyManager.notify(1023, notificationBuilder.build());
  }

  private void handleCopyComplete() {
    Log.d(TAG, "handleCopyComplete()");

    new AddressbookSyncScheduler(getBaseContext()).requestSync();
    stopForeground(false);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");

    if (countContactCopiesFailed == 0) {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentText(getString(R.string.notification_import_complete_copied_contacts, countContactsCopied));
    }
    else {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentText(getString(R.string.notification_import_complete_copied_contacts_failed,
                                    countContactsCopied, countContactCopiesFailed));
    }

    notifyManager.notify(1023, notificationBuilder.build());
  }

  private void handleQueueAccountForCopy(Intent intent) {
    Log.d(TAG, "handleQueueAccountForCopy()");

    Account fromAccount  = intent.getParcelableExtra(KEY_FROM_ACCOUNT);
    Account toAccount    = intent.getParcelableExtra(KEY_TO_ACCOUNT);
    Integer contactCount = intent.getIntExtra(KEY_CONTACT_COUNT, -1);

    if (fromAccount == null || toAccount == null || contactCount < 0) {
      Log.e(TAG, "failed to parse to account, from account, or contact count from intent extras.");
      return;
    }

    accountsForCopy.add(new Pair<Account, Account>(fromAccount, toAccount));
    countContactsToCopy += contactCount;

    Log.d(TAG, "contacts to copy: " + countContactsToCopy);
  }

  private void handleStartCopy() {
    Log.d(TAG, "handleStartCopy()");
    handleInitializeNotification();

    ContentProviderClient client = getBaseContext().getContentResolver()
        .acquireContentProviderClient(AddressbookSyncScheduler.CONTENT_AUTHORITY);

    for (Pair<Account, Account> copyPair : accountsForCopy) {
      LocalContactCollection fromCollection = null;

      if (!copyPair.first.type.equals(getString(R.string.local_storage)))
        fromCollection = new LocalContactCollection(getBaseContext(), client, copyPair.first, "hack");
      else
        fromCollection = new LocalContactCollection(getBaseContext(), client, null, "hack");

      try {

        fromCollection.copyToAccount(copyPair.second, this);

      } catch (RemoteException e) {
        ErrorToaster.handleShowError(getBaseContext(), e);
        return;
      }
    }

    handleCopyComplete();
  }

  @Override
  public void onContactCopied(Account fromAccount, Account toAccount) {
    handleContactCopied(fromAccount);
  }

  @Override
  public void onContactCopyFailed(Exception e, Account fromAccount, Account toAccount) {
    Log.e(TAG, "onContactCopyFailed() from " +
               ((fromAccount != null) ? fromAccount.name : getString(R.string.local_storage)) +
               " to " +
               ((toAccount != null) ? toAccount.name : getString(R.string.local_storage)), e);
    handleContactCopyFailed(fromAccount);
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("ContactCopyService", HandlerThread.NORM_PRIORITY);
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
          handleQueueAccountForCopy(intent);
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

}
