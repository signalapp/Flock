/*
 * *
 *  Copyright (C) 2015 Open Whisper Systems
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

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Name;
import net.fortuna.ical4j.model.property.Version;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.AbstractLocalComponentCollection;
import org.anhonesteffort.flock.sync.InvalidLocalComponentException;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.ContactFactory;
import org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore;
import org.anhonesteffort.flock.sync.addressbook.LocalContactCollection;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.LocalCalendarStore;
import org.anhonesteffort.flock.sync.calendar.LocalEventCollection;
import org.anhonesteffort.flock.util.guava.Optional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.io.text.VCardWriter;
import ezvcard.property.Photo;
import ezvcard.property.Uid;

/**
 * Programmer: rhodey
 */
public class ExportService extends Service {

  private static final String TAG = ExportService.class.getSimpleName();

  private static final int NOTIFY_ID = 1025;

  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;

  private int     countFailedContactExports = 0;
  private int     countFailedEventExports   = 0;

  private enum EndState {
    SUCCESS,           PROMPT_LOGIN,
    PROMPT_MAKE_SPACE, PROMPT_RESTART
  }
  private EndState endState = null;

  private void handleContactExportFailed() {
    countFailedContactExports++;
    Log.d(TAG, "contact export failed, counter: " + countFailedContactExports);
  }

  private void handleEventExportFailed() {
    countFailedEventExports++;
    Log.d(TAG, "event export failed, counter: " + countFailedEventExports);
  }

  private void handleInitializeNotification() {
    notificationBuilder
        .setProgress(0, 0, true)
        .setContentTitle(getString(R.string.export))
        .setContentText(getString(R.string.exporting_contacts_and_calendars))
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(NOTIFY_ID, notificationBuilder.build());
  }

  private Optional<LocalContactCollection> getAddressbook(ContentProviderClient client, DavAccount account) {
    LocalAddressbookStore        addressbookStore = new LocalAddressbookStore(getBaseContext(), client, account);
    List<LocalContactCollection> addressbooks     = addressbookStore.getCollections();

    if (addressbooks.isEmpty()) return Optional.absent();
    else                        return Optional.of(addressbooks.get(0));
  }

  private List<LocalEventCollection> getCalendars(ContentProviderClient client, Account account)
      throws RemoteException
  {
    LocalCalendarStore calendarStore = new LocalCalendarStore(client, account);
    return calendarStore.getCollections();
  }

  private Optional<File> createExternalFile(String filename) {
    if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
      Log.w(TAG, "external media not mounted?");
      return Optional.absent();
    }

    try {

      File file = new File(Environment.getExternalStorageDirectory(), filename);

      if      (file.exists())        return Optional.of(file);
      else if (file.createNewFile()) return Optional.of(file);

      return Optional.absent();

    } catch (IOException e) {
      Log.w(TAG, "unable to create file " + filename, e);
      return Optional.absent();
    }
  }

  private List<File> createFilesForCollections(List<AbstractLocalComponentCollection<?>> collections) {
    List<File>     files    = new LinkedList<>();
    Optional<File> file     = null;
    int            calCount = 1;

    for (AbstractLocalComponentCollection collection : collections) {
      if (collection instanceof LocalContactCollection)
        file = createExternalFile(getString(R.string.flock_contacts_vcf));
      else {
        file = createExternalFile(getString(R.string.flock_calendar_ical, calCount));
        calCount++;
      }
      if (file.isPresent())
        files.add(file.get());
    }

    return files;
  }

  private void simulateExport(AbstractLocalComponentCollection<?> collection, File output)
      throws IOException, RemoteException
  {
    FileOutputStream stream = new FileOutputStream(output, false);

    try {

      for (int i = 0; i < collection.getComponentIds().size(); i++)
        stream.write(new byte[512]);

    } finally {
      stream.close();
    }
  }

  private boolean isStorageSpaceAvailable(List<AbstractLocalComponentCollection<?>> collections, List<File> files)
      throws RemoteException
  {
    if (files.size() != collections.size()) {
      Log.w(TAG, "collection count and output file count differ");
      return false;
    }

    try {

      for (int i = 0; i < collections.size(); i++)
        simulateExport(collections.get(i), files.get(i));
      return true;

    } catch (IOException e) {
      Log.w(TAG, "error during export simulation, not enough space?", e);
      return false;
    }
  }

  private void handleExportContacts(LocalContactCollection addressbook, File output)
      throws RemoteException, IOException
  {
    VCardWriter vCardWriter = new VCardWriter(output, false, VCardVersion.V3_0);

    try {
      for (Long contactId : addressbook.getComponentIds()) {
        try {

          Optional<VCard> vCard = addressbook.getComponent(contactId);
          if (vCard.isPresent()) {
            vCard.get().removeProperties(Uid.class);
            vCard.get().removeProperties(Photo.class);
            vCard.get().removeExtendedProperty(ContactFactory.PROPERTY_STARRED);
            vCardWriter.write(vCard.get());
          } else {
            Log.w(TAG, "couldn't find " + contactId + " in addressbook");
          }

        } catch (InvalidLocalComponentException e) {
          handleContactExportFailed();
        }
      }
    } finally {
      vCardWriter.close();
    }
  }

  private void handleExportCalendars(List<LocalEventCollection> eventCollections, List<File> outputs)
      throws ValidationException, RemoteException, IOException
  {
    CalendarOutputter calendarWriter = new CalendarOutputter(false);
    for (int i = 0; i < eventCollections.size(); i++) {
      LocalEventCollection eventCollection = eventCollections.get(i);
      List<Long>           eventIds        = eventCollection.getComponentIds();
      Calendar             calendar        = new Calendar();
      FileOutputStream     output          = new FileOutputStream(outputs.get(i), false);

      Optional<String> displayName = eventCollection.getDisplayName();
      if (displayName.isPresent() && !displayName.get().isEmpty())
        calendar.getProperties().add(new Name(displayName.get()));

      try {

        for (Long eventId : eventIds) {
          try {

            Optional<Calendar> event = eventCollection.getComponent(eventId);
            if (event.isPresent()) {
              VEvent vEvent = (VEvent) event.get().getComponent(VEvent.VEVENT);
              if (vEvent != null) {
                if (vEvent.getProperty(Property.ORGANIZER) != null)
                  vEvent.getProperties().remove(vEvent.getProperty(Property.ORGANIZER));
                calendar.getComponents().add(vEvent);
              }
              else
                Log.w(TAG, "couldn't parse VEVENT from local calendar component");
            } else {
              Log.w(TAG, "couldn't find " + eventId + " in calendar " + eventCollection.getPath());
            }

          } catch (InvalidLocalComponentException e) {
            handleEventExportFailed();
          }
        }

        calendar.getProperties().add(Version.VERSION_2_0);
        calendarWriter.output(calendar, output);

      } finally {
        output.close();
      }
    }
  }

  private void handleIndexFilesWithMediaScanner(List<File> files) {
    for (File file : files) {
      sendBroadcast(new Intent(
          Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
          Uri.fromFile(file)
      ));
    }
  }

  private void handleExportComplete(EndState endState) {
    Log.d(TAG, "HANDLE EXPORT COMPLETE: " + endState);
    this.endState = endState;
    stopForeground(false);
    stopSelf();
  }

  private void handlePromptLoginAndRetry() {
    Log.w(TAG, "HANDLE PROMPT LOGIN AND RETRY");

    Intent        clickIntent   = new Intent(getBaseContext(), CorrectPasswordActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    notificationBuilder
        .setAutoCancel(true)
        .setProgress(0, 0, false)
        .setContentIntent(pendingIntent)
        .setContentTitle(getString(R.string.export_failed))
        .setContentText(getString(R.string.tap_to_login_then_retry_export));
    notifyManager.notify(NOTIFY_ID, notificationBuilder.build());
  }

  private void handlePromptClearSpaceAndRetry() {
    Log.w(TAG, "HANDLE PROMPT CLEAR SPACE AND RETRY");
    notificationBuilder
        .setProgress(0, 0, false)
        .setContentTitle(getString(R.string.export_failed))
        .setContentText(getString(R.string.try_making_more_storage_space_available));
    notifyManager.notify(NOTIFY_ID, notificationBuilder.build());
  }

  private void handleUnrecoverableError() {
    Log.w(TAG, "HANDLE UNRECOVERABLE ERROR");
    notificationBuilder
        .setProgress(0, 0, false)
        .setContentTitle(getString(R.string.export_failed))
        .setContentText(getString(R.string.try_a_separate_export_app_if_error_continues));
    notifyManager.notify(NOTIFY_ID, notificationBuilder.build());
  }

  private void handleStartExport() {
    Log.d(TAG, "HANDLE START EXPORT");
    handleInitializeNotification();

    try {
      Optional<DavAccount>  account        = DavAccountHelper.getAccount(getBaseContext());
      ContentProviderClient contactClient  = getBaseContext().getContentResolver()
          .acquireContentProviderClient(AddressbookSyncScheduler.CONTENT_AUTHORITY);
      ContentProviderClient calendarClient = getBaseContext().getContentResolver()
          .acquireContentProviderClient(CalendarsSyncScheduler.CONTENT_AUTHORITY);

      if (account.isPresent()) {
        try {

          Optional<LocalContactCollection>          addressbook = getAddressbook(contactClient, account.get());
          List<LocalEventCollection>                calendars   = getCalendars(calendarClient, account.get().getOsAccount());
          List<AbstractLocalComponentCollection<?>> collections = new LinkedList<>();

          if (!addressbook.isPresent()) {
            throw new RemoteException("addressbook missing, what is going on?");
          }

          collections.add(addressbook.get());
          collections.addAll(calendars);
          List<File> outputFiles = createFilesForCollections(collections);

          if (isStorageSpaceAvailable(collections, outputFiles)) {
            File contactsFile = outputFiles.remove(0);
            handleExportContacts(addressbook.get(), contactsFile);
            handleExportCalendars(calendars, outputFiles);
            outputFiles.add(contactsFile);
            handleIndexFilesWithMediaScanner(outputFiles);
            handleExportComplete(EndState.SUCCESS);
            return;
          } else {
            handleExportComplete(EndState.PROMPT_MAKE_SPACE);
            return;
          }

        } catch (ValidationException e) {
          Log.e(TAG, "WTF ical4j", e);
        } catch (RemoteException e) {
          Log.e(TAG, "why android?", e);
        } catch (IOException e) {
          Log.e(TAG, "why android?", e);
        }
      } else {
        handleExportComplete(EndState.PROMPT_LOGIN);
        return;
      }
    } catch (Exception e) {
      Log.e(TAG, "caught unexpected runtime exception", e);
    }

    handleExportComplete(EndState.PROMPT_RESTART);
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "ON DESTROY");

    switch (endState) {
      case PROMPT_LOGIN:
        handlePromptLoginAndRetry();
        break;

      case PROMPT_MAKE_SPACE:
        handlePromptClearSpaceAndRetry();
        break;

      case PROMPT_RESTART:
        handleUnrecoverableError();
        break;
    }

    if (endState != EndState.SUCCESS) {
      Toast.makeText(getBaseContext(), R.string.export_failed, Toast.LENGTH_SHORT).show();
      return;
    }

    if (countFailedContactExports == 0 && countFailedEventExports == 0) {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentTitle(getString(R.string.export_complete))
          .setContentText(getString(R.string.export_completed_successfully));
    } else {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentTitle(getString(R.string.export_complete))
          .setContentText(getString(
              R.string.failed_to_copy_contacts_and_events,
              countFailedContactExports,
              countFailedEventExports
          ));
    }

    notifyManager.notify(NOTIFY_ID, notificationBuilder.build());
    Toast.makeText(getBaseContext(), R.string.export_complete, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread(getClass().getSimpleName(), HandlerThread.NORM_PRIORITY);
    thread.start();

    serviceHandler      = new ServiceHandler(thread.getLooper());
    notifyManager       = (NotificationManager)getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);
    notificationBuilder = new NotificationCompat.Builder(getBaseContext());
  }

  private final class ServiceHandler extends Handler {

    public ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      handleStartExport();
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    serviceHandler.sendMessage(serviceHandler.obtainMessage());
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

}
