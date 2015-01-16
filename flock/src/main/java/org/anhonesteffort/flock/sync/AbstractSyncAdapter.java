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
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.NotificationDrawer;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Programmer: rhodey
 */
public abstract class AbstractSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = "org.anhonesteffort.flock.sync.AbstractSyncAdapter";

  protected ContentProviderClient provider;
  protected SyncResult            syncResult;
  protected DavAccount            davAccount;
  protected MasterCipher          masterCipher;

  public AbstractSyncAdapter(Context context) {
    super(context, true);
  }

  protected boolean syncIntervalHasPassed() {
    long           syncIntervalMs = getSyncScheduler().getSyncIntervalMinutes() * 60 * 1000;
    Optional<Long> timeLastSyncMs = getSyncScheduler().getTimeLastSync();
    long           timeNowMs      = new Date().getTime();

    return !timeLastSyncMs.isPresent() || (timeNowMs - syncIntervalMs) >= timeLastSyncMs.get();
  }

  protected abstract AbstractSyncScheduler getSyncScheduler();

  protected abstract boolean localHasChanged() throws RemoteException;

  protected abstract void handlePreSyncOperations()
      throws PropertyParseException, InvalidMacException, DavException,
             RemoteException, GeneralSecurityException, IOException;

  protected abstract List<SyncWorker> getSyncWorkers(boolean localChangesOnly)
      throws DavException, RemoteException, IOException;

  protected abstract void handlePostSyncOperations()
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

    this.provider   = provider;
    this.syncResult = syncResult;

    syncResult.stats.numAuthExceptions =
        syncResult.stats.numConflictDetectedExceptions =
            syncResult.stats.numIoExceptions =
                syncResult.stats.numParseExceptions = 0;

    if (!getSyncScheduler().getIsSyncEnabled(account)) {
      Log.w(TAG, "sync disabled, not gonna sync " + authority);
      return;
    }

    Optional<DavAccount> davAccountOptional = DavAccountHelper.getAccount(getContext());
    if (!davAccountOptional.isPresent()) {
      Log.d(TAG, "dav account is missing, not gonna sync " + authority);
      syncResult.stats.numAuthExceptions++;
      showNotifications(syncResult);
      return ;
    }
    davAccount = davAccountOptional.get();

    try {

      Optional<MasterCipher> masterCipherOptional = KeyHelper.getMasterCipher(getContext());
      if (!masterCipherOptional.isPresent()) {
        Log.d(TAG, "master cipher is missing, not gonna sync " + authority);
        syncResult.stats.numAuthExceptions++;
        showNotifications(syncResult);
        return;
      }
      masterCipher = masterCipherOptional.get();

      boolean forceSync = extras.getBoolean(AbstractSyncScheduler.EXTRA_FORCE_SYNC, false);

      if (!forceSync && !syncIntervalHasPassed() && !localHasChanged()) {
        Log.d(TAG, "sync is not forced, interval has not passed, and local has not changed. " +
                   "not gonna sync " + authority);
        return ;
      }

      handlePreSyncOperations();
      List<SyncWorker> workers = getSyncWorkers(!forceSync && !syncIntervalHasPassed());

      if (workers.size() > 0) {
        Log.d(TAG, "starting thread executor service for " + workers.size() + " " +
                   authority + " sync workers.");
        ExecutorService executor = Executors.newFixedThreadPool(workers.size());

        for (SyncWorker worker : workers)
          executor.execute(worker);

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.MINUTES);

        for (SyncWorker worker : workers)
          worker.cleanup();
      }

      handlePostSyncOperations();
      getSyncScheduler().setTimeLastSync(new Date().getTime());

    } catch (PropertyParseException e) {
      SyncWorkerUtil.handleException(getContext(), e, syncResult);
    } catch (DavException e) {
      SyncWorkerUtil.handleException(getContext(), e, syncResult);
    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(getContext(), e, syncResult);
    } catch (InvalidMacException e) {
      SyncWorkerUtil.handleException(getContext(), e, syncResult);
    } catch (GeneralSecurityException e) {
      SyncWorkerUtil.handleException(getContext(), e, syncResult);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(getContext(), e, syncResult);
    } catch (InterruptedException e) {
      SyncWorkerUtil.handleException(getContext(), e, syncResult);
    }

    Log.d(TAG, "completed sync for authority >> " + authority);
    showNotifications(syncResult);
  }

  private void showNotifications(SyncResult result) {
    if (result.stats.numAuthExceptions > 0 &&
        !NotificationDrawer.isAuthNotificationDisabled(getContext(), getSyncScheduler().getAuthority()))
    {
      NotificationDrawer.handleInvalidatePasswordAndShowAuthNotification(getContext());
    }
    if (result.stats.numSkippedEntries > 0)
      NotificationDrawer.showSubscriptionExpiredNotification(getContext());
    if (result.stats.numParseExceptions > 0)
      NotificationDrawer.handlePromptForDebugLogIfNotDisabled(getContext());

    if (NotificationDrawer.isAuthNotificationDisabled(getContext(), getSyncScheduler().getAuthority()))
      NotificationDrawer.enableAuthNotifications(getContext(), getSyncScheduler().getAuthority());
  }

}
