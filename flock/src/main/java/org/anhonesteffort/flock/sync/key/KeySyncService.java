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

package org.anhonesteffort.flock.sync.key;

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
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;

import java.util.Date;

/**
 * Programmer: rhodey
 */
public class KeySyncService extends Service {

  private static final String TAG = "org.anhonesteffort.flock.sync.key.KeySyncService";

  private static       KeySyncAdapter sSyncAdapter     = null;
  private static final Object         sSyncAdapterLock = new Object();

  @Override
  public void onCreate() {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null)
        sSyncAdapter = new KeySyncAdapter(getApplicationContext());
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }

  private static class KeySyncAdapter extends AbstractDavSyncAdapter {

    public KeySyncAdapter(Context context) {
      super(context);
    }

    protected String getAuthority() {
      return KeySyncScheduler.CONTENT_AUTHORITY;
    }

    @Override
    public void onPerformSync(Account               account,
                              Bundle                extras,
                              String                authority,
                              ContentProviderClient provider,
                              SyncResult            syncResult)
    {
      Log.d(TAG, "performing sync for authority >> " + authority);

      Optional<DavAccount> davAccount = DavAccountHelper.getAccount(getContext());
      if (!davAccount.isPresent()) {
        syncResult.stats.numAuthExceptions++;
        showNotifications(syncResult);
        return;
      }

      new KeySyncWorker(getContext(), davAccount.get()).run(syncResult);

      showNotifications(syncResult);
      new KeySyncScheduler(getContext()).setTimeLastSync(new Date().getTime());
    }
  }

}
