/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.anhonesteffort.flock.sync.account;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.sync.AbstractSyncAdapter;
import org.anhonesteffort.flock.sync.SyncWorker;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;

/**
 * rhodey
 */
public class AccountSyncService extends Service {

  private   static       AccountSyncAdapter   sSyncAdapter     = null;
  private   static final Object               sSyncAdapterLock = new Object();

  @Override
  public void onCreate() {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null)
        sSyncAdapter = new AccountSyncAdapter(getApplicationContext());
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }

  private class AccountSyncAdapter extends AbstractSyncAdapter {

    public AccountSyncAdapter(Context context) {
      super(context);
    }

    @Override
    protected AccountSyncScheduler getSyncScheduler() {
      return new AccountSyncScheduler(getContext());
    }

    @Override
    protected boolean localHasChanged() throws RemoteException {
      return false;
    }

    @Override
    protected void handlePreSyncOperations()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {

    }

    @Override
    protected List<SyncWorker> getSyncWorkers(boolean localChangesOnly)
        throws DavException, RemoteException, RegistrationApiException, IOException
    {
      List<SyncWorker> workers = new LinkedList<>();

      if (DavAccountHelper.isUsingOurServers(davAccount))
        workers.add(new AccountSyncWorker(getContext(), davAccount, syncResult));

      return workers;
    }

    @Override
    protected void handlePostSyncOperations()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {

    }
  }
}
