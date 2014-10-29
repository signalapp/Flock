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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.AbstractSyncAdapter;
import org.anhonesteffort.flock.sync.SyncWorker;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;

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

  private static class ContactsSyncAdapter extends AbstractSyncAdapter {

    public ContactsSyncAdapter(Context context) {
      super(context);
    }

    @Override
    protected AddressbookSyncScheduler getSyncScheduler() {
      return new AddressbookSyncScheduler(getContext());
    }

    @Override
    protected boolean localHasChanged() throws RemoteException {
      LocalAddressbookStore localStore =
          new LocalAddressbookStore(getContext(), provider,davAccount);

      for (LocalContactCollection localCollection : localStore.getCollections()) {
        if (localCollection.hasChanges())
          return true;
      }

      return false;
    }

    @Override
    protected void handlePreSyncOperations()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {
      Log.d(TAG, "handlePreSyncOperations()");
    }

    @Override
    protected List<SyncWorker> getSyncWorkers(boolean localChangesOnly)
        throws DavException, RemoteException, IOException
    {
      List<SyncWorker>      workers     = new LinkedList<SyncWorker>();
      LocalAddressbookStore localStore  = new LocalAddressbookStore(getContext(), provider, davAccount);
      HidingCardDavStore    remoteStore = DavAccountHelper.getHidingCardDavStore(getContext(), davAccount, masterCipher);

      try {

        for (LocalContactCollection localCollection : localStore.getCollections()) {
          Log.d(TAG, "found local collection " + localCollection.getPath());
          if (!localChangesOnly || localCollection.hasChanges()) {

            Optional<HidingCardDavCollection> remoteCollection =
                remoteStore.getCollection(localCollection.getPath());

            if (remoteCollection.isPresent()) {
              remoteCollection.get().setClient(
                  DavAccountHelper.getAndroidDavClient(getContext(), davAccount)
              );
              workers.add(
                  new AddressbookSyncWorker(getContext(), syncResult, localCollection, remoteCollection.get())
              );
            }
            else {
              Log.d(TAG, "local collection missing remotely, deleting locally");
              localStore.removeCollection(localCollection.getPath());
            }
          }
          else
            Log.d(TAG, "local collection " + localCollection.getPath() +
                       " does not have changes, skipping.");
        }

      } finally {
        remoteStore.releaseConnections();
      }

      return workers;
    }

    @Override
    protected void handlePostSyncOperations()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {
      Log.d(TAG, "handlePostSyncOperations()");
    }
  }
}
