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
import android.content.OperationApplicationException;
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

  public static void handleException(Context context, Exception e, SyncResult result) {
    if (e instanceof DavException) {
      DavException ex = (DavException) e;
      Log.e(TAG, "error code: " + ex.getErrorCode() + ", status phrase: " + ex.getStatusPhrase(), e);

      if (ex.getErrorCode() == DavServletResponse.SC_UNAUTHORIZED)
        result.stats.numAuthExceptions++;
      else if (ex.getErrorCode() == OwsWebDav.STATUS_PAYMENT_REQUIRED)
        result.stats.numSkippedEntries++;
      else if (ex.getErrorCode() != DavServletResponse.SC_PRECONDITION_FAILED)
        result.stats.numConflictDetectedExceptions++;
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
    else if (e instanceof RemoteException || e instanceof OperationApplicationException) {
      result.stats.numParseExceptions++;
      Log.e(TAG, e.toString(), e);
    }

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

    else if (!(e instanceof InterruptedException)) {
      result.stats.numIoExceptions++;
      Log.e(TAG, "DID NOT CATCH THIS EXCEPTION CORRECTLY!!! >> " + e.toString(), e);
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

    syncResult.stats.numAuthExceptions =
        syncResult.stats.numConflictDetectedExceptions =
            syncResult.stats.numIoExceptions =
                syncResult.stats.numParseExceptions = 0;

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
