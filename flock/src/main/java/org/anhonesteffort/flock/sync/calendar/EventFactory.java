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

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Trigger;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.model.property.XProperty;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Programmer: rhodey
 *
 * Much thanks to the DAVDroid project and especially
 * Richard Hirner (bitfire web engineering) for leading
 * the way in shoving VEvent objects into Androids Calendar
 * Content Provider. This would have been much more of a
 * pain without a couple hints from the DAVDroid codebase.
 */
public class EventFactory {

  private static final String TAG = "org.anhonesteffort.flock.sync.calendar.EventFactory";

  private   static final String PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID       = "X-FLOCK-ORIGINAL-SYNC-ID";
  private   static final String PROPERTY_NAME_FLOCK_ORIGINAL_INSTANCE_TIME = "X-FLOCK-ORIGINAL-INSTANCE-TIME";
  protected static final String PROPERTY_NAME_FLOCK_COPY_EVENT_ID          = "X-FLOCK-COPY-EVENT-ID";

  protected static String[] getProjectionForEvent() {
    return new String[] {
        CalendarContract.Events._ID,                    // 00
        CalendarContract.Events.CALENDAR_ID,            // 01
        CalendarContract.Events.ORGANIZER,              // 02
        CalendarContract.Events.TITLE,                  // 03
        CalendarContract.Events.EVENT_LOCATION,         // 04
        CalendarContract.Events.DESCRIPTION,            // 05
        CalendarContract.Events.DTSTART,                // 06
        CalendarContract.Events.DTEND,                  // 07
        CalendarContract.Events.EVENT_TIMEZONE,         // 08
        CalendarContract.Events.EVENT_END_TIMEZONE,     // 09
        CalendarContract.Events.DURATION,               // 10
        CalendarContract.Events.ALL_DAY,                // 11
        CalendarContract.Events.RRULE,                  // 12
        CalendarContract.Events.RDATE,                  // 13
        CalendarContract.Events.EXRULE,                 // 14
        CalendarContract.Events.EXDATE,                 // 15
        CalendarContract.Events.HAS_ALARM,              // 16
        CalendarContract.Events.HAS_ATTENDEE_DATA,      // 17
        CalendarContract.Events.ORIGINAL_ID,            // 18
        CalendarContract.Events.ORIGINAL_SYNC_ID,       // 19
        CalendarContract.Events.ORIGINAL_INSTANCE_TIME, // 20
        CalendarContract.Events.ORIGINAL_ALL_DAY,       // 21
        CalendarContract.Events.AVAILABILITY,           // 22
        CalendarContract.Events.STATUS,                 // 23
        CalendarContract.Events._SYNC_ID,               // 24 UID
        CalendarContract.Events.SYNC_DATA1,             // 25 ETag
        CalendarContract.Events.SYNC_DATA2              // 26 copies event id
    };
  }

  protected static ContentValues getValuesForEvent(Cursor cursor) {
    ContentValues values = new ContentValues(25);

    values.put(CalendarContract.Events._ID,                    cursor.getInt(0));
    values.put(CalendarContract.Events.CALENDAR_ID,            cursor.getInt(1));
    values.put(CalendarContract.Events.ORGANIZER,              cursor.getString(2));
    values.put(CalendarContract.Events.TITLE,                  cursor.getString(3));
    values.put(CalendarContract.Events.EVENT_LOCATION,         cursor.getString(4));
    values.put(CalendarContract.Events.DESCRIPTION,            cursor.getString(5));
    values.put(CalendarContract.Events.DTSTART,                cursor.getLong(6));
    values.put(CalendarContract.Events.DTEND,                  cursor.getLong(7));
    values.put(CalendarContract.Events.EVENT_TIMEZONE,         cursor.getString(8));
    values.put(CalendarContract.Events.EVENT_END_TIMEZONE,     cursor.getString(9));
    values.put(CalendarContract.Events.DURATION,               cursor.getString(10));
    values.put(CalendarContract.Events.ALL_DAY,                (cursor.getInt(11) != 0));
    values.put(CalendarContract.Events.RRULE,                  cursor.getString(12));
    values.put(CalendarContract.Events.RDATE,                  cursor.getString(13));
    values.put(CalendarContract.Events.EXRULE,                 cursor.getString(14));
    values.put(CalendarContract.Events.EXDATE,                 cursor.getString(15));
    values.put(CalendarContract.Events.HAS_ALARM,              (cursor.getInt(16) != 0));
    values.put(CalendarContract.Events.HAS_ATTENDEE_DATA,      (cursor.getInt(17) != 0));
    values.put(CalendarContract.Events.ORIGINAL_ID,            cursor.getLong(18));
    values.put(CalendarContract.Events.ORIGINAL_SYNC_ID,       cursor.getString(19));
    values.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, cursor.getLong(20));
    values.put(CalendarContract.Events.ORIGINAL_ALL_DAY,       (cursor.getInt(21) != 0));
    values.put(CalendarContract.Events.AVAILABILITY,           cursor.getInt(22));
    values.put(CalendarContract.Events.STATUS,                 cursor.getInt(23));
    values.put(CalendarContract.Events._SYNC_ID,               cursor.getString(24));
    values.put(CalendarContract.Events.SYNC_DATA1,             cursor.getString(25));
    values.put(CalendarContract.Events.SYNC_DATA2,             cursor.getLong(26));

    return values;
  }

  protected static boolean isRecurrenceException(VEvent vEvent) {
    return vEvent.getProperty(EventFactory.PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID) != null;
  }

  protected static boolean isCopiedRecurrenceWithExceptions(VEvent vEvent) {
    return vEvent.getProperty(EventFactory.PROPERTY_NAME_FLOCK_COPY_EVENT_ID) != null;
  }

  protected static Optional<Long> getLocalIdFromCopiedRecurrenceWithExceptions(VEvent event) {
    if (!isCopiedRecurrenceWithExceptions(event))
      return Optional.absent();

    return Optional.of(Long.valueOf(
        event.getProperty(EventFactory.PROPERTY_NAME_FLOCK_COPY_EVENT_ID).getValue()
    ));
  }

  protected static void handleAttachPropertiesForCopiedRecurrenceWithExceptions(VEvent event,
                                                                                Long   eventId)
  {
    event.getProperties().add(new XProperty(PROPERTY_NAME_FLOCK_COPY_EVENT_ID, String.valueOf(eventId)));

    String uid = UUID.randomUUID().toString();
    Log.d(TAG, "setting uuid as " + uid);

    if (event.getUid() != null)
      event.getUid().setValue(uid);
    else
      event.getProperties().add(new Uid(uid));
  }

  private static void handleAttachPropertiesForCopiedRecurrenceWithExceptions(ContentValues values,
                                                                              VEvent        event)
  {
    Long copiedEventId = values.getAsLong(CalendarContract.Events.SYNC_DATA2);

    if (copiedEventId != null && copiedEventId > 0)
      event.getProperties().add(new XProperty(PROPERTY_NAME_FLOCK_COPY_EVENT_ID,
                                              String.valueOf(copiedEventId)));
  }

  private static void handleAddValuesForRecurrenceProperties(VEvent        event,
                                                             ContentValues values)
  {
    Property copyIdProp = event.getProperty(PROPERTY_NAME_FLOCK_COPY_EVENT_ID);

    if (copyIdProp != null)
      values.put(CalendarContract.Events.SYNC_DATA2, Long.valueOf(copyIdProp.getValue()));
  }

  protected static void handleReplaceOriginalSyncId(String path, String syncId, VEvent event)
    throws InvalidComponentException
  {
    try {

      Property originalSyncIdProp = event.getProperty(PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID);
      if (originalSyncIdProp != null)
        originalSyncIdProp.setValue(syncId);
      else
        event.getProperties().add(new XProperty(PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID, syncId));

    } catch (ParseException e) {
      throw new InvalidComponentException("cmon now ical4j", false, CalDavConstants.CALDAV_NAMESPACE, path, e);
    } catch (URISyntaxException e) {
      throw new InvalidComponentException("cmon now ical4j", false, CalDavConstants.CALDAV_NAMESPACE, path, e);
    } catch (IOException e) {
      throw new InvalidComponentException("cmon now ical4j", false, CalDavConstants.CALDAV_NAMESPACE, path, e);
    }
  }

  private static void handleAddValuesForDeletionExceptionToRecurring(LocalEventCollection hack,
                                                                     VEvent               vEvent,
                                                                     ContentValues        eventValues)
      throws InvalidComponentException, RemoteException
  {
    Log.w(TAG, "gonna try and import deletion exception to androids recurrence model...");

    Property originalSyncIdProp = vEvent.getProperty(PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID);

    if (originalSyncIdProp  == null || TextUtils.isEmpty(originalSyncIdProp.getValue()))
      throw new InvalidComponentException("original sync id prop required on recurring event deletion exceptions",
                                          false, CalDavConstants.CALDAV_NAMESPACE, hack.getPath());

    if (vEvent.getStartDate() == null || vEvent.getStartDate().getDate() == null)
      throw new InvalidComponentException("deletion exception VEvents must have a start time",
                                          false, CalDavConstants.CALDAV_NAMESPACE, hack.getPath());

    eventValues.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                    vEvent.getStartDate().getDate().getTime());

    if (vEvent.getProperty(RRule.RRULE) == null &&
        vEvent.getProperty(RRule.RDATE) == null &&
        (vEvent.getEndDate()            == null || vEvent.getEndDate().getDate()  == null) &&
        vEvent.getDuration()            == null)
    {
      eventValues.put(CalendarContract.Events.ALL_DAY,          1);
      eventValues.put(CalendarContract.Events.ORIGINAL_ALL_DAY, 1);
    }
    else if ((vEvent.getProperty(RRule.RRULE) != null ||
              vEvent.getProperty(RRule.RDATE) != null) &&
              vEvent.getDuration()            == null)
    {
      eventValues.put(CalendarContract.Events.ALL_DAY,          1);
      eventValues.put(CalendarContract.Events.ORIGINAL_ALL_DAY, 1);
    }

    Optional<Long> originalLocalId = hack.getLocalIdForUid(originalSyncIdProp.getValue());
    if (!originalLocalId.isPresent()) {
      throw new InvalidComponentException("unable to build content values for recurrence deletion " +
                                          "exception, cannot find original event " +
                                          originalSyncIdProp.getValue() + " in collection",
                                          false, CalDavConstants.CALDAV_NAMESPACE, hack.getPath());
    }

    eventValues.put(CalendarContract.Events.ORIGINAL_ID,      originalLocalId.get());
    eventValues.put(CalendarContract.Events.ORIGINAL_SYNC_ID, originalSyncIdProp.getValue());
    eventValues.put(CalendarContract.Events.STATUS,           CalendarContract.Events.STATUS_CANCELED);
  }

  private static void handleAddValuesForEditExceptionToRecurring(LocalEventCollection hack,
                                                                 VEvent               vEvent,
                                                                 ContentValues        eventValues)
      throws InvalidComponentException, RemoteException
  {
    Log.w(TAG, "gonna try and import edit exception to androids recurrence model...");

    Property originalSyncIdProp   = vEvent.getProperty(PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID);
    Property originalInstanceTime = vEvent.getProperty(PROPERTY_NAME_FLOCK_ORIGINAL_INSTANCE_TIME);

    if (originalSyncIdProp == null || TextUtils.isEmpty(originalSyncIdProp.getValue()))
      throw new InvalidComponentException("original sync id prop required on recurring event edit exceptions",
                                          false, CalDavConstants.CALDAV_NAMESPACE, hack.getPath());

    if (originalInstanceTime == null || TextUtils.isEmpty(originalInstanceTime.getValue()))
      throw new InvalidComponentException("original instance time prop required on recurring event edit exceptions",
                                          false, CalDavConstants.CALDAV_NAMESPACE, hack.getPath());


    eventValues.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                    Long.valueOf(originalInstanceTime.getValue()));

    if (vEvent.getProperty(RRule.RRULE) == null &&
        vEvent.getProperty(RRule.RDATE) == null &&
        (vEvent.getEndDate()            == null || vEvent.getEndDate().getDate()  == null) &&
        vEvent.getDuration()            == null)
    {
      eventValues.put(CalendarContract.Events.ALL_DAY,          1);
      eventValues.put(CalendarContract.Events.ORIGINAL_ALL_DAY, 1);
    }
    else if ((vEvent.getProperty(RRule.RRULE)  != null ||
        vEvent.getProperty(RRule.RDATE)        != null) &&
        vEvent.getDuration()                   == null)
    {
      eventValues.put(CalendarContract.Events.ALL_DAY,          1);
      eventValues.put(CalendarContract.Events.ORIGINAL_ALL_DAY, 1);
    }

    Optional<Long> originalLocalId = hack.getLocalIdForUid(originalSyncIdProp.getValue());
    if (!originalLocalId.isPresent()) {
      throw new InvalidComponentException("unable to build content values for recurrence edit " +
                                          "exception, cannot find original event " +
                                          originalSyncIdProp.getValue() + " in collection",
                                          false, CalDavConstants.CALDAV_NAMESPACE, hack.getPath());
    }

    eventValues.put(CalendarContract.Events.ORIGINAL_ID,      originalLocalId.get());
    eventValues.put(CalendarContract.Events.ORIGINAL_SYNC_ID, originalSyncIdProp.getValue());
  }

  protected static ContentValues getValuesForEvent(LocalEventCollection        hack,
                                                   Long                        calendarId,
                                                   ComponentETagPair<Calendar> component)
      throws InvalidComponentException, RemoteException
  {
    VEvent vEvent = (VEvent) component.getComponent().getComponent(VEvent.VEVENT);

    if (vEvent != null) {
      ContentValues values = new ContentValues();

      handleAddValuesForRecurrenceProperties(vEvent, values);
      values.put(CalendarContract.Events.CALENDAR_ID, calendarId);

      if (vEvent.getUid() != null && vEvent.getUid().getValue() != null)
        values.put(CalendarContract.Events._SYNC_ID, vEvent.getUid().getValue());
      else
        values.putNull(CalendarContract.Events._SYNC_ID);

      if (component.getETag().isPresent())
        values.put(CalendarContract.Events.SYNC_DATA1, component.getETag().get());

      DtStart dtStart = vEvent.getStartDate();
      if (dtStart != null && dtStart.getDate() != null) {
        if (dtStart.getTimeZone() != null)
          values.put(CalendarContract.Events.EVENT_TIMEZONE, dtStart.getTimeZone().getID());

        values.put(CalendarContract.Events.DTSTART, dtStart.getDate().getTime());
      }
      else {
        Log.e(TAG, "no start date found on event");
        throw new InvalidComponentException("no start date found on event", false,
                                            CalDavConstants.CALDAV_NAMESPACE, hack.getPath());
      }

      Status   status                   = vEvent.getStatus();
      Property originalInstanceTimeProp = vEvent.getProperty(PROPERTY_NAME_FLOCK_ORIGINAL_INSTANCE_TIME);

      if (status != null && status != Status.VEVENT_CANCELLED && originalInstanceTimeProp != null)
        handleAddValuesForEditExceptionToRecurring(hack, vEvent, values);

      if (status != null && status == Status.VEVENT_CONFIRMED)
        values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED);
      else if (status != null && status == Status.VEVENT_CANCELLED)
        handleAddValuesForDeletionExceptionToRecurring(hack, vEvent, values);
      else
        values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_TENTATIVE);

      Summary summary = vEvent.getSummary();
      if (summary != null)
        values.put(CalendarContract.Events.TITLE, summary.getValue());

      Location location = vEvent.getLocation();
      if (location != null)
        values.put(CalendarContract.Events.EVENT_LOCATION, location.getValue());

      Description description = vEvent.getDescription();
      if (description != null)
        values.put(CalendarContract.Events.DESCRIPTION, description.getValue());

      Transp transparency = vEvent.getTransparency();
      if (transparency != null && transparency == Transp.OPAQUE)
        values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
      else
        values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE);

      Organizer organizer = vEvent.getOrganizer();
      if (organizer != null && organizer.getCalAddress() != null) {
        URI organizerAddress = organizer.getCalAddress();
        if (organizerAddress.getScheme() != null && organizerAddress.getScheme().equalsIgnoreCase("mailto"))
          values.put(CalendarContract.Events.ORGANIZER, organizerAddress.getSchemeSpecificPart());
      }

      RRule  rRule  = (RRule)  vEvent.getProperty(RRule.RRULE);
      RDate  rDate  = (RDate)  vEvent.getProperty(RDate.RDATE);
      ExRule exRule = (ExRule) vEvent.getProperty(ExRule.EXRULE);
      ExDate exDate = (ExDate) vEvent.getProperty(ExDate.EXDATE);

      if (rRule != null)
        values.put(CalendarContract.Events.RRULE, rRule.getValue());
      if (rDate != null)
        values.put(CalendarContract.Events.RDATE, rDate.getValue());
      if (exRule != null)
        values.put(CalendarContract.Events.EXRULE, exRule.getValue());
      if (exDate != null)
        values.put(CalendarContract.Events.EXDATE, exDate.getValue());

      if (rRule == null && rDate == null) {
        DtEnd dtEnd = vEvent.getEndDate();

        if (dtEnd != null && dtEnd.getDate() != null && vEvent.getDuration() == null &&
           (dtStart.getDate().getTime() + DateUtils.DAY_IN_MILLIS) == dtEnd.getDate().getTime())
        {
          if (dtEnd.getTimeZone() != null)
            values.put(CalendarContract.Events.EVENT_TIMEZONE, dtEnd.getTimeZone().getID());

          values.put(CalendarContract.Events.DTEND, dtEnd.getDate().getTime());
          values.put(CalendarContract.Events.ALL_DAY, 1);
        }
        else if (dtEnd != null && dtEnd.getDate() != null) {
          if (dtEnd.getTimeZone() != null)
            values.put(CalendarContract.Events.EVENT_TIMEZONE, dtEnd.getTimeZone().getID());

          values.put(CalendarContract.Events.DTEND, dtEnd.getDate().getTime());
        }
        else if (vEvent.getDuration() != null) {
          Duration       duration = vEvent.getDuration();
          java.util.Date endDate  = duration.getDuration().getTime(dtStart.getDate());
          values.put(CalendarContract.Events.DTEND, endDate.getTime());
        }
        else {
          values.put(CalendarContract.Events.DTEND, dtStart.getDate().getTime() + DateUtils.DAY_IN_MILLIS);
          values.put(CalendarContract.Events.ALL_DAY, 1);
        }
      }
      else if (vEvent.getDuration() != null && vEvent.getDuration().getValue().equals("P1D")) {
        values.put(CalendarContract.Events.DURATION, vEvent.getDuration().getValue());
        values.put(CalendarContract.Events.ALL_DAY, 1);
      }
      else if (vEvent.getDuration() != null)
        values.put(CalendarContract.Events.DURATION, vEvent.getDuration().getValue());

      PropertyList attendees = vEvent.getProperties(Attendee.ATTENDEE);
      if (attendees != null && attendees.size() > 0)
        values.put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1);
      else
        values.put(CalendarContract.Events.HAS_ATTENDEE_DATA, 0);

      PropertyList alarms = vEvent.getProperties(VAlarm.VALARM);
      if (alarms != null && alarms.size() > 0)
        values.put(CalendarContract.Events.HAS_ALARM, 1);
      else
        values.put(CalendarContract.Events.HAS_ALARM, 0);

      return values;
    }

    Log.e(TAG, "no VEVENT found in component");
    throw new InvalidComponentException("no VEVENT found in component", false,
                                        CalDavConstants.CALDAV_NAMESPACE, hack.getPath());
  }

  private static void handleAddPropertiesForDeletionExceptionToRecurring(String        path,
                                                                         ContentValues eventValues,
                                                                         VEvent        vEvent)
      throws InvalidComponentException
  {
    Log.w(TAG, "gonna try and export deletion exception from androids recurrence model...");
    vEvent.getProperties().add(Status.VEVENT_CANCELLED);

    Long   originalLocalId = eventValues.getAsLong(CalendarContract.Events.ORIGINAL_ID);
    String originalSyncId  = eventValues.getAsString(CalendarContract.Events.ORIGINAL_SYNC_ID);
    String syncId          = eventValues.getAsString(CalendarContract.Events._SYNC_ID);

    if (TextUtils.isEmpty(originalSyncId))
      throw new InvalidComponentException("original sync id required on recurring event deletion exceptions",
                                          false, CalDavConstants.CALDAV_NAMESPACE, path);

    if (originalLocalId == null || originalLocalId < 1)
      throw new InvalidComponentException("original local id required on recurring event deletion exceptions",
                                          false, CalDavConstants.CALDAV_NAMESPACE, path);

    if (vEvent.getUid() == null)
      vEvent.getProperties().add(new Uid(syncId));
    else if (vEvent.getUid().getValue() == null)
      vEvent.getUid().setValue(syncId);

    XProperty originalSyncIdProp = new XProperty(PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID, originalSyncId);
    vEvent.getProperties().add(originalSyncIdProp);
  }

  private static void handleAddPropertiesForEditExceptionToRecurring(String        path,
                                                                     ContentValues eventValues,
                                                                     VEvent        vEvent)
      throws InvalidComponentException
  {
    Log.w(TAG, "gonna try and export edit exception from androids recurrence model...");

    Long   originalLocalId      = eventValues.getAsLong(CalendarContract.Events.ORIGINAL_ID);
    String originalSyncId       = eventValues.getAsString(CalendarContract.Events.ORIGINAL_SYNC_ID);
    Long   originalInstanceTime = eventValues.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME);
    String syncId               = eventValues.getAsString(CalendarContract.Events._SYNC_ID);

    if (TextUtils.isEmpty(originalSyncId))
      throw new InvalidComponentException("original sync id required on recurring event edit exceptions",
                                          false, CalDavConstants.CALDAV_NAMESPACE, path);

    if (originalLocalId == null || originalLocalId < 1 || originalInstanceTime == null)
      throw new InvalidComponentException("original local id and instance time required on recurring event edit exceptions",
                                          false, CalDavConstants.CALDAV_NAMESPACE, path);

    if (vEvent.getUid() == null)
      vEvent.getProperties().add(new Uid(syncId));
    else if (vEvent.getUid().getValue() == null)
      vEvent.getUid().setValue(syncId);

    vEvent.getProperties().add(new XProperty(PROPERTY_NAME_FLOCK_ORIGINAL_SYNC_ID,       originalSyncId));
    vEvent.getProperties().add(new XProperty(PROPERTY_NAME_FLOCK_ORIGINAL_INSTANCE_TIME, String.valueOf(originalInstanceTime)));
  }

  protected static ComponentETagPair<Calendar> getEventComponent(String        path,
                                                                 ContentValues eventValues)
    throws InvalidComponentException
  {
    Calendar         calendar = new Calendar();
    VEvent           vEvent   = new VEvent();
    TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();

    calendar.getProperties().add(Version.VERSION_2_0);
    handleAttachPropertiesForCopiedRecurrenceWithExceptions(eventValues, vEvent);

    String uidText = eventValues.getAsString(CalendarContract.Events._SYNC_ID);
    if (!StringUtils.isEmpty(uidText)) {
      Uid eventUid = new Uid(uidText);
      vEvent.getProperties().add(eventUid);
    }

    try {

      String organizerText = eventValues.getAsString(CalendarContract.Events.ORGANIZER);
      if (StringUtils.isNotEmpty(organizerText)) {
        URI       organizerEmail = new URI("mailto", organizerText, null);
        Organizer organizer      = new Organizer(organizerEmail);
        vEvent.getProperties().add(organizer);
      }

    } catch (URISyntaxException e) {
      Log.e(TAG, "caught exception while parsing URI from organizerText", e);
      throw new InvalidComponentException("caught exception while parsing URI from organizerText",
                                          false, CalDavConstants.CALDAV_NAMESPACE, path, e);
    }

    String summaryText = eventValues.getAsString(CalendarContract.Events.TITLE);
    if (StringUtils.isNotEmpty(summaryText)) {
      Summary summary = new Summary(summaryText);
      vEvent.getProperties().add(summary);
    }

    String locationText = eventValues.getAsString(CalendarContract.Events.EVENT_LOCATION);
    if (StringUtils.isNotEmpty(locationText)) {
      Location location = new Location(locationText);
      vEvent.getProperties().add(location);
    }

    String descriptionText = eventValues.getAsString(CalendarContract.Events.DESCRIPTION);
    if (StringUtils.isNotEmpty(descriptionText)) {
      Description description = new Description(descriptionText);
      vEvent.getProperties().add(description);
    }

    Integer status               = eventValues.getAsInteger(CalendarContract.Events.STATUS);
    Long    originalInstanceTime = eventValues.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME);
    if (status != null && status != CalendarContract.Events.STATUS_CANCELED &&
        originalInstanceTime != null && originalInstanceTime > 0)
    {
      handleAddPropertiesForEditExceptionToRecurring(path, eventValues, vEvent);
    }

    if (status != null && status == CalendarContract.Events.STATUS_CONFIRMED)
      vEvent.getProperties().add(Status.VEVENT_CONFIRMED);
    else if (status != null && status == CalendarContract.Events.STATUS_CANCELED)
      handleAddPropertiesForDeletionExceptionToRecurring(path, eventValues, vEvent);
    else
      vEvent.getProperties().add(Status.VEVENT_TENTATIVE);

    Integer availability = eventValues.getAsInteger(CalendarContract.Events.AVAILABILITY);
    if (availability != null && availability == CalendarContract.Events.AVAILABILITY_BUSY)
      vEvent.getProperties().add(Transp.OPAQUE);
    else
      vEvent.getProperties().add(Transp.TRANSPARENT);

    Long dtStartMilliseconds = eventValues.getAsLong(CalendarContract.Events.DTSTART);
    if (dtStartMilliseconds == null)
      dtStartMilliseconds = eventValues.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME);

    if (dtStartMilliseconds != null) {
      DtStart dtStart      = new DtStart(new Date(dtStartMilliseconds));
      String dtStartTZText = eventValues.getAsString(CalendarContract.Events.EVENT_TIMEZONE);

      if (dtStartTZText != null) {
        DateTime startDate     = new DateTime(dtStartMilliseconds);
        TimeZone startTimeZone = registry.getTimeZone(dtStartTZText);
        startDate.setTimeZone(startTimeZone);

        dtStart = new DtStart(startDate);
      }

      vEvent.getProperties().add(dtStart);
    }
    else {
      Log.e(TAG, "no start date found on event");
      throw new InvalidComponentException("no start date found on event", false,
                                          CalDavConstants.CALDAV_NAMESPACE, path);
    }

    Boolean allDay            = eventValues.getAsBoolean(CalendarContract.Events.ALL_DAY);
    Long    dtEndMilliseconds = eventValues.getAsLong(CalendarContract.Events.DTEND);

    if (allDay && eventValues.getAsString(CalendarContract.Events.DURATION) == null)
      dtEndMilliseconds = dtStartMilliseconds + DateUtils.DAY_IN_MILLIS;

    if (dtEndMilliseconds != null && dtEndMilliseconds > 0) {
      DtEnd  dtEnd         = new DtEnd(new Date(dtEndMilliseconds));
      String dtStartTZText = eventValues.getAsString(CalendarContract.Events.EVENT_TIMEZONE);

      if (dtStartTZText != null) {
        DateTime endDate     = new DateTime(dtEndMilliseconds);
        TimeZone endTimeZone = registry.getTimeZone(dtStartTZText);
        endDate.setTimeZone(endTimeZone);

        dtEnd = new DtEnd(endDate);
      }

      vEvent.getProperties().add(dtEnd);
    }

    String durationText = eventValues.getAsString(CalendarContract.Events.DURATION);
    if (StringUtils.isNotEmpty(durationText)) {
      Dur      dur      = new Dur(durationText);
      Duration duration = new Duration(dur);
      vEvent.getProperties().add(duration);
    }

    try {

      String rRuleText = eventValues.getAsString(CalendarContract.Events.RRULE);
      if (StringUtils.isNotEmpty(rRuleText)) {
        RRule rRule = new RRule(rRuleText);
        vEvent.getProperties().add(rRule);
      }

      String rDateText = eventValues.getAsString(CalendarContract.Events.RDATE);
      if (StringUtils.isNotEmpty(rDateText)) {
        RDate rDate = new RDate();
        rDate.setValue(rDateText);
        vEvent.getProperties().add(rDate);
      }

      String exRuleText = eventValues.getAsString(CalendarContract.Events.EXRULE);
      if (StringUtils.isNotEmpty(exRuleText)) {
        ExRule exRule = new ExRule();
        exRule.setValue(exRuleText);
        vEvent.getProperties().add(exRule);
      }

      String exDateText = eventValues.getAsString(CalendarContract.Events.EXDATE);
      if (StringUtils.isNotEmpty(exDateText)) {
        ExDate exDate = new ExDate();
        exDate.setValue(exDateText);
        vEvent.getProperties().add(exDate);
      }

    } catch (ParseException e) {
      Log.e(TAG, "caught exception while parsing recurrence rule stuff from event values", e);
      throw new InvalidComponentException("caught exception while parsing recurrence rule stuff from event values",
                                          false, CalDavConstants.CALDAV_NAMESPACE, path, e);
    }

    calendar.getComponents().add(vEvent);
    Optional<String> eTag = Optional.fromNullable(eventValues.getAsString(CalendarContract.Events.SYNC_DATA1));

    return new ComponentETagPair<Calendar>(calendar, eTag);
  }

  protected static String[] getProjectionForAttendee() {
    return new String[] {
        CalendarContract.Attendees.EVENT_ID,              // 00
        CalendarContract.Attendees.ATTENDEE_EMAIL,        // 01
        CalendarContract.Attendees.ATTENDEE_NAME,         // 02
        CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, // 03
        CalendarContract.Attendees.ATTENDEE_TYPE,         // 04
        CalendarContract.Attendees.ATTENDEE_STATUS,       // 05
        CalendarContract.Attendees.ATTENDEE_IDENTITY,     // 06
        CalendarContract.Attendees.ATTENDEE_ID_NAMESPACE  // 07
    };
  }

  protected static ContentValues getValuesForAttendee(Cursor cursor) {
    ContentValues values = new ContentValues(8);

    values.put(CalendarContract.Attendees.EVENT_ID,              cursor.getLong(0));
    values.put(CalendarContract.Attendees.ATTENDEE_EMAIL,        cursor.getString(1));
    values.put(CalendarContract.Attendees.ATTENDEE_NAME,         cursor.getString(2));
    values.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, cursor.getInt(3));
    values.put(CalendarContract.Attendees.ATTENDEE_TYPE,         cursor.getInt(4));
    values.put(CalendarContract.Attendees.ATTENDEE_STATUS,       cursor.getInt(5));
    values.put(CalendarContract.Attendees.ATTENDEE_IDENTITY,     cursor.getString(6));
    values.put(CalendarContract.Attendees.ATTENDEE_ID_NAMESPACE, cursor.getString(7));

    return values;
  }

  protected static List<ContentValues> getValuesForAttendees(Calendar component) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();
    VEvent              vEvent     = (VEvent) component.getComponent(VEvent.VEVENT);

    if (vEvent == null || vEvent.getProperties(Attendee.ATTENDEE) == null)
      return valuesList;

    PropertyList attendeeList = vEvent.getProperties(Attendee.ATTENDEE);

    for (int i = 0; i < attendeeList.size(); i++) {
      ContentValues values   = new ContentValues();
      Attendee      attendee = (Attendee) attendeeList.get(i);
      if (attendee != null) {

        String   email        = Uri.parse(attendee.getValue()).getSchemeSpecificPart();
        Cn       name         = (Cn) attendee.getParameter(Cn.CN);
        Role     relationship = (Role) attendee.getParameter(Parameter.ROLE);
        PartStat status       = (PartStat) attendee.getParameter(Parameter.PARTSTAT);

        if (StringUtils.isNotEmpty(email))
          values.put(CalendarContract.Attendees.ATTENDEE_EMAIL, email);

        if (name != null)
          values.put(CalendarContract.Attendees.ATTENDEE_NAME, name.getValue());

        if (relationship != null) {
          if (relationship == Role.CHAIR) {
            values.put(CalendarContract.Attendees.ATTENDEE_TYPE,
                       CalendarContract.Attendees.TYPE_REQUIRED);
            values.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                       CalendarContract.Attendees.RELATIONSHIP_ORGANIZER);
          }
          else if (relationship == Role.REQ_PARTICIPANT)  {
            values.put(CalendarContract.Attendees.ATTENDEE_TYPE,
                       CalendarContract.Attendees.TYPE_REQUIRED);
            values.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                       CalendarContract.Attendees.RELATIONSHIP_ATTENDEE);
          }
          else {
            values.put(CalendarContract.Attendees.ATTENDEE_TYPE,
                       CalendarContract.Attendees.TYPE_OPTIONAL);
            values.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                       CalendarContract.Attendees.RELATIONSHIP_ATTENDEE);
          }
        }
        else {
          values.put(CalendarContract.Attendees.ATTENDEE_TYPE,
                     CalendarContract.Attendees.TYPE_OPTIONAL);
          values.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                     CalendarContract.Attendees.RELATIONSHIP_ATTENDEE);
        }

        if (status != null) {
          if (status == PartStat.NEEDS_ACTION)
            values.put(CalendarContract.Attendees.ATTENDEE_STATUS,
                       CalendarContract.Attendees.ATTENDEE_STATUS_INVITED);
          else if (status == PartStat.TENTATIVE)
            values.put(CalendarContract.Attendees.ATTENDEE_STATUS,
                       CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE);
          else if (status == PartStat.ACCEPTED)
            values.put(CalendarContract.Attendees.ATTENDEE_STATUS,
                       CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED);
          else if (status == PartStat.DECLINED)
            values.put(CalendarContract.Attendees.ATTENDEE_STATUS,
                       CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED);
          else
            values.put(CalendarContract.Attendees.ATTENDEE_STATUS,
                       CalendarContract.Attendees.ATTENDEE_STATUS_NONE);
        }
        else
          values.put(CalendarContract.Attendees.ATTENDEE_STATUS,
                     CalendarContract.Attendees.ATTENDEE_STATUS_NONE);

        valuesList.add(values);
      }
    }

    return valuesList;
  }

  protected static void addAttendee(String path, Calendar component, ContentValues attendeeValues)
      throws InvalidComponentException
  {
    VEvent vEvent = (VEvent) component.getComponent(VEvent.VEVENT);
    if (vEvent == null) {
      Log.e(TAG, "unable to add attendee to component with no VEVENT");
      throw new InvalidComponentException("unable to add attendee to component with no VEVENT", false,
                                          CalDavConstants.CALDAV_NAMESPACE, path);
    }

    String  email        = attendeeValues.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL);
    String  name         = attendeeValues.getAsString(CalendarContract.Attendees.ATTENDEE_NAME);
    Integer type         = attendeeValues.getAsInteger(CalendarContract.Attendees.ATTENDEE_TYPE);
    Integer relationship = attendeeValues.getAsInteger(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP);
    Integer status       = attendeeValues.getAsInteger(CalendarContract.Attendees.ATTENDEE_STATUS);

    if (StringUtils.isEmpty(email)) {
      Log.e(TAG, "attendee email is null or empty");
      throw new InvalidComponentException("attendee email is null or empty", false,
                                          CalDavConstants.CALDAV_NAMESPACE, path);
    }

    try {

      Attendee attendee = new Attendee(new URI("mailto", email, null));
      ParameterList attendeeParams = attendee.getParameters();

      attendeeParams.add(CuType.INDIVIDUAL);

      if (StringUtils.isNotEmpty(name))
        attendeeParams.add(new Cn(name));

      if (relationship != null && relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER)
        attendeeParams.add(Role.CHAIR);
      else if (type != null && type == CalendarContract.Attendees.TYPE_REQUIRED)
        attendeeParams.add(Role.REQ_PARTICIPANT);
      else
        attendeeParams.add(Role.OPT_PARTICIPANT);

      if (status != null) {
        switch (status) {
          case CalendarContract.Attendees.ATTENDEE_STATUS_INVITED:
            attendeeParams.add(PartStat.NEEDS_ACTION);
            break;

          case CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED:
            attendeeParams.add(PartStat.ACCEPTED);
            break;

          case CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED:
            attendeeParams.add(PartStat.DECLINED);
            break;

          case CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE:
            attendeeParams.add(PartStat.TENTATIVE);
            break;
        }
      }
      vEvent.getProperties().add(attendee);

    } catch (URISyntaxException e) {
      Log.e(TAG, "caught exception while adding email to attendee", e);
      throw new InvalidComponentException("caught exception while adding email to attendee", false,
                                          CalDavConstants.CALDAV_NAMESPACE, path, e);
    }
  }

  protected static String [] getProjectionForReminder() {
    return new String[] {
        CalendarContract.Reminders.EVENT_ID, // 00
        CalendarContract.Reminders.MINUTES,  // 01
        CalendarContract.Reminders.METHOD    // 02
    };
  }

  protected static ContentValues getValuesForReminder(String path, Cursor cursor)
      throws InvalidComponentException
  {
    if (!cursor.isNull(0) && !cursor.isNull(1)) {
      ContentValues values = new ContentValues(3);

      values.put(CalendarContract.Reminders.EVENT_ID, cursor.getLong(0));
      values.put(CalendarContract.Reminders.MINUTES,  cursor.getInt(1));
      values.put(CalendarContract.Reminders.METHOD,   cursor.getInt(2));
      return values;
    }

    Log.e(TAG, "reminder event id or minutes is null");
    throw new InvalidComponentException("reminder event id or minutes is null", false,
                                        CalDavConstants.CALDAV_NAMESPACE, path);
  }

  protected static List<ContentValues> getValuesForReminders(Calendar component) {
    List<ContentValues> valueList = new LinkedList<ContentValues>();
    VEvent              vEvent = (VEvent) component.getComponent(VEvent.VEVENT);
    VToDo               vToDo  = (VToDo)  component.getComponent(VToDo.VTODO);
    ComponentList       vAlarms;

    if (vEvent != null)
      vAlarms = vEvent.getAlarms();
    else if (vToDo != null)
      vAlarms = vToDo.getAlarms();
    else
      return valueList;

    for (int i = 0; i < vAlarms.size(); i++) {
      VAlarm vAlarm = (VAlarm) vAlarms.get(i);
      if (vAlarm != null) {

        Trigger trigger = vAlarm.getTrigger();
        if (trigger != null && trigger.getDuration() != null) {
          ContentValues values = new ContentValues();
          values.put(CalendarContract.Reminders.MINUTES, (trigger.getDuration().getMinutes()));
          values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT);
          valueList.add(values);
        }
      }
    }

    return valueList;
  }

  // TODO: can we support more alarm types?
  protected static void addReminder(String path, Calendar component, ContentValues reminderValues)
    throws InvalidComponentException
  {
    Integer minutes = reminderValues.getAsInteger(CalendarContract.Reminders.MINUTES);

    if (minutes != null) {
      VAlarm       vAlarm     = new VAlarm(new Dur(0, 0, -minutes, 0));
      PropertyList alarmProps = vAlarm.getProperties();

      alarmProps.add(Action.DISPLAY);

      VEvent vEvent = (VEvent) component.getComponent(VEvent.VEVENT);
      VToDo  vToDo  = (VToDo) component.getComponent(VEvent.VTODO);

      if (vEvent != null && vEvent.getSummary() != null) {
        alarmProps.add(new Description(vEvent.getSummary().getValue()));
        vEvent.getAlarms().add(vAlarm);
      }
      else if (vToDo != null && vToDo.getSummary() != null) {
        alarmProps.add(new Description(vToDo.getSummary().getValue()));
        vToDo.getAlarms().add(vAlarm);
      }
    }
    else {
      Log.e(TAG, "reminder minutes is null");
      throw new InvalidComponentException("reminder minutes is null", false,
                                          CalDavConstants.CALDAV_NAMESPACE, path);
    }
  }

}
