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
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.sync.InvalidRemoteComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ConstraintViolationException;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.Calendars;
import org.anhonesteffort.flock.sync.AbstractLocalComponentCollection;
import org.anhonesteffort.flock.sync.InvalidLocalComponentException;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Programmer: rhodey
 */
public class LocalEventCollection extends AbstractLocalComponentCollection<Calendar> {

  private static final String TAG = "org.anhonesteffort.flock.sync.calendar.LocalEventCollection";

  private   static final String COLUMN_NAME_COLLECTION_C_TAG  = CalendarContract.Calendars.CAL_SYNC2;
  private   static final String COLUMN_NAME_COLLECTION_ORDER  = CalendarContract.Calendars.CAL_SYNC3;
  protected static final String COLUMN_NAME_COLLECTION_COPIED = CalendarContract.Calendars.CAL_SYNC4;

  public LocalEventCollection(ContentProviderClient client,
                              Account               account,
                              Long                  localId,
                              String                remotePath)
  {
    super(client, account, remotePath, localId);
  }

  public static Uri getSyncAdapterUri(Uri base, Account account) {
    return base.buildUpon()
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,  "true")
        .build();
  }

  @Override
  protected Uri getSyncAdapterUri(Uri base) {
    return getSyncAdapterUri(base, account);
  }

  @Override
  protected Uri handleAddAccountQueryParams(Uri uri) {
    return CalendarContract.Events.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .build();
  }

  protected static Uri getCollectionsUri(Account account) {
    return getSyncAdapterUri(CalendarContract.Calendars.CONTENT_URI, account);
  }

  private Uri getCollectionUri() {
    return ContentUris.withAppendedId(getCollectionsUri(account), localId);
  }

  @Override
  protected Uri getUriForComponents() {
    if (account != null)
      return getSyncAdapterUri(CalendarContract.Events.CONTENT_URI);

    return CalendarContract.Events.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .build();
  }

  private Uri getUriForAttendees() {
    return getSyncAdapterUri(CalendarContract.Attendees.CONTENT_URI);
  }

  private Uri getUriForReminders() {
    return getSyncAdapterUri(CalendarContract.Reminders.CONTENT_URI);
  }

  @Override
  protected String getColumnNameCollectionLocalId() {
    return CalendarContract.Events.CALENDAR_ID;
  }

  @Override
  protected String getColumnNameComponentLocalId() {
    return CalendarContract.Events._ID;
  }

  @Override
  protected String getColumnNameComponentUid() {
    return CalendarContract.Events._SYNC_ID;
  }

  @Override
  protected String getColumnNameComponentETag() {
    return CalendarContract.Events.SYNC_DATA1;
  }

  @Override
  protected String getColumnNameDirty() {
    return CalendarContract.Events.DIRTY;
  }

  @Override
  protected String getColumnNameDeleted() {
    return CalendarContract.Events.DELETED;
  }

  @Override
  protected String getColumnNameQueuedForMigration() {
    return CalendarContract.Events.SYNC_DATA3;
  }

  @Override
  protected String getColumnNameAccountType() {
    return CalendarContract.Events.ACCOUNT_TYPE;
  }

  @Override
  public List<Long> getNewComponentIds() throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentLocalId()};
    final String   SELECTION  = "(" + getColumnNameComponentUid() + " IS NULL OR " +
                                      CalendarContract.Events.SYNC_DATA2 + " > 0 OR " +
                                      getColumnNameQueuedForMigration()  + "=1) AND " +
                                      getColumnNameCollectionLocalId()   + "=" + localId;

    Cursor     cursor = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    List<Long> newIds = new LinkedList<Long>();

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext()) {
      if (!newIds.contains(cursor.getLong(0))) // android gets weird sometimes :(
        newIds.add(cursor.getLong(0));
    }
    cursor.close();

    return newIds;
  }

  @Override
  public void cleanComponent(Long localId) {
    Log.d(TAG, "cleanComponent() localId " + localId);

    pendingOperations.add(ContentProviderOperation
        .newUpdate(ContentUris.withAppendedId(getUriForComponents(), localId))
        .withValue(getColumnNameDirty(), 0)
        .withValue(CalendarContract.Events.SYNC_DATA2, null)
        .build());
  }

  @Override
  public Optional<String> getDisplayName() throws RemoteException {
    final String[] PROJECTION = new String[]{CalendarContract.Calendars.CALENDAR_DISPLAY_NAME};

    Cursor cursor      = client.query(getCollectionUri(), PROJECTION, null, null, null);
    String displayName = null;

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext())
      displayName = cursor.getString(0);
    cursor.close();

    return Optional.fromNullable(displayName);
  }

  public void setVisible(Boolean isVisible) {
    pendingOperations.add(ContentProviderOperation.newUpdate(getCollectionUri())
        .withValue(CalendarContract.Calendars.VISIBLE, isVisible ? 1 : 0)
        .build());
  }

  @Override
  public void setDisplayName(String displayName) {
    pendingOperations.add(ContentProviderOperation.newUpdate(getCollectionUri())
        .withValue(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
        .build());
  }

  public Optional<Integer> getColor() throws RemoteException {
    final String[] PROJECTION = new String[]{CalendarContract.Calendars.CALENDAR_COLOR};

    Cursor  cursor = client.query(getCollectionUri(), PROJECTION, null, null, null);
    Integer color  = null;

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext())
      color = cursor.getInt(0);
    cursor.close();

    if (color == null)
      return Optional.absent();

    return Optional.of(color);
  }

  public void setColor(int color) {
    pendingOperations.add(ContentProviderOperation.newUpdate(getCollectionUri())
        .withValue(CalendarContract.Calendars.CALENDAR_COLOR, color)
        .build());
  }

  @Override
  public Optional<String> getCTag() throws RemoteException {
    final String[] PROJECTION = new String[]{COLUMN_NAME_COLLECTION_C_TAG};

    Cursor cursor = client.query(getCollectionUri(), PROJECTION, null, null, null);
    String cTag   = null;

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext())
      cTag = cursor.getString(0);
    cursor.close();

    return Optional.fromNullable(cTag);
  }

  @Override
  public void setCTag(String cTag) throws RemoteException {
    pendingOperations.add(ContentProviderOperation.newUpdate(getCollectionUri())
        .withValue(COLUMN_NAME_COLLECTION_C_TAG, cTag)
        .build());
  }

  public Optional<Calendar> getTimeZone() throws RemoteException {
    final String[] PROJECTION = new String[]{CalendarContract.Calendars.CALENDAR_TIME_ZONE};

    Cursor cursor     = client.query(getCollectionUri(), PROJECTION, null, null, null);
    String timeZoneId = null;

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext())
      timeZoneId = cursor.getString(0);
    cursor.close();

    if (timeZoneId != null) {
      Calendar         calendar  = new Calendar();
      TimeZoneRegistry registry  = TimeZoneRegistryFactory.getInstance().createRegistry();
      VTimeZone        vTimeZone = registry.getTimeZone(timeZoneId).getVTimeZone();

      calendar.getProperties().add(Version.VERSION_2_0);
      calendar.getComponents().add(vTimeZone);

      return Optional.of(calendar);
    }

    return Optional.absent();
  }

  public void setTimeZone(Calendar timezone) throws PropertyParseException {
    VTimeZone vTimeZone = (VTimeZone) timezone.getComponent(VTimeZone.VTIMEZONE);

    if (vTimeZone != null && vTimeZone.getTimeZoneId() != null) {
      pendingOperations.add(ContentProviderOperation.newUpdate(getCollectionUri())
          .withValue(CalendarContract.Calendars.CALENDAR_TIME_ZONE, vTimeZone.getTimeZoneId().getValue())
          .build());
    }
    else
      throw new PropertyParseException("Calendar object must contain a valid VTimeZone component.",
                                       getPath(), CalDavConstants.PROPERTY_NAME_CALENDAR_TIMEZONE);
  }

  public Optional<Integer> getOrder() throws RemoteException {
    final String[] PROJECTION = new String[]{COLUMN_NAME_COLLECTION_ORDER};

    Cursor  cursor = client.query(getCollectionUri(), PROJECTION, null, null, null);
    Integer order  = null;

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext())
      order = cursor.getInt(0);
    cursor.close();

    return Optional.fromNullable(order);
  }

  public void setOrder(Integer order) {
    pendingOperations.add(ContentProviderOperation.newUpdate(getCollectionUri())
        .withValue(COLUMN_NAME_COLLECTION_ORDER, order)
        .build());
  }

  private void addAttendees(Long eventId, Calendar component)
      throws InvalidLocalComponentException, RemoteException
  {
    String   SELECTION      = CalendarContract.Attendees.EVENT_ID + "=?";
    String[] SELECTION_ARGS = new String[]{eventId.toString()};

    Cursor cursor = client.query(getUriForAttendees(),
                                 EventFactory.getProjectionForAttendee(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues attendeeValues = EventFactory.getValuesForAttendee(cursor);
      EventFactory.addAttendee(getPath(), component, attendeeValues);
    }
    cursor.close();
  }

  private void addReminders(Long eventId, Calendar component)
      throws InvalidLocalComponentException, RemoteException
  {
    String   SELECTION      = CalendarContract.Reminders.EVENT_ID + "=?";
    String[] SELECTION_ARGS = new String[]{eventId.toString()};

    Cursor cursor = client.query(getUriForReminders(),
                                 EventFactory.getProjectionForReminder(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues reminderValues = EventFactory.getValuesForReminder(getPath(), cursor);
      EventFactory.addReminder(getPath(), component, reminderValues);
    }
    cursor.close();
  }

  private void buildEvent(Long eventId, Calendar component)
      throws InvalidLocalComponentException, RemoteException
  {
    addAttendees(eventId, component);
    addReminders(eventId, component);
  }

  @Override
  public Optional<Calendar> getComponent(Long eventId)
      throws RemoteException, InvalidLocalComponentException
  {
    Cursor cursor = client.query(ContentUris.withAppendedId(getUriForComponents(), eventId),
                                 EventFactory.getProjectionForEvent(),
                                 null,
                                 null,
                                 null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext()) {
      ContentValues               eventValues = EventFactory.getValuesForEvent(cursor);
      ComponentETagPair<Calendar> component   = EventFactory.getEventComponent(getPath(), eventValues);

        buildEvent(eventId, component.getComponent());
        cursor.close();
        return Optional.of(component.getComponent());
    }

    cursor.close();
    return Optional.absent();
  }

  @Override
  public Optional<ComponentETagPair<Calendar>> getComponent(String uid)
      throws RemoteException, InvalidLocalComponentException
  {
    String   SELECTION      = getColumnNameComponentUid() + "=?";
    String[] SELECTION_ARGS = new String[]{uid};

    Cursor cursor = client.query(getUriForComponents(),
                                 EventFactory.getProjectionForEvent(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext()) {
      ContentValues               eventValues = EventFactory.getValuesForEvent(cursor);
      ComponentETagPair<Calendar> component   = EventFactory.getEventComponent(getPath(), eventValues);

      buildEvent(eventValues.getAsLong(CalendarContract.Events._ID), component.getComponent());
      cursor.close();
      return Optional.of(component);
    }

    cursor.close();
    return Optional.absent();
  }

  @Override
  public List<ComponentETagPair<Calendar>> getComponents()
      throws RemoteException, InvalidLocalComponentException
  {
    String   SELECTION      = getColumnNameCollectionLocalId() + "=?";
    String[] SELECTION_ARGS = new String[]{localId.toString()};

    List<ComponentETagPair<Calendar>> components = new LinkedList<ComponentETagPair<Calendar>>();
    Cursor                            cursor     = client.query(getUriForComponents(),
                                                                EventFactory.getProjectionForEvent(),
                                                                SELECTION,
                                                                SELECTION_ARGS, null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext()) {
      ContentValues eventValues = EventFactory.getValuesForEvent(cursor);
      Long          eventId     = eventValues.getAsLong(getColumnNameComponentLocalId());

      try {

        ComponentETagPair<Calendar> component = EventFactory.getEventComponent(getPath(), eventValues);
        buildEvent(eventValues.getAsLong(CalendarContract.Events._ID), component.getComponent());
        components.add(component);

      } catch (InvalidLocalComponentException e) {
        if (e.getUid().isPresent())
          throw new InvalidLocalComponentException(e.getMessage(), CalDavConstants.CALDAV_NAMESPACE,
                                                   getPath(), e.getUid().get(), eventId, e);

        throw new InvalidLocalComponentException(e.getMessage(), CalDavConstants.CALDAV_NAMESPACE,
                                                 getPath(), eventId, e);
      }
    }

    cursor.close();
    return components;
  }

  @Override
  public int addComponent(ComponentETagPair<Calendar> component)
      throws RemoteException, InvalidRemoteComponentException
  {
    ContentValues eventValues    = EventFactory.getValuesForEvent(this, localId, component);
    int           event_op_index = pendingOperations.size();

    pendingOperations.add(ContentProviderOperation.newInsert(getUriForComponents())
        .withValues(eventValues)
        .build());

    List<ContentValues> attendeeValues = EventFactory.getValuesForAttendees(component.getComponent());
    for (ContentValues attendee : attendeeValues) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForAttendees())
          .withValues(attendee)
          .withValueBackReference(CalendarContract.Attendees.EVENT_ID, event_op_index)
          .build());
    }

    List<ContentValues> reminderValues = EventFactory.getValuesForReminders(component.getComponent());
    for (ContentValues reminder : reminderValues) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForReminders())
          .withValues(reminder)
          .withValueBackReference(CalendarContract.Reminders.EVENT_ID, event_op_index)
          .build());
    }

    return pendingOperations.size() - event_op_index;
  }

  @Override
  public void removeComponent(String remoteUId) throws RemoteException {
    final String         SELECTION      = getColumnNameComponentUid() + "=?";
    final String[]       SELECTION_ARGS = new String[]{remoteUId};
    final Optional<Long> LOCAL_ID       = getLocalIdForUid(remoteUId);

    pendingOperations.add(ContentProviderOperation
        .newDelete(getUriForComponents())
        .withSelection(SELECTION, SELECTION_ARGS)
        .withYieldAllowed(true)
        .build());

    if (LOCAL_ID.isPresent()) {
      pendingOperations.add(ContentProviderOperation
          .newDelete(ContentUris.withAppendedId(getUriForAttendees(), LOCAL_ID.get()))
          .withYieldAllowed(true)
          .build());

      pendingOperations.add(ContentProviderOperation
          .newDelete(ContentUris.withAppendedId(getUriForReminders(), LOCAL_ID.get()))
          .withYieldAllowed(true)
          .build());
    }
  }

  @Override
  public int updateComponent(ComponentETagPair<Calendar> component)
      throws RemoteException, InvalidRemoteComponentException
  {
    try {

      String componentUid = Calendars.getUid(component.getComponent()).getValue();

      removeComponent(componentUid);
      return addComponent(component);

    } catch (ConstraintViolationException e) {
      Log.d(TAG, "caught exception while updating component ", e);
      throw new InvalidRemoteComponentException("Caught exception while parsing UID from calendar",
                                                CalDavConstants.CALDAV_NAMESPACE, getPath(), e);
    }
  }

  public void handleCorrectEventReminders() throws RemoteException {
    final ContentValues UPDATE_VALUES  = new ContentValues(1);
    final String        SELECTION      = CalendarContract.Reminders.METHOD + "=?";
    final String[]      SELECTION_ARGS = new String[] {
        Integer.toString(CalendarContract.Reminders.METHOD_DEFAULT)
    };

    UPDATE_VALUES.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
    int updateCount = client.update(getUriForReminders(), UPDATE_VALUES, SELECTION, SELECTION_ARGS);

    Log.d(TAG, "corrected " + updateCount + " event reminders.");
  }

  private boolean hasRecurrenceExceptions(Long eventId) throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentLocalId(), CalendarContract.Events.ORIGINAL_ID};
    final String   SELECTION  = CalendarContract.Events.ORIGINAL_ID + "=" + eventId;

    Cursor cursor = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext()) {
      cursor.close();
      return true;
    }

    cursor.close();
    return false;
  }

  private Optional<Long> getOriginalIdForRecurrenceException(Long recurrenceExceptionId)
      throws RemoteException
  {
    final String[] PROJECTION = new String[]{CalendarContract.Events.ORIGINAL_ID};
    final String   SELECTION  = getColumnNameComponentLocalId() + "=" + recurrenceExceptionId;

    Cursor cursor = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    Optional<Long> originalId = Optional.absent();
    if (cursor.moveToNext())
      originalId = Optional.of(cursor.getLong(0));

    cursor.close();
    return originalId;
  }

  private Optional<String> getUidForCopiedEventLocalId(Long copiedEventId) throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentUid()};
    final String   SELECTION  = CalendarContract.Events.SYNC_DATA2 + "=" + copiedEventId;

    Cursor cursor = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    Optional<String> uid = Optional.absent();
    if (cursor.moveToNext())
      uid = Optional.fromNullable(cursor.getString(0));

    cursor.close();
    return uid;
  }

  private void handleCorrectOrganizersAndAttendees(VEvent vEvent, Account toAccount)
      throws InvalidLocalComponentException
  {
    Uid uid = vEvent.getUid();
    if (uid != null)
      uid.setValue(null);

    Organizer oldOrganizer = vEvent.getOrganizer();
    if (oldOrganizer != null)
      vEvent.getProperties().remove(oldOrganizer);

    try {

      URI newOrganizerEmail = new URI("mailto", toAccount.name, null);
      Organizer newOrganizer = new Organizer(newOrganizerEmail);
      vEvent.getProperties().add(newOrganizer);

      PropertyList attendeeList = vEvent.getProperties(Attendee.ATTENDEE);
      for (int i = 0; i < attendeeList.size(); i++) {
        Attendee attendee = (Attendee) attendeeList.get(i);
        if (attendee != null) {
          String attendeeEmail = Uri.parse(attendee.getValue()).getSchemeSpecificPart();
          if (attendeeEmail != null && attendeeEmail.equals(account.name))
            attendee.setValue(new URI("mailto", toAccount.name, null).toString());
        }
      }

    } catch (URISyntaxException e) {
      Log.e(TAG, "caught exception while copying collection to account ", e);
      throw new InvalidLocalComponentException("caught exception while copying collection to account",
                                               CalDavConstants.CALDAV_NAMESPACE, getPath(), e);
    }
  }

  private boolean handleCommitPendingIfFull(LocalEventCollection   toCollection,
                                            List<Integer>          eventOperationCounts,
                                            CalendarCopiedListener listener,
                                            boolean                forceFull)
  {
    int operationSum = 0;
    for (int operationCount : eventOperationCounts)
      operationSum += operationCount;

    if (operationSum >= 100 || forceFull) {
      try {

        int pendingCount = toCollection.pendingOperations.size();
        int successCount = toCollection.commitPendingOperations();
        int failCount    = pendingCount - successCount;

        Log.d(TAG, pendingCount + " were pending " + successCount + " were committed");

        for (int operationCount : eventOperationCounts)
          listener.onEventCopied(account, toCollection.getAccount(), localId);

        if (failCount > 0)
          Log.e(TAG, "failed to commit " + failCount + "" +
                      "operations but no idea which events they're from!");

      } catch (OperationApplicationException e) {

        for (int operationCount : eventOperationCounts)
          listener.onEventCopyFailed(e, account, toCollection.getAccount(), localId);
        toCollection.pendingOperations.clear();

      } catch (RemoteException e) {

        for (int operationCount : eventOperationCounts)
          listener.onEventCopyFailed(e, account, toCollection.getAccount(), localId);
        toCollection.pendingOperations.clear();

      }

      return true;
    }
    return false;
  }

  private void handleCopyRecurrenceExceptions(Account                toAccount,
                                              LocalEventCollection   toCollection,
                                              CalendarCopiedListener listener)
      throws RemoteException
  {
    List<Long>    componentIds         = getComponentIds();
    List<Integer> eventOperationCounts = new LinkedList<Integer>();

    for (Long eventId : componentIds) {
      try {

        Optional<Calendar> copyComponent = getComponent(eventId);
        if (!copyComponent.isPresent())
          throw new InvalidLocalComponentException("absent returned on copy of " + eventId + " from " + localId,
                                                   CalDavConstants.CALDAV_NAMESPACE, getPath(), eventId);

        VEvent vEvent = (VEvent) copyComponent.get().getComponent(VEvent.VEVENT);
        if (vEvent != null) {

          Uid uid = vEvent.getUid();
          if (uid != null)
            uid.setValue(null);

          handleCorrectOrganizersAndAttendees(vEvent, toAccount);

          ComponentETagPair<Calendar> correctedComponent =
              new ComponentETagPair<Calendar>(copyComponent.get(), Optional.<String>absent());

          if (EventFactory.isRecurrenceException(vEvent)) {
            Log.d(TAG, "found recurrence exception (" + eventId + ") during copy, will copy over now");

            Optional<Long> originalId = getOriginalIdForRecurrenceException(eventId);
            if (!originalId.isPresent()) {
              throw new InvalidLocalComponentException("could not get original ID for recurrence exception",
                                                       CalDavConstants.CALDAV_NAMESPACE, getPath(), eventId);
            }

            Optional<String> parentUid = toCollection.getUidForCopiedEventLocalId(originalId.get());
            if (!parentUid.isPresent()) {
              throw new InvalidLocalComponentException("could not get uid for copied event local id",
                                                       CalDavConstants.CALDAV_NAMESPACE, getPath(), eventId);
            }

            EventFactory.handleReplaceOriginalSyncId(getPath(), parentUid.get(), vEvent);
            eventOperationCounts.add(toCollection.addComponent(correctedComponent));

            if (handleCommitPendingIfFull(toCollection, eventOperationCounts, listener, false))
              eventOperationCounts.clear();
          }
        }
        else
          throw new InvalidLocalComponentException("could not parse VEvent from calendar component.",
                                                   CalDavConstants.CALDAV_NAMESPACE, getPath(), eventId);

      } catch (InvalidComponentException e) {
        listener.onEventCopyFailed(e, getAccount(), toAccount, localId);
      }
    }

    if (toCollection.pendingOperations.size() > 0)
      handleCommitPendingIfFull(toCollection, eventOperationCounts, listener, true);
  }

  public void copyToAccount(Account                toAccount,
                            String                 newCalendarName,
                            int                    newCalendarColor,
                            CalendarCopiedListener listener)
      throws RemoteException
  {
    LocalCalendarStore             toStore        = new LocalCalendarStore(client, toAccount);
    String                         tempRemotePath = UUID.randomUUID().toString();
    Optional<LocalEventCollection> toCollection   = Optional.absent();

    List<Long>    componentIds         = getComponentIds();
    List<Integer> eventOperationCounts = new LinkedList<Integer>();

    Log.d(TAG, "copy my " + componentIds.size() + " events to account " + toAccount.name);

    try {

      toStore.addCollection(tempRemotePath, newCalendarName, newCalendarColor);
      toCollection = toStore.getCollection(tempRemotePath);

      if (!toCollection.isPresent()) {
        Log.e(TAG, "local calendar store for " + toAccount.name +
                   " returned absent for the collection we just copied");
        throw new RemoteException("LocalCalendarStore does not have a copy of our collection!");
      }

      toStore.setCollectionCopied(toCollection.get().getLocalId(), true);
      setVisible(false);

      commitPendingOperations();
      listener.onCalendarCopied(getAccount(), toAccount, localId);

    } catch (RemoteException e) {
      listener.onCalendarCopyFailed(e, getAccount(), toAccount, localId);
      return;
    } catch (OperationApplicationException e) {
      listener.onCalendarCopyFailed(e, getAccount(), toAccount, localId);
      return;
    }

    for (Long eventId : componentIds) {
      try {

        Optional<Calendar> copyComponent = getComponent(eventId);
        if (!copyComponent.isPresent())
          throw new InvalidLocalComponentException("absent returned on copy of " + eventId + " from " + localId,
                                                   CalDavConstants.CALDAV_NAMESPACE, getPath(), eventId);

        VEvent vEvent = (VEvent) copyComponent.get().getComponent(VEvent.VEVENT);
        if (vEvent != null) {

          Uid uid = vEvent.getUid();
          if (uid != null)
            uid.setValue(null);

          handleCorrectOrganizersAndAttendees(vEvent, toAccount);

          ComponentETagPair<Calendar> correctedComponent =
              new ComponentETagPair<Calendar>(copyComponent.get(), Optional.<String>absent());

          if (EventFactory.isRecurrenceException(vEvent))
            Log.d(TAG, "found recurrence exception (" + eventId + ") during copy, will copy over next");
          else if (hasRecurrenceExceptions(eventId)) {
            EventFactory.handleAttachPropertiesForCopiedRecurrenceWithExceptions(vEvent, eventId);
            eventOperationCounts.add(toCollection.get().addComponent(correctedComponent));

            if (handleCommitPendingIfFull(toCollection.get(), eventOperationCounts, listener, false))
              eventOperationCounts.clear();
          }
          else {
            eventOperationCounts.add(toCollection.get().addComponent(correctedComponent));

            if (handleCommitPendingIfFull(toCollection.get(), eventOperationCounts, listener, false))
              eventOperationCounts.clear();
          }
        }
        else
          throw new InvalidLocalComponentException("could not parse VEvent from calendar component.",
                                                   CalDavConstants.CALDAV_NAMESPACE, getPath(), eventId);

      } catch (InvalidComponentException e) {
        listener.onEventCopyFailed(e, getAccount(), toAccount, localId);
      }
    }

    if (toCollection.get().pendingOperations.size() > 0)
      handleCommitPendingIfFull(toCollection.get(), eventOperationCounts, listener, true);

    handleCopyRecurrenceExceptions(toAccount, toCollection.get(), listener);
  }
}
