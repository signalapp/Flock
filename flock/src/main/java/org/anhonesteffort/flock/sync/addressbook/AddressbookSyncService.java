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

package org.anhonesteffort.flock.sync.addressbook;

import android.accounts.Account;
import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.util.Date;

/**
 * Programmer: rhodey
 */
public class AddressbookSyncService extends Service {

  private static final String TAG = "org.anhonesteffort.flock.sync.addressbook.AddressbookSyncService";

  private static       ContactsSyncAdapter sSyncAdapter     = null;
  private static final Object              sSyncAdapterLock = new Object();

  @Override
  public void onCreate() {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null)
        sSyncAdapter = new ContactsSyncAdapter(getApplicationContext());
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }

  private static class ContactsSyncAdapter extends AbstractDavSyncAdapter {

    public ContactsSyncAdapter(Context context) {
      super(context);
    }

    protected String getAuthority() {
      return AddressbookSyncScheduler.CONTENT_AUTHORITY;
    }

    @Override
    public void onPerformSync(Account               account,
                              Bundle                extras,
                              String                authority,
                              ContentProviderClient provider,
                              SyncResult            syncResult)
    {
      Log.d(TAG, "performing sync for authority: " + authority + ", account: " + account.name);

      Optional<DavAccount> davAccount = DavAccountHelper.getAccount(getContext());
      if (!davAccount.isPresent()) {
        Log.d(TAG, "dav account is missing");
        syncResult.stats.numAuthExceptions++;
        showNotifications(syncResult);
        return;
      }

      try {

        Optional<MasterCipher> masterCipher = KeyHelper.getMasterCipher(getContext());
        if (!masterCipher.isPresent()) {
          Log.d(TAG, "master cipher is missing");
          syncResult.stats.numAuthExceptions++;
          return ;
        }

        LocalAddressbookStore localStore  = new LocalAddressbookStore(getContext(), provider, davAccount.get());
        HidingCardDavStore    remoteStore = DavAccountHelper.getHidingCardDavStore(getContext(), davAccount.get(), masterCipher.get());

        for (LocalContactCollection localCollection : localStore.getCollections()) {
          Log.d(TAG, "found local collection: " + localCollection.getPath());
          Optional<HidingCardDavCollection> remoteCollection = remoteStore.getCollection(localCollection.getPath());

          if (remoteCollection.isPresent())
            new AddressbookSyncWorker(getContext(), localCollection, remoteCollection.get()).run(syncResult, false);
          else {
            Log.d(TAG, "local collection missing remotely, deleting locally");
            localStore.removeCollection(localCollection.getPath());
          }
        }

        remoteStore.releaseConnections();

      } catch (IOException e) {
        handleException(getContext(), e, syncResult);
      } catch (DavException e) {
        handleException(getContext(), e, syncResult);
      }

      showNotifications(syncResult);
      new AddressbookSyncScheduler(getContext()).setTimeLastSync(new Date().getTime());
    }
  }

}
