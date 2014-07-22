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

import android.accounts.Account;
import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.key.KeySyncUtil;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.PreferencesActivity;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
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

    protected String getAuthority() {
      return CalendarsSyncScheduler.CONTENT_AUTHORITY;
    }

    private void finalizeCopiedCalendars(LocalCalendarStore localStore,
                                         HidingCalDavStore  remoteStore)
        throws RemoteException, IOException,
               GeneralSecurityException, DavException, PropertyParseException
    {
      Log.d(TAG, "finalizeCopiedCalendars()");

      Optional<String> calendarHome = remoteStore.getCalendarHomeSet();
      if (!calendarHome.isPresent())
        throw new PropertyParseException("No calendar-home-set property found for user.",
                                         remoteStore.getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

      List<LocalEventCollection> copiedCalendars = localStore.getCopiedCollections();
      for (LocalEventCollection copiedCalendar : copiedCalendars) {
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

    @Override
    public void onPerformSync(Account               account,
                              Bundle                extras,
                              String                authority,
                              ContentProviderClient provider,
                              SyncResult            syncResult)
    {
      Log.d(TAG, "performing sync for authority >> " + authority);

      // ical4j TimeZoneRegistry kills everything without this...
      Thread.currentThread().setContextClassLoader(getContext().getClassLoader());

      Optional<DavAccount> davAccountOptional = DavAccountHelper.getAccount(getContext());
      if (!davAccountOptional.isPresent()) {
        Log.d(TAG, "dav account is missing");
        syncResult.stats.numAuthExceptions++;
        showNotifications(syncResult);
        return ;
      }

      try {

        Optional<MasterCipher> masterCipher = KeyHelper.getMasterCipher(getContext());
        if (!masterCipher.isPresent()) {
          Log.d(TAG, "master cipher is missing");
          syncResult.stats.numAuthExceptions++;
          return ;
        }

        LocalCalendarStore localStore  = new LocalCalendarStore(provider, davAccountOptional.get().getOsAccount());
        HidingCalDavStore  remoteStore = DavAccountHelper.getHidingCalDavStore(getContext(), davAccountOptional.get(), masterCipher.get());

        if (DavAccountHelper.isUsingOurServers(getContext())) {
          for (HidingCalDavCollection remoteCollection : remoteStore.getCollections()) {
            try {

              if (!remoteCollection.getPath().contains(KeySyncUtil.PATH_KEY_COLLECTION) &&
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

            } catch (IOException e) {
              handleException(getContext(), e, syncResult);
            } catch (PropertyParseException e) {
              handleException(getContext(), e, syncResult);
            } catch (RemoteException e) {
              handleException(getContext(), e, syncResult);
            } catch (InvalidMacException e) {
              handleException(getContext(), e, syncResult);
            } catch (GeneralSecurityException e) {
              handleException(getContext(), e, syncResult);
            }
          }
        }

        for (LocalEventCollection localCollection : localStore.getCollections()) {
          Log.d(TAG, "found local collection: " + localCollection.getPath());
          Optional<HidingCalDavCollection> remoteCollection = remoteStore.getCollection(localCollection.getPath());

          if (remoteCollection.isPresent())
            new CalendarSyncWorker(getContext(), localCollection, remoteCollection.get()).run(syncResult, false);
          else {
            Log.d(TAG, "local collection missing remotely, deleting locally");
            localStore.removeCollection(localCollection.getPath());
          }
        }

        finalizeCopiedCalendars(localStore, remoteStore);
        remoteStore.releaseConnections();

      } catch (IOException e) {
        handleException(getContext(), e, syncResult);
      } catch (DavException e) {
        handleException(getContext(), e, syncResult);
      } catch (PropertyParseException e) {
        handleException(getContext(), e, syncResult);
      } catch (RemoteException e) {
        handleException(getContext(), e, syncResult);
      } catch (GeneralSecurityException e) {
        handleException(getContext(), e, syncResult);
      }

      showNotifications(syncResult);
      new CalendarsSyncScheduler(getContext()).setTimeLastSync(new Date().getTime());
    }
  }
}
