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

package org.anhonesteffort.flock.sync;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.CorrectPasswordActivity;
import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.ManageSubscriptionActivity;
import org.anhonesteffort.flock.R;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

/**
 * Programmer: rhodey
 */
public abstract class AbstractDavSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = "org.anhonesteffort.flock.sync.AbstractDavSyncAdapter";

  private static final int ID_NOTIFICATION_AUTH         = 1020;
  private static final int ID_NOTIFICATION_SUBSCRIPTION = 1021;

  private static final String PREFERENCES_NAME            = "AbstractDavSyncAdapter.PREFERENCES_NAME";
  private static final String KEY_VOID_AUTH_NOTIFICATIONS = "KEY_VOID_AUTH_NOTIFICATIONS";

  public static void handleException(Context context, Exception e, SyncResult result) {
    if (e instanceof DavException) {
      DavException ex = (DavException) e;
      Log.e(TAG, "error code: " + ex.getErrorCode() + ", status phrase: " + ex.getStatusPhrase(), e);

      if (ex.getErrorCode() == DavServletResponse.SC_UNAUTHORIZED)
        result.stats.numAuthExceptions++;
      else if (ex.getErrorCode() == OwsWebDav.STATUS_PAYMENT_REQUIRED)
        result.stats.numSkippedEntries++;
      else if (ex.getErrorCode() != DavServletResponse.SC_PRECONDITION_FAILED)
        result.stats.numParseExceptions++;
    }

    else if (e instanceof InvalidComponentException) {
      InvalidComponentException ex = (InvalidComponentException) e;
      result.stats.numParseExceptions++;
      Log.e(TAG, ex.toString(), ex);
    }

    // server is giving us funky stuff...
    else if (e instanceof PropertyParseException) {
      PropertyParseException ex = (PropertyParseException) e;
      result.stats.numParseExceptions++;
      Log.e(TAG, ex.toString(), ex);
    }

    // client is doing funky stuff...
    else if (e instanceof RemoteException || e instanceof OperationApplicationException)
      result.stats.numParseExceptions++;

    /*
      NOTICE: MAC errors are only expected upon initial import of encrypted key material.
      A MAC error here means there is remote content on a remote collection which has the
      encrypted key material property and valid encryption prefix but invalid ciphertext.
      TODO: should probably delete this property or component?
     */
    else if (e instanceof InvalidMacException) {
      Log.e(TAG, "BAD MAC IN SYNC!!! 0.o ", e);
      result.stats.numParseExceptions++;
    }
    else if (e instanceof GeneralSecurityException) {
      Log.e(TAG, "crypto problems in sync 0.u ", e);
      result.stats.numParseExceptions++;
    }

    else if (e instanceof SSLException) {
      Log.e(TAG, "SSL PROBLEM IN SYNC!!! 0.o ", e);
      result.stats.numIoExceptions++;
    }
    else if (e instanceof IOException) {
      Log.e(TAG, "who knows...", e);
      result.stats.numIoExceptions++;
    }

    else {
      result.stats.numParseExceptions++;
      Log.e(TAG, "DID NOT CATCH THIS EXCEPTION CORRECTLY!!! >> " + e.toString());
    }
  }

  public AbstractDavSyncAdapter(Context context) {
    super(context, true);
  }

  protected abstract AbstractSyncScheduler getSyncScheduler();

  protected abstract void handlePreSyncOperations(DavAccount            account,
                                                  MasterCipher          masterCipher,
                                                  ContentProviderClient provider)
      throws PropertyParseException, InvalidMacException, DavException,
             RemoteException, GeneralSecurityException, IOException;

  protected abstract List<AbstractDavSyncWorker> getSyncWorkers(DavAccount            account,
                                                                MasterCipher          masterCipher,
                                                                ContentProviderClient client,
                                                                SyncResult            syncResult)
      throws DavException, RemoteException, IOException;

  protected abstract void handlePostSyncOperations(DavAccount            account,
                                                   MasterCipher          masterCipher,
                                                   ContentProviderClient provider)
      throws PropertyParseException, InvalidMacException, DavException,
      RemoteException, GeneralSecurityException, IOException;

  @Override
  public void onPerformSync(Account               account,
                            Bundle                extras,
                            String                authority,
                            ContentProviderClient provider,
                            SyncResult            syncResult)
  {
    Log.d(TAG, "performing sync for authority >> " + authority);

    if (!getSyncScheduler().getIsSyncEnabled(account)) {
      Log.w(TAG, "sync disabled for authority " + authority + ", not gonna sync");
      return;
    }

    Optional<DavAccount> davAccount = DavAccountHelper.getAccount(getContext());
    if (!davAccount.isPresent()) {
      Log.d(TAG, "dav account is missing");
      syncResult.stats.numAuthExceptions++;
      showNotifications(syncResult);

      Log.d(TAG, "completed sync for authority >> " + authority);
      return ;
    }

    try {

      Optional<MasterCipher> masterCipher = KeyHelper.getMasterCipher(getContext());
      if (!masterCipher.isPresent()) {
        Log.d(TAG, "master cipher is missing");
        syncResult.stats.numAuthExceptions++;
        showNotifications(syncResult);

        Log.d(TAG, "completed sync for authority >> " + authority);
        return ;
      }

      handlePreSyncOperations(davAccount.get(), masterCipher.get(), provider);

      List<AbstractDavSyncWorker> workers = getSyncWorkers(davAccount.get(), masterCipher.get(), provider, syncResult);

      if (workers.size() > 0) {
        Log.d(TAG, "starting new thread executor service for " + workers.size() + " sync workers.");
        ExecutorService executor = Executors.newFixedThreadPool(workers.size());

        for (AbstractDavSyncWorker worker : workers)
          executor.execute(worker);

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.MINUTES);

        for (AbstractDavSyncWorker worker : workers)
          worker.remoteCollection.closeHttpConnection();
      }

      handlePostSyncOperations(davAccount.get(), masterCipher.get(), provider);
      getSyncScheduler().setTimeLastSync(new Date().getTime());

    } catch (PropertyParseException e) {
      handleException(getContext(), e, syncResult);
    } catch (DavException e) {
      handleException(getContext(), e, syncResult);
    } catch (RemoteException e) {
      handleException(getContext(), e, syncResult);
    } catch (InvalidMacException e) {
      handleException(getContext(), e, syncResult);
    } catch (GeneralSecurityException e) {
      handleException(getContext(), e, syncResult);
    } catch (IOException e) {
      handleException(getContext(), e, syncResult);
    } catch (InterruptedException e) {
      handleException(getContext(), e, syncResult);
    }

    Log.d(TAG, "completed sync for authority >> " + authority);
    showNotifications(syncResult);
  }

  public static void disableAuthNotificationsForRunningAdapters(Context context, Account account) {
    AddressbookSyncScheduler addressbookSync = new AddressbookSyncScheduler(context);
    CalendarsSyncScheduler   calendarSync    = new CalendarsSyncScheduler(context);
    KeySyncScheduler         keySync         = new KeySyncScheduler(context);
    SharedPreferences        settings        = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);

    if (addressbookSync.syncInProgress(account)) {
      settings.edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + addressbookSync.getAuthority(), true).commit();
      Log.e(TAG, "disabling auth notifications for " + addressbookSync.getAuthority());
    }

    if (calendarSync.syncInProgress(account)) {
      settings.edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + calendarSync.getAuthority(), true).commit();
      Log.e(TAG, "disabling auth notifications for " + calendarSync.getAuthority());
    }

    if (keySync.syncInProgress(account)) {
      settings.edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + keySync.getAuthority(), true).commit();
      Log.e(TAG, "disabling auth notifications for " + keySync.getAuthority());
    }
  }

  private boolean isAuthNotificationDisabled() {
    SharedPreferences settings = getContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);

    if (settings.getBoolean(KEY_VOID_AUTH_NOTIFICATIONS + getSyncScheduler().getAuthority(), false)) {
      settings.edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + getSyncScheduler().getAuthority(), false).commit();
      Log.e(TAG, "auth notification is disabled for " + getSyncScheduler().getAuthority());
      return true;
    }

    Log.e(TAG, "auth notification is not disabled for " + getSyncScheduler().getAuthority());
    return false;
  }

  public static void showAuthNotificationAndInvalidatePassword(Context context) {
    Log.d(TAG, "showAuthNotificationAndInvalidatePassword()");

    DavAccountHelper.invalidateAccountPassword(context);
    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);

    notificationBuilder.setContentTitle(context.getString(R.string.notification_flock_login_error));
    notificationBuilder.setContentText(context.getString(R.string.notification_tap_to_correct_password));
    notificationBuilder.setSmallIcon(R.drawable.alert_warning_light);
    notificationBuilder.setAutoCancel(true);

    Intent        clickIntent   = new Intent(context, CorrectPasswordActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(context,
                                                            0,
                                                            clickIntent,
                                                            PendingIntent.FLAG_UPDATE_CURRENT);
    notificationBuilder.setContentIntent(pendingIntent);

    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.notify(ID_NOTIFICATION_AUTH, notificationBuilder.build());
  }

  public static void cancelAuthNotification(Context context) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.cancel(ID_NOTIFICATION_AUTH);
  }

  public static void showSubscriptionExpiredNotification(Context context) {
    Log.d(TAG, "showSubscriptionExpiredNotification()");

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);

    notificationBuilder.setContentTitle(context.getString(R.string.notification_flock_subscription_expired));
    notificationBuilder.setContentText(context.getString(R.string.notification_tap_to_update_subscription));
    notificationBuilder.setSmallIcon(R.drawable.alert_warning_light);
    notificationBuilder.setAutoCancel(true);

    Intent               clickIntent = new Intent(context, ManageSubscriptionActivity.class);
    Optional<DavAccount> account     = DavAccountHelper.getAccount(context);

    clickIntent.putExtra(ManageSubscriptionActivity.KEY_DAV_ACCOUNT_BUNDLE, account.get().toBundle());

    PendingIntent pendingIntent = PendingIntent.getActivity(context,
                                                            0,
                                                            clickIntent,
                                                            PendingIntent.FLAG_UPDATE_CURRENT);
    notificationBuilder.setContentIntent(pendingIntent);

    NotificationManager notificationManager =
        (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.notify(ID_NOTIFICATION_SUBSCRIPTION, notificationBuilder.build());
  }

  public static void cancelSubscriptionExpiredNotification(Context context) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.cancel(ID_NOTIFICATION_SUBSCRIPTION);
  }

  private void showNotifications(SyncResult result) {
    if (result.stats.numAuthExceptions > 0 && !isAuthNotificationDisabled())
      showAuthNotificationAndInvalidatePassword(getContext());
    if (result.stats.numSkippedEntries > 0)
      showSubscriptionExpiredNotification(getContext());
  }

}
