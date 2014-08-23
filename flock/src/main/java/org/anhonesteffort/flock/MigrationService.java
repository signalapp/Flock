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

package org.anhonesteffort.flock;

import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.sync.AbstractLocalComponentCollection;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.HidingCardDavStore;
import org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore;
import org.anhonesteffort.flock.sync.addressbook.LocalContactCollection;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavStore;
import org.anhonesteffort.flock.sync.calendar.LocalCalendarStore;
import org.anhonesteffort.flock.sync.calendar.LocalEventCollection;
import org.anhonesteffort.flock.sync.key.DavKeyCollection;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.anhonesteffort.flock.webdav.carddav.CardDavStore;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


/**
 * rhodey
 */
public class MigrationService extends Service {

  public static final String ACTION_MIGRATION_STARTED  = "org.anhonesteffort.flock.MigrationService.ACTION_MIGRATION_STARTED";
  public static final String ACTION_MIGRATION_COMPLETE = "org.anhonesteffort.flock.MigrationService.ACTION_MIGRATION_COMPLETE";

  private static final String TAG              = MigrationService.class.getSimpleName();
  private static final String PREFERENCES_NAME = "MigrationService.PREFERENCES_NAME";

  private static final String KEY_STATE                        = "MigrationService.KEY_STATE";
  private static final String KEY_TIME_FIRST_CALENDAR_SYNC     = "MigrationService.KEY_TIME_FIRST_CALENDAR_SYNC";
  private static final String KEY_TIME_FIRST_ADDRESSBOOK_SYNC  = "MigrationService.KEY_TIME_FIRST_ADDRESSBOOK_SYNC";

  private static final int STATE_STARTED_MIGRATION                         = 1;
  private static final int STATE_SYNCED_WITH_REMOTE                        = 2;
  private static final int STATE_DELETED_KEY_COLLECTION                    = 3;
  private static final int STATE_GENERATED_NEW_KEYS                        = 4;
  private static final int STATE_REPLACED_KEY_COLLECTION                   = 5;
  private static final int STATE_REPLACED_KEYS                             = 6;
  private static final int STATE_DELETED_REMOTE_CALENDARS_AND_ADDRESSBOOKS = 7;
  private static final int STATE_REPLACED_REMOTE_CALENDARS                 = 8;
  private static final int STATE_REPLACED_REMOTE_ADDRESSBOOKS              = 9;
  private static final int STATE_READY_TO_REPLACE_EVENTS_AND_CONTACTS      = 10;
  private static final int STATE_REPLACED_EVENTS_AND_CONTACTS              = 11;
  private static final int STATE_MIGRATION_COMPLETE                        = 12;

  private static final long KICK_INTERVAL_MILLISECONDS = 5000;

  private final Handler                    messageHandler = new Handler();
  private       ServiceHandler             serviceHandler;
  private       Timer                      intervalTimer;
  private       NotificationManager        notifyManager;
  private       NotificationCompat.Builder notificationBuilder;

  private DavAccount   account;
  private MasterCipher masterCipher;

  private void setState(int state) {
    Log.d(TAG, "setState() >> " + state);

    SharedPreferences settings =
        getBaseContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

    settings.edit().putInt(KEY_STATE, state).commit();
    handleUpdateNotificationUsingState();
  }

  private static int getState(Context context) {
    SharedPreferences settings =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

    Log.d(TAG, "getState() >> " + settings.getInt(KEY_STATE, 0));

    return settings.getInt(KEY_STATE, 0);
  }

  public static void hackOnActionPushCreatedContacts(Context context) {
    if (getState(context) == STATE_READY_TO_REPLACE_EVENTS_AND_CONTACTS) {
      Log.d(TAG, "just finished pushing new contacts during replace events and contacts state" +
                  ", marking early finish of contact sync.");
      new AddressbookSyncScheduler(context).setTimeLastSync(new Date().getTime());
    }
  }

  private void recordTimeFirstSync(String key, long timeMilliseconds) {
    Log.d(TAG, "recordTimeFirstSync() >> " + key + " " + timeMilliseconds);

    SharedPreferences settings =
        getBaseContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

    settings.edit().putLong(key, timeMilliseconds).commit();
  }

  private long getTimeFirstSync(String key) {
    SharedPreferences settings =
        getBaseContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

    return settings.getLong(key, -1);
  }

  private void handleInitializeNotification() {
    Log.d(TAG, "handleInitializeNotification()");

    notificationBuilder.setContentTitle(getString(R.string.migrating_to_new_version))
        .setContentText(getString(R.string.preparing_to_upgrade_sync_protocol))
        .setProgress(0, 0, true)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(1030, notificationBuilder.build());
  }

  private void handleAskUserToEnableSync() {
    Log.d(TAG, "handleAskUserToEnableSync()");

    notificationBuilder.setContentTitle(getString(R.string.please_enable_sync))
        .setContentText(getString(R.string.please_enable_anrdoid_sync_to_complete_migration))
        .setProgress(0, 0, false)
        .setSmallIcon(R.drawable.alert_warning_light);

    startForeground(1030, notificationBuilder.build());
  }

  private void handleUpdateNotificationUsingState() {
    Log.d(TAG, "handleUpdateNotificationUsingState() >> " + getState(getBaseContext()));

    notificationBuilder.setContentTitle(getString(R.string.migrating_to_new_version))
        .setSmallIcon(R.drawable.flock_actionbar_icon)
        .setProgress(0, 0, true);

    switch (getState(getBaseContext())) {
      case STATE_STARTED_MIGRATION:
        notificationBuilder.setContentText(getString(R.string.checking_sync_for_new_contacts_and_calendar));
        break;

      case STATE_SYNCED_WITH_REMOTE:
      case STATE_DELETED_KEY_COLLECTION:
      case STATE_GENERATED_NEW_KEYS:
      case STATE_REPLACED_KEY_COLLECTION:
        notificationBuilder.setContentText(getString(R.string.generating_new_encryption_secrets));
        break;

      case STATE_REPLACED_KEYS:
      case STATE_DELETED_REMOTE_CALENDARS_AND_ADDRESSBOOKS:
      case STATE_REPLACED_REMOTE_CALENDARS:
        notificationBuilder.setContentText(getString(R.string.replacing_addressbooks_and_calendars));
        break;

      case STATE_REPLACED_REMOTE_ADDRESSBOOKS:
      case STATE_READY_TO_REPLACE_EVENTS_AND_CONTACTS:
        notificationBuilder.setContentText(getString(R.string.replacing_old_contacts_and_events));
        break;

      case STATE_REPLACED_EVENTS_AND_CONTACTS:
        notificationBuilder.setContentText(getString(R.string.finalizing_migration));
        break;

      case STATE_MIGRATION_COMPLETE:
        notificationBuilder.setContentTitle(getString(R.string.migration_complete));
        notificationBuilder.setContentText(getString(R.string.please_update_flock_on_all_your_devices));
        break;
    }

    notifyManager.notify(1030, notificationBuilder.build());
  }

  private void handleMigrationComplete() {
    Log.d(TAG, "handleMigrationComplete()");

    if (intervalTimer != null)
      intervalTimer.cancel();

    Intent intent = new Intent();
    intent.setPackage(MigrationHelperBroadcastReceiver.class.getPackage().getName());
    intent.setAction(ACTION_MIGRATION_COMPLETE);
    sendBroadcast(intent);

    stopForeground(false);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");

    if (getState(getBaseContext()) == STATE_MIGRATION_COMPLETE) {
      notificationBuilder
          .setSmallIcon(R.drawable.flock_actionbar_icon)
          .setContentTitle(getString(R.string.migration_complete))
          .setContentText(getString(R.string.please_update_flock_on_all_your_devices))
          .setProgress(0, 0, false);

      notifyManager.notify(1030, notificationBuilder.build());
    }
  }

  private void handleEnableAllSyncAdapters() {
    new CalendarsSyncScheduler(getBaseContext()).setSyncEnabled(account.getOsAccount(), true);
    new AddressbookSyncScheduler(getBaseContext()).setSyncEnabled(account.getOsAccount(), true);
  }

  private void handleDisableAllSyncAdapters() {
    new CalendarsSyncScheduler(getBaseContext()).setSyncEnabled(account.getOsAccount(), false);
    new AddressbookSyncScheduler(getBaseContext()).setSyncEnabled(account.getOsAccount(), false);

    new CalendarsSyncScheduler(getBaseContext()).cancelPendingSyncs(account.getOsAccount());
    new AddressbookSyncScheduler(getBaseContext()).cancelPendingSyncs(account.getOsAccount());
  }
  
  private void handleException(Exception e) {
    if (e instanceof IOException) {
      IOException ex = (IOException) e;

      if (ex instanceof SocketException      ||
          ex instanceof UnknownHostException ||
          ex instanceof SocketTimeoutException)
      {
        Log.d(TAG, "experienced connection error during migration, will continue trying.", e);
      }
      else
        Log.d(TAG, "caught unknown IOException during migration >> " + e.toString(), e);
    }
    else
      Log.d(TAG, "caught exception during migration >> " + e.toString(), e);
  }

  private void handleStartMigration() {
    Log.d(TAG, "handleStartMigration()");

    handleInitializeNotification();
    handleStartKickingMigration();

    KeyStore.setUseCipherVersionZero(getBaseContext(), true);

    if (DavAccountHelper.isUsingOurServers(getBaseContext())) {
      try {

        new RegistrationApi(getBaseContext()).setAccountVersion(account, 2);

      } catch (RegistrationApiException e) {
        handleException(e);
        return;
      } catch (IOException e) {
        handleException(e);
        return;
      }
    }

    Intent intent = new Intent();
    intent.setPackage(MigrationHelperBroadcastReceiver.class.getPackage().getName());
    intent.setAction(ACTION_MIGRATION_STARTED);
    sendBroadcast(intent);

    setState(STATE_STARTED_MIGRATION);
  }

  private void handleSyncWithRemote() {
    Log.d(TAG, "handleSyncWithRemote()");

    handleEnableAllSyncAdapters();
    if (!ContentResolver.getMasterSyncAutomatically()) {
      handleAskUserToEnableSync();
      return;
    }
    handleUpdateNotificationUsingState();

    Long           firstRecordedCalendarSync = getTimeFirstSync(KEY_TIME_FIRST_CALENDAR_SYNC);
    Optional<Long> timeLastCalendarSync      = new CalendarsSyncScheduler(getBaseContext()).getTimeLastSync();

    if (firstRecordedCalendarSync <= 0) {
      if (!timeLastCalendarSync.isPresent())
        recordTimeFirstSync(KEY_TIME_FIRST_CALENDAR_SYNC, new Date().getTime());
      else
        recordTimeFirstSync(KEY_TIME_FIRST_CALENDAR_SYNC, timeLastCalendarSync.get());

      new CalendarsSyncScheduler(getBaseContext()).requestSync();
    }

    Long           firstRecordedAddressbookSync = getTimeFirstSync(KEY_TIME_FIRST_ADDRESSBOOK_SYNC);
    Optional<Long> timeLastAddressbookSync      = new AddressbookSyncScheduler(getBaseContext()).getTimeLastSync();

    if (firstRecordedAddressbookSync <= 0) {
      if (!timeLastAddressbookSync.isPresent())
        recordTimeFirstSync(KEY_TIME_FIRST_ADDRESSBOOK_SYNC, new Date().getTime());
      else
        recordTimeFirstSync(KEY_TIME_FIRST_ADDRESSBOOK_SYNC, timeLastAddressbookSync.get());

      new AddressbookSyncScheduler(getBaseContext()).requestSync();
    }

    if (firstRecordedCalendarSync > 0 && firstRecordedAddressbookSync > 0) {
      if (timeLastCalendarSync.isPresent() && timeLastAddressbookSync.isPresent()) {
        if (timeLastCalendarSync.get()    > firstRecordedCalendarSync &&
            timeLastAddressbookSync.get() > firstRecordedAddressbookSync)
        {
          if (new CalendarsSyncScheduler(getBaseContext()).syncInProgress(account.getOsAccount()) ||
              new AddressbookSyncScheduler(getBaseContext()).syncInProgress(account.getOsAccount()))
          {
            Log.w(TAG, "finished syncing with remote but waiting for active syncs to complete.");
            handleDisableAllSyncAdapters();
            return;
          }

          KeyStore.setUseCipherVersionZero(getBaseContext(), false);
          handleDisableAllSyncAdapters();

          recordTimeFirstSync(KEY_TIME_FIRST_CALENDAR_SYNC,    -1);
          recordTimeFirstSync(KEY_TIME_FIRST_ADDRESSBOOK_SYNC, -1);

          setState(STATE_SYNCED_WITH_REMOTE);
        }
      }
    }
  }

  private void handleDeleteKeyCollection() {
    Log.d(TAG, "handleDeleteKeyCollection()");

    try {

      DavKeyStore keyStore = DavAccountHelper.getDavKeyStore(getBaseContext(), account);

      try {

        Optional<String> calendarHomeSet = keyStore.getCalendarHomeSet();
        keyStore.removeCollection(calendarHomeSet.get().concat(DavKeyStore.PATH_KEY_COLLECTION));

        setState(STATE_DELETED_KEY_COLLECTION);

      } catch (PropertyParseException e) {
        handleException(e);
      } catch (DavException e) {
        handleException(e);
      } catch (IOException e) {
        handleException(e);
      } finally {
        keyStore.closeHttpConnection();
      }

    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleGenerateNewKeys() {
    Log.d(TAG, "handleGenerateNewKeys()");

    try {

      KeyHelper.generateAndSaveSaltAndKeyMaterial(getBaseContext());
      masterCipher = KeyHelper.getMasterCipher(getBaseContext()).get();

      setState(STATE_GENERATED_NEW_KEYS);

    } catch (GeneralSecurityException e) {
      handleException(e);
    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleCreateKeyCollection() {
    Log.d(TAG, "handleCreateKeyCollection()");

    try {

      DavKeyStore.createCollection(getBaseContext(), account);

      setState(STATE_REPLACED_KEY_COLLECTION);

    } catch (PropertyParseException e) {
      handleException(e);
    } catch (DavException e) {

      if (e.getErrorCode() == DavServletResponse.SC_FORBIDDEN) {
        Log.w(TAG, "caught 403 when trying to create key collection, assuming already exists.");
        setState(STATE_REPLACED_KEY_COLLECTION);
      }
      else
        handleException(e);

    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleReplaceKeys() {
    Log.d(TAG, "handleReplaceKeys()");

    try {

      DavKeyStore keyStore = DavAccountHelper.getDavKeyStore(getBaseContext(), account);

      try {

        Optional<DavKeyCollection> keyCollection = keyStore.getCollection();

        if (!keyCollection.isPresent()) {
          Log.e(TAG, "missing key collection, reverting state to regenerate keys!");
          setState(STATE_SYNCED_WITH_REMOTE);
          return;
        }

        Optional<String> localKeyMaterialSalt      = KeyHelper.buildEncodedSalt(getBaseContext());
        Optional<String> localEncryptedKeyMaterial = KeyStore.getEncryptedKeyMaterial(getBaseContext());

        if (localKeyMaterialSalt.isPresent() && localEncryptedKeyMaterial.isPresent()) {
          keyCollection.get().setKeyMaterialSalt(localKeyMaterialSalt.get());
          keyCollection.get().setEncryptedKeyMaterial(localEncryptedKeyMaterial.get());

          setState(STATE_REPLACED_KEYS);
        }
        else {
          Log.e(TAG, "missing key material, reverting state to regenerate keys!");
          setState(STATE_SYNCED_WITH_REMOTE);
        }

      } catch (PropertyParseException e) {
        handleException(e);
      } catch (DavException e) {
        handleException(e);
      } catch (IOException e) {
        handleException(e);
      } finally {
        keyStore.closeHttpConnection();
      }

    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleDeleteRemoteCalendarsAndAddressbooks() {
    Log.d(TAG, "handleDeleteRemoteCalendarsAndAddressbooks()");

    try {

      CalDavStore  remoteCalendarStore    = DavAccountHelper.getCalDavStore(getBaseContext(), account);
      CardDavStore remoteAddressbookStore = DavAccountHelper.getCardDavStore(getBaseContext(), account);

      try {

        LocalCalendarStore    localCalendarStore    = new LocalCalendarStore(getBaseContext(), account.getOsAccount());
        LocalAddressbookStore localAddressbookStore = new LocalAddressbookStore(getBaseContext(), account);

        for (LocalEventCollection localCollection : localCalendarStore.getCollections()) {
          Log.d(TAG, "deleting remote caldav collection at >> " + localCollection.getPath());
          remoteCalendarStore.removeCollection(localCollection.getPath());
        }

        for (LocalContactCollection localCollection : localAddressbookStore.getCollections()) {
          Log.d(TAG, "deleting remote carddav collection at >> " + localCollection.getPath());
          remoteAddressbookStore.removeCollection(localCollection.getPath());
        }

        setState(STATE_DELETED_REMOTE_CALENDARS_AND_ADDRESSBOOKS);

      } catch (RemoteException e) {
        handleException(e);
      } catch (DavException e) {
        handleException(e);
      } catch (IOException e) {
        handleException(e);
      } finally {
        remoteCalendarStore.closeHttpConnection();
        remoteAddressbookStore.closeHttpConnection();
      }

    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleReplaceRemoteCalendars() {
    Log.d(TAG, "handleReplaceRemoteCalendars()");

    try {

      HidingCalDavStore remoteCalendarStore = DavAccountHelper.getHidingCalDavStore(getBaseContext(), account, masterCipher);

      try {

        LocalCalendarStore localCalendarStore = new LocalCalendarStore(getBaseContext(), account.getOsAccount());

        for (LocalEventCollection localCollection : localCalendarStore.getCollections()) {
          if (!remoteCalendarStore.getCollection(localCollection.getPath()).isPresent()) {
            Log.d(TAG, "creating remote caldav collection at >> " + localCollection.getPath());

            Optional<String>  displayName = localCollection.getDisplayName();
            Optional<Integer> color       = localCollection.getColor();

            if (displayName.isPresent() && color.isPresent())
              remoteCalendarStore.addCollection(localCollection.getPath(), displayName.get(), color.get());
            else if (displayName.isPresent()) {
              remoteCalendarStore.addCollection(localCollection.getPath(),
                                                displayName.get(),
                                                getBaseContext().getResources().getColor(R.color.flocktheme_color));
            }
            else
              remoteCalendarStore.addCollection(localCollection.getPath());
          }
        }

        setState(STATE_REPLACED_REMOTE_CALENDARS);

      } catch (RemoteException e) {
        handleException(e);
      } catch (DavException e) {

        if (e.getErrorCode() != DavServletResponse.SC_FORBIDDEN)
          handleException(e);

      } catch (GeneralSecurityException e) {
        handleException(e);
      } catch (IOException e) {
        handleException(e);
      } finally {
        remoteCalendarStore.releaseConnections();
      }

    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleReplaceRemoteAddressbooks() {
    Log.d(TAG, "handleReplaceRemoteAddressbooks()");

    try {

      HidingCardDavStore remoteAddressbookStore = DavAccountHelper.getHidingCardDavStore(getBaseContext(), account, masterCipher);

      try {

        LocalAddressbookStore localAddressbookStore = new LocalAddressbookStore(getBaseContext(), account);

        for (LocalContactCollection localCollection : localAddressbookStore.getCollections()) {
          if (!remoteAddressbookStore.getCollection(localCollection.getPath()).isPresent()) {
            Log.d(TAG, "creating remote carddav collection at >> " + localCollection.getPath());

            Optional<String> displayName = localCollection.getDisplayName();
            if (displayName.isPresent())
              remoteAddressbookStore.addCollection(localCollection.getPath(), displayName.get());
            else
              remoteAddressbookStore.addCollection(localCollection.getPath());
          }
        }

        setState(STATE_REPLACED_REMOTE_ADDRESSBOOKS);

      } catch (DavException e) {

        if (e.getErrorCode() != DavServletResponse.SC_FORBIDDEN)
          handleException(e);

      } catch (GeneralSecurityException e) {
        handleException(e);
      } catch (IOException e) {
        handleException(e);
      } finally {
        remoteAddressbookStore.releaseConnections();
      }

    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleMarkAllComponentsAsNew(AbstractLocalComponentCollection<?> localCollection)
      throws RemoteException, OperationApplicationException
  {
    Log.d(TAG, "handleMarkAllComponentsAsNew() >> " + localCollection.getPath());

    for (Long componentId : localCollection.getComponentIds())
      localCollection.queueForMigration(componentId);

    localCollection.commitPendingOperations();
  }

  private void handleMarkAllLocalEventsAndContactsAsNew() {
    Log.d(TAG, "handleMarkAllLocalEventsAndContactsAsNew()");

    LocalCalendarStore    localCalendarStore    = new LocalCalendarStore(getBaseContext(), account.getOsAccount());
    LocalAddressbookStore localAddressbookStore = new LocalAddressbookStore(getBaseContext(), account);

    try {

      for (LocalEventCollection collection : localCalendarStore.getCollections()) {
        Log.d(TAG, "marking all components as new in collection >> " + collection.getPath());
        handleMarkAllComponentsAsNew(collection);
      }

      for (LocalContactCollection collection : localAddressbookStore.getCollections()) {
        Log.d(TAG, "marking all components as new in collection >> " + collection.getPath());
        handleMarkAllComponentsAsNew(collection);
      }

      setState(STATE_READY_TO_REPLACE_EVENTS_AND_CONTACTS);

    } catch (OperationApplicationException e) {
      handleException(e);
    } catch (RemoteException e) {
      handleException(e);
    }
  }

  private void handleReplaceEventsAndContacts() {
    Log.d(TAG, "handleReplaceEventsAndContacts()");

    handleEnableAllSyncAdapters();
    if (!ContentResolver.getMasterSyncAutomatically()) {
      handleAskUserToEnableSync();
      return;
    }
    handleUpdateNotificationUsingState();

    Long           firstRecordedCalendarSync = getTimeFirstSync(KEY_TIME_FIRST_CALENDAR_SYNC);
    Optional<Long> timeLastCalendarSync      = new CalendarsSyncScheduler(getBaseContext()).getTimeLastSync();

    if (firstRecordedCalendarSync <= 0) {
      if (!timeLastCalendarSync.isPresent())
        recordTimeFirstSync(KEY_TIME_FIRST_CALENDAR_SYNC, new Date().getTime());
      else
        recordTimeFirstSync(KEY_TIME_FIRST_CALENDAR_SYNC, timeLastCalendarSync.get());

      new CalendarsSyncScheduler(getBaseContext()).requestSync();
    }

    Long           firstRecordedAddressbookSync = getTimeFirstSync(KEY_TIME_FIRST_ADDRESSBOOK_SYNC);
    Optional<Long> timeLastAddressbookSync      = new AddressbookSyncScheduler(getBaseContext()).getTimeLastSync();

    if (firstRecordedAddressbookSync <= 0) {
      if (!timeLastAddressbookSync.isPresent())
        recordTimeFirstSync(KEY_TIME_FIRST_ADDRESSBOOK_SYNC, new Date().getTime());
      else
        recordTimeFirstSync(KEY_TIME_FIRST_ADDRESSBOOK_SYNC, timeLastAddressbookSync.get());

      new AddressbookSyncScheduler(getBaseContext()).requestSync();
    }

    if (firstRecordedCalendarSync > 0 && firstRecordedAddressbookSync > 0) {
      if (timeLastCalendarSync.isPresent() && timeLastAddressbookSync.isPresent()) {
        if (timeLastCalendarSync.get()    > firstRecordedCalendarSync &&
            timeLastAddressbookSync.get() > firstRecordedAddressbookSync)
        {
          setState(STATE_REPLACED_EVENTS_AND_CONTACTS);
        }
      }
    }
  }

  private void handleSetMigrationComplete() {
    Log.d(TAG, "handleSetMigrationComplete()");

    try {

      DavKeyStore davKeyStore = DavAccountHelper.getDavKeyStore(getBaseContext(), account);

      try {

        Optional<DavKeyCollection> keyCollection = davKeyStore.getCollection();

        if (keyCollection.isPresent()) {
          keyCollection.get().setMigrationComplete(getBaseContext());
          setState(STATE_MIGRATION_COMPLETE);
        }
        else {
          Log.e(TAG, "missing key collection, reverting state to regenerate keys!");
          setState(STATE_SYNCED_WITH_REMOTE);
        }

      } catch (InvalidComponentException e) {
        handleException(e);
      } catch (PropertyParseException e) {
        handleException(e);
      } catch (DavException e) {
        handleException(e);
      } catch (IOException e) {
        handleException(e);
      } finally {
        davKeyStore.closeHttpConnection();
      }

    } catch (IOException e) {
      handleException(e);
    }
  }

  private void handleStartOrResumeMigration() {
    Log.d(TAG, "handleStartOrResumeMigration()");

    switch (getState(getBaseContext())) {
      case STATE_STARTED_MIGRATION:
        handleSyncWithRemote();
        if (getState(getBaseContext()) == STATE_STARTED_MIGRATION)
          break;

      case STATE_SYNCED_WITH_REMOTE:
        handleDeleteKeyCollection();
        if (getState(getBaseContext()) <= STATE_SYNCED_WITH_REMOTE)
          break;

      case STATE_DELETED_KEY_COLLECTION:
        handleGenerateNewKeys();
        if (getState(getBaseContext()) <= STATE_DELETED_KEY_COLLECTION)
          break;

      case STATE_GENERATED_NEW_KEYS:
        handleCreateKeyCollection();
        if (getState(getBaseContext()) <= STATE_GENERATED_NEW_KEYS)
          break;

      case STATE_REPLACED_KEY_COLLECTION:
        handleReplaceKeys();
        if (getState(getBaseContext()) <= STATE_REPLACED_KEY_COLLECTION)
          break;

      case STATE_REPLACED_KEYS:
        handleDeleteRemoteCalendarsAndAddressbooks();
        if (getState(getBaseContext()) <= STATE_REPLACED_KEYS)
          break;

      case STATE_DELETED_REMOTE_CALENDARS_AND_ADDRESSBOOKS:
        handleReplaceRemoteCalendars();
        if (getState(getBaseContext()) <= STATE_DELETED_REMOTE_CALENDARS_AND_ADDRESSBOOKS)
          break;

      case STATE_REPLACED_REMOTE_CALENDARS:
        handleReplaceRemoteAddressbooks();
        if (getState(getBaseContext()) <= STATE_REPLACED_REMOTE_CALENDARS)
          break;

      case STATE_REPLACED_REMOTE_ADDRESSBOOKS:
        handleMarkAllLocalEventsAndContactsAsNew();
        if (getState(getBaseContext()) <= STATE_REPLACED_REMOTE_ADDRESSBOOKS)
          break;

      case STATE_READY_TO_REPLACE_EVENTS_AND_CONTACTS:
        handleReplaceEventsAndContacts();
        if (getState(getBaseContext()) <= STATE_READY_TO_REPLACE_EVENTS_AND_CONTACTS)
          break;

      case STATE_REPLACED_EVENTS_AND_CONTACTS:
        handleSetMigrationComplete();
        if (getState(getBaseContext()) <= STATE_REPLACED_EVENTS_AND_CONTACTS)
          break;

      case STATE_MIGRATION_COMPLETE:
        handleMigrationComplete();
        break;

      default:
        handleStartMigration();
    }
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("MigrationService", HandlerThread.NORM_PRIORITY);
    thread.start();

    Looper serviceLooper = thread.getLooper();

    serviceHandler      = new ServiceHandler(serviceLooper);
    notifyManager       = (NotificationManager)getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);
    notificationBuilder = new NotificationCompat.Builder(getBaseContext());

    try {

      Optional<DavAccount>   account      = DavAccountHelper.getAccount(getBaseContext());
      Optional<MasterCipher> masterCipher = KeyHelper.getMasterCipher(getBaseContext());

      if (account.isPresent() && masterCipher.isPresent()) {
        this.account      = account.get();
        this.masterCipher = masterCipher.get();
      }
      else
        Log.e(TAG, "ACCOUNT NOT PRESENT xxx 0.O");

    } catch (IOException e) {
      Log.e(TAG, "exception while getting MasterCipher >> " + e);
    }
  }

  private final class ServiceHandler extends Handler {

    public ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      Log.d(TAG, "handleMessage()");

      if (account != null && masterCipher != null)
        handleStartOrResumeMigration();
      else
        Log.e(TAG, "missing account or master cipher! xxx");
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "onStartCommand()");

    serviceHandler.sendMessage(serviceHandler.obtainMessage());
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "onBind()");
    return null;
  }

  private final Runnable kickMigrationRunnable = new Runnable() {
    @Override
    public void run() {
      getBaseContext().startService(new Intent(getBaseContext(), MigrationService.class));
    }
  };

  public void handleStartKickingMigration() {
    Log.d(TAG, "handleStartKickingMigration()");

              intervalTimer     = new Timer();
    TimerTask kickMigrationTask = new TimerTask() {
      @Override
      public void run() {
        messageHandler.post(kickMigrationRunnable);
      }
    };

    intervalTimer.schedule(kickMigrationTask, 0, KICK_INTERVAL_MILLISECONDS);
  }
}
