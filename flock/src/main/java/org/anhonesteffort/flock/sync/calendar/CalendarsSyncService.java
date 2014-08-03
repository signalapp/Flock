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

package org.anhonesteffort.flock.sync.calendar;

import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.AbstractDavSyncWorker;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.PreferencesActivity;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Programmer: rhodey
 */
public class CalendarsSyncService extends Service {

  private static final String TAG = "org.anhonesteffort.flock.sync.calendar.CalendarsSyncService";

  private static       CalendarsSyncAdapter sSyncAdapter     = null;
  private static final Object               sSyncAdapterLock = new Object();

  @Override
  public void onCreate() {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null)
        sSyncAdapter = new CalendarsSyncAdapter(getApplicationContext());
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }

  private static class CalendarsSyncAdapter extends AbstractDavSyncAdapter {

    public CalendarsSyncAdapter(Context context) {
      super(context);
    }

    @Override
    protected String getAuthority() {
      return CalendarsSyncScheduler.CONTENT_AUTHORITY;
    }

    @Override
    protected void setTimeLastSync() {
      new CalendarsSyncScheduler(getContext()).setTimeLastSync(new Date().getTime());
    }

    @Override
    protected void handlePreSyncOperations(DavAccount            account,
                                           MasterCipher          masterCipher,
                                           ContentProviderClient provider)
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {
      Log.d(TAG, "handlePreSyncOperations() -- importing new flock sync collections...");
      if (!DavAccountHelper.isUsingOurServers(account))
        return;

      LocalCalendarStore localStore  = new LocalCalendarStore(provider, account.getOsAccount());
      HidingCalDavStore  remoteStore = DavAccountHelper.getHidingCalDavStore(getContext(), account, masterCipher);

      try {

        for (HidingCalDavCollection remoteCollection : remoteStore.getCollections()) {
          if (!remoteCollection.getPath().contains(DavKeyStore.PATH_KEY_COLLECTION) &&
              !localStore.getCollection(remoteCollection.getPath()).isPresent())
          {
            if (remoteCollection.getHiddenDisplayName().isPresent() &&
                remoteCollection.getHiddenColor().isPresent())
            {
              localStore.addCollection(remoteCollection.getPath(),
                                       remoteCollection.getHiddenDisplayName().get(),
                                       remoteCollection.getHiddenColor().get());
            }
            else if (remoteCollection.getHiddenDisplayName().isPresent())
              localStore.addCollection(remoteCollection.getPath(), remoteCollection.getHiddenDisplayName().get());
            else
              localStore.addCollection(remoteCollection.getPath());
          }
        }

      } finally {
        remoteStore.releaseConnections();
      }
    }

    @Override
    public List<AbstractDavSyncWorker> getSyncWorkers(DavAccount            account,
                                                      MasterCipher          masterCipher,
                                                      ContentProviderClient provider,
                                                      SyncResult            syncResult)
        throws DavException, RemoteException, IOException
    {
      List<AbstractDavSyncWorker> workers     = new LinkedList<AbstractDavSyncWorker>();
      LocalCalendarStore          localStore  = new LocalCalendarStore(provider, account.getOsAccount());
      HidingCalDavStore           remoteStore = DavAccountHelper.getHidingCalDavStore(getContext(), account, masterCipher);

      try {

        for (LocalEventCollection localCollection : localStore.getCollections()) {
          Log.d(TAG, "found local collection: " + localCollection.getPath());
          Optional<HidingCalDavCollection> remoteCollection = remoteStore.getCollection(localCollection.getPath());

          if (remoteCollection.isPresent()) {
            remoteCollection.get().setClient(
                DavAccountHelper.getAndroidDavClient(getContext(), account)
            );

            workers.add(
                new CalendarSyncWorker(getContext(), syncResult, localCollection, remoteCollection.get())
            );
          } else {
            Log.d(TAG, "local collection missing remotely, deleting locally");
            localStore.removeCollection(localCollection.getPath());
          }
        }

      } finally {
        remoteStore.releaseConnections();
      }

      return workers;
    }

    @Override
    protected void handlePostSyncOperations(DavAccount            account,
                                            MasterCipher          masterCipher,
                                            ContentProviderClient provider)
        throws RemoteException, IOException,
               GeneralSecurityException, DavException, PropertyParseException
    {
      Log.d(TAG, "handlePostSyncOperations() -- finalizing imported calendars...");

      LocalCalendarStore localStore  = new LocalCalendarStore(provider, account.getOsAccount());
      HidingCalDavStore  remoteStore = DavAccountHelper.getHidingCalDavStore(getContext(), account, masterCipher);

      try {

        Optional<String> calendarHome = remoteStore.getCalendarHomeSet();
        if (!calendarHome.isPresent())
          throw new PropertyParseException("No calendar-home-set property found for user.",
                                           remoteStore.getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

        for (LocalEventCollection copiedCalendar : localStore.getCopiedCollections()) {
          String            remotePath  = calendarHome.get().concat(UUID.randomUUID().toString() + "/");
          Optional<String>  displayName = copiedCalendar.getDisplayName();
          Optional<Integer> color       = copiedCalendar.getColor();
          SharedPreferences settings    = PreferenceManager.getDefaultSharedPreferences(getContext());

          Log.d(TAG, "found copied calendar >> " + copiedCalendar.getLocalId());
          Log.d(TAG, "will put to           >> " + remotePath);

          if (displayName.isPresent()) {
            if (color.isPresent())
              remoteStore.addCollection(remotePath, displayName.get(), color.get());
            else {
              int defaultColor = settings.getInt(PreferencesActivity.KEY_PREF_DEFAULT_CALENDAR_COLOR, 0xFFFFFFFF);
              remoteStore.addCollection(remotePath, displayName.get(), defaultColor);
            }
          }
          else
            remoteStore.addCollection(remotePath);

          localStore.setCollectionPath(copiedCalendar.getLocalId(), remotePath);
          localStore.setCollectionCopied(copiedCalendar.getLocalId(), false);
        }

      }
      finally {
        remoteStore.releaseConnections();
      }
    }
  }
}
