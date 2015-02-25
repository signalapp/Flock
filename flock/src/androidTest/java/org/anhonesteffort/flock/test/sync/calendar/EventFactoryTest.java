/*
 * Copyright (C) 2015 Open Whisper Systems
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

package org.anhonesteffort.flock.test.sync.calendar;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.CalendarContract;
import android.test.AndroidTestCase;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.Attendee;

import org.anhonesteffort.flock.sync.calendar.EventFactory;

import java.net.URI;
import java.util.List;

/**
 * rhodey
 */
public class EventFactoryTest extends AndroidTestCase {

  public void testGetValuesForAttendees() throws Exception {
    final String   EMAIL  = "doge@such.wow";
    final String   NAME   = "crypto doge";
    final Role     ROLE   = Role.OPT_PARTICIPANT;
    final PartStat STATUS = PartStat.DECLINED;

    final Calendar inCalendar = new Calendar();
    final VEvent   inVEvent   = new VEvent();
    final Attendee inAttendee = new Attendee(new URI("mailto", EMAIL, null));

    inAttendee.getParameters().add(new Cn(NAME));
    inAttendee.getParameters().add(ROLE);
    inAttendee.getParameters().add(STATUS);

    inVEvent.getProperties().add(inAttendee);
    inCalendar.getComponents().add(inVEvent);

    final List<ContentValues> outValuesList = EventFactory.getValuesForAttendees(inCalendar);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL).equals(EMAIL));
    assertTrue(outValues.getAsString(CalendarContract.Attendees.ATTENDEE_NAME).equals(NAME));
    assertTrue(outValues.getAsInteger(CalendarContract.Attendees.ATTENDEE_TYPE).equals(CalendarContract.Attendees.TYPE_OPTIONAL));
    assertTrue(outValues.getAsInteger(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP).equals(CalendarContract.Attendees.RELATIONSHIP_ATTENDEE));
    assertTrue(outValues.getAsInteger(CalendarContract.Attendees.ATTENDEE_STATUS).equals(CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED));
  }

  public void testAddAttendee() throws Exception {
    final String  EMAIL  = "doge@such.wow";
    final String  NAME   = "crypto doge";
    final Integer ROLE   = CalendarContract.Attendees.RELATIONSHIP_ORGANIZER;
    final Integer TYPE   = CalendarContract.Attendees.TYPE_REQUIRED;
    final Integer STATUS = CalendarContract.Attendees.STATUS_CONFIRMED;

    final ContentValues inValues = new ContentValues();
    inValues.put(CalendarContract.Attendees.ATTENDEE_EMAIL,        EMAIL);
    inValues.put(CalendarContract.Attendees.ATTENDEE_NAME,         NAME);
    inValues.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, ROLE);
    inValues.put(CalendarContract.Attendees.ATTENDEE_TYPE,         TYPE);
    inValues.put(CalendarContract.Attendees.ATTENDEE_STATUS,       STATUS);

    final Calendar outCalendar = new Calendar();
    outCalendar.getComponents().add(new VEvent());

    EventFactory.addAttendee("wow", outCalendar, inValues);

    assertTrue(outCalendar.getComponent(VEvent.VEVENT) != null);
    final VEvent outVEvent = (VEvent) outCalendar.getComponent(VEvent.VEVENT);

    assertTrue(outVEvent.getProperties(Attendee.ATTENDEE).size() == 1);
    final Attendee outAttendee = (Attendee) outVEvent.getProperties(Attendee.ATTENDEE).get(0);

    final String   outEmail  = Uri.parse(outAttendee.getValue()).getSchemeSpecificPart();
    final String   outName   = outAttendee.getParameter(Cn.CN).getValue();
    final Role     outRole   = (Role) outAttendee.getParameter(Role.ROLE);
    final PartStat outStatus = (PartStat) outAttendee.getParameter(PartStat.PARTSTAT);

    assertTrue(outEmail.equals(EMAIL));
    assertTrue(outName.equals(NAME));
    assertTrue(outRole == Role.CHAIR);
    assertTrue(outStatus == PartStat.ACCEPTED);
  }

  public void testGetValuesForReminders() throws Exception {
    final Integer MINUTES_BEFORE_EVENT = 1337;

    final Calendar inCalendar = new Calendar();
    final VEvent   inVEvent   = new VEvent();
    final VAlarm   inAlarm    = new VAlarm(new Dur(0, 0, -MINUTES_BEFORE_EVENT, 0));

    inVEvent.getAlarms().add(inAlarm);
    inCalendar.getComponents().add(inVEvent);

    final List<ContentValues> outValuesList = EventFactory.getValuesForReminders(inCalendar);
    assertTrue(outValuesList.size() == 1);

    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsInteger(CalendarContract.Reminders.MINUTES).equals(MINUTES_BEFORE_EVENT));
    assertTrue(outValues.getAsInteger(CalendarContract.Reminders.METHOD).equals(CalendarContract.Reminders.METHOD_ALERT));
  }

  public void testAddReminder() throws Exception {
    final Integer MINUTES_BEFORE_EVENT = 1337;

    final ContentValues inValues = new ContentValues();
    inValues.put(CalendarContract.Reminders.MINUTES, MINUTES_BEFORE_EVENT);

    final Calendar outCalendar = new Calendar();
    outCalendar.getComponents().add(new VEvent());
    EventFactory.addReminder(outCalendar, inValues);

    assertTrue(outCalendar.getComponent(VEvent.VEVENT) != null);
    final VEvent outVEvent = (VEvent) outCalendar.getComponent(VEvent.VEVENT);

    assertTrue(outVEvent.getAlarms().size() == 1);
    final VAlarm outAlarm = (VAlarm) outVEvent.getAlarms().get(0);

    assertTrue(outAlarm.getTrigger().getDuration().getMinutes() == MINUTES_BEFORE_EVENT);
  }

}
