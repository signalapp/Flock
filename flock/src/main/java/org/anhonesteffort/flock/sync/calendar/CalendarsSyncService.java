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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.AbstractSyncAdapter;
import org.anhonesteffort.flock.sync.SyncWorker;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.PreferencesActivity;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Programmer: rhodey
 */
public class CalendarsSyncService extends Service {

  private static final String TAG = "org.anhonesteffort.flock.sync.calendar.CalendarsSyncService";

  private static final String KEY_EVENT_REMINDERS_CORRECTED = "CalendarSyncService.KEY_EVENT_REMINDERS_CORRECTED";

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

  private static class CalendarsSyncAdapter extends AbstractSyncAdapter {

    public CalendarsSyncAdapter(Context context) {
      super(context);
    }

    @Override
    protected CalendarsSyncScheduler getSyncScheduler() {
      return new CalendarsSyncScheduler(getContext());
    }

    @Override
    protected boolean localHasChanged() throws RemoteException {
      LocalCalendarStore localStore =
          new LocalCalendarStore(provider, davAccount.getOsAccount());

      for (LocalEventCollection localCollection : localStore.getCollections()) {
        if (localCollection.hasChanges())
          return true;
      }

      for (LocalEventCollection localCollection : localStore.getCopiedCollections()) {
        if (localCollection.hasChanges())
          return true;
      }

      return false;
    }

    private void setEventRemindersCorrected() {
      Log.d(TAG, "setEventRemindersCorrected()");
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
      preferences.edit().putBoolean(KEY_EVENT_REMINDERS_CORRECTED, true).apply();
    }

    private boolean getEventRemindersCorrected() {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
      return preferences.getBoolean(KEY_EVENT_REMINDERS_CORRECTED, false);
    }

    private void handleCorrectEventReminders() throws RemoteException {
      Log.d(TAG, "handleCorrectEventReminders()");

      LocalCalendarStore localStore =
          new LocalCalendarStore(provider, davAccount.getOsAccount());

      for (LocalEventCollection collection : localStore.getCollections())
        collection.handleCorrectEventReminders();

      setEventRemindersCorrected();
    }

    private void handleImportNewCollections()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {
      Log.d(TAG, "handleImportNewCollections()");
      LocalCalendarStore localStore  = new LocalCalendarStore(provider, davAccount.getOsAccount());
      HidingCalDavStore  remoteStore = DavAccountHelper.getHidingCalDavStore(getContext(), davAccount, masterCipher);

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
              localStore.addCollection(remoteCollection.getPath(),
                                       remoteCollection.getHiddenDisplayName().get());
            else
              localStore.addCollection(remoteCollection.getPath());
          }
        }

      } finally {
        remoteStore.releaseConnections();
      }
    }

    @Override
    protected void handlePreSyncOperations()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {
      Log.d(TAG, "handlePreSyncOperations()");

      if (!getEventRemindersCorrected())
        handleCorrectEventReminders();

      if (DavAccountHelper.isUsingOurServers(davAccount))
        handleImportNewCollections();
    }

    @Override
    public List<SyncWorker> getSyncWorkers(boolean localChangesOnly)
        throws DavException, RemoteException, IOException
    {
      List<SyncWorker>   workers     = new LinkedList<SyncWorker>();
      LocalCalendarStore localStore  = new LocalCalendarStore(provider, davAccount.getOsAccount());
      HidingCalDavStore  remoteStore = DavAccountHelper.getHidingCalDavStore(getContext(), davAccount, masterCipher);

      try {

        for (LocalEventCollection localCollection : localStore.getCollections()) {
          Log.d(TAG, "found local collection " + localCollection.getPath());
          if (!localChangesOnly || localCollection.hasChanges()) {

            Optional<HidingCalDavCollection> remoteCollection =
                remoteStore.getCollection(localCollection.getPath());

            if (remoteCollection.isPresent()) {
              remoteCollection.get().setClient(
                  DavAccountHelper.getAndroidDavClient(getContext(), davAccount)
              );
              workers.add(
                  new CalendarSyncWorker(getContext(), syncResult, localCollection, remoteCollection.get())
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
        throws RemoteException, IOException,
               GeneralSecurityException, DavException, PropertyParseException
    {
      Log.d(TAG, "handlePostSyncOperations() -- finalizing imported calendars...");

      LocalCalendarStore localStore  = new LocalCalendarStore(provider, davAccount.getOsAccount());
      HidingCalDavStore  remoteStore = DavAccountHelper.getHidingCalDavStore(getContext(), davAccount, masterCipher);

      try {

        for (LocalEventCollection copiedCalendar : localStore.getCopiedCollections()) {
          Optional<String> calendarHome = remoteStore.getCalendarHomeSet();
          if (!calendarHome.isPresent())
            throw new PropertyParseException("No calendar-home-set property found for user.",
                                             remoteStore.getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

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
