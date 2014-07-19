package org.anhonesteffort.flock.test.webdav.caldav;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.Calendars;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.test.webdav.DavTestParams;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;

import java.util.Calendar;
import java.util.UUID;

/**
 * Programmer: rhodey
 * Date: 2/4/14
 */
public class CalDavCollectionTest extends AndroidTestCase {

  private CalDavCollection calDavCollection;

  @Override
  protected void setUp() throws Exception {
    CalDavStore calDavStore = new CalDavStore(DavTestParams.WEBDAV_HOST,
                                              DavTestParams.USERNAME,
                                              DavTestParams.PASSWORD,
                                              Optional.<String>absent(),
                                              Optional.<String>absent());

    Optional<String> calendarHomeSet = calDavStore.getCalendarHomeSet();
    String           COLLECTION_PATH = calendarHomeSet.get().concat("calendar/");

    calDavCollection = calDavStore.getCollection(COLLECTION_PATH).get();
  }

  public void testEditProperties() throws Exception {
    final Optional<String>  ORIGINAL_DISPLAY_NAME = calDavCollection.getDisplayName();
    final Optional<String>  ORIGINAL_DESCRIPTION  = calDavCollection.getDescription();
    final Optional<Integer> ORIGINAL_COLOR        = calDavCollection.getColor();

    final String  NEW_DISPLAY_NAME = "GOTO FAIL";
    final String  NEW_DESCRIPTION  = "TYPO-- I SWEAR!";
    final Integer NEW_COLOR        = 0xFF;

    calDavCollection.setDisplayName(NEW_DISPLAY_NAME);
    calDavCollection.setDescription(NEW_DESCRIPTION);
    calDavCollection.setColor(NEW_COLOR);

    assertEquals("Display name should be maintained.",
                 NEW_DISPLAY_NAME,
                 calDavCollection.getDisplayName().get());
    assertEquals("Description should be maintained.",
                 NEW_DESCRIPTION,
                 calDavCollection.getDescription().get());
    assertEquals("Color should be maintained.",
                 NEW_COLOR,
                 calDavCollection.getColor().get());

    if (ORIGINAL_DISPLAY_NAME.isPresent())
      calDavCollection.setDisplayName(ORIGINAL_DISPLAY_NAME.get());
    if (ORIGINAL_DESCRIPTION.isPresent())
      calDavCollection.setDescription(ORIGINAL_DESCRIPTION.get());
    if (ORIGINAL_COLOR.isPresent())
      calDavCollection.setColor(ORIGINAL_COLOR.get());
  }

  public void testEditTimeZone() throws Exception {
    net.fortuna.ical4j.model.Calendar putCalendar = new net.fortuna.ical4j.model.Calendar();
    putCalendar.getProperties().add(Version.VERSION_2_0);
    putCalendar.getProperties().add(CalScale.GREGORIAN);

    TimeZoneRegistry registry    = TimeZoneRegistryFactory.getInstance().createRegistry();
    TimeZone         timezone    = registry.getTimeZone("America/Mexico_City");
    VTimeZone        putTimeZone = timezone.getVTimeZone();

    putCalendar.getComponents().add(putTimeZone);
    calDavCollection.setTimeZone(putCalendar);

    VTimeZone gotTimeZone = (VTimeZone) calDavCollection.getTimeZone().get().getComponent(VTimeZone.VTIMEZONE);

    assertEquals("Time zone thing must be maintained.",
                 putTimeZone.getTimeZoneId().getValue(),
                 gotTimeZone.getTimeZoneId().getValue());
  }

  public void testAddGetRemoveComponent() throws Exception {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.MONTH, Calendar.JUNE);
    calendar.set(Calendar.DAY_OF_MONTH, 5);

    net.fortuna.ical4j.model.Calendar putCalendar = new net.fortuna.ical4j.model.Calendar();
    putCalendar.getProperties().add(Version.VERSION_2_0);
    putCalendar.getProperties().add(CalScale.GREGORIAN);

    Date putStartDate = new Date(calendar.getTime());
    Date putEndDate   = new Date(putStartDate.getTime() + (1000 * 60 * 60 * 24));

    VEvent vEventPut = new VEvent(putStartDate, putEndDate, "Tank Man");
    vEventPut.getProperties().add(new Uid(UUID.randomUUID().toString()));
    vEventPut.getProperties().add(new Description("THIS IS A LINE LONG ENOUGH TO BE SPLIT IN TWO BY THE ICAL FOLDING NONSENSE WHY DOES THIS EXIST?!?!??!?!?!?!?!??"));
    putCalendar.getComponents().add(vEventPut);

    calDavCollection.addComponent(putCalendar);

    Optional<ComponentETagPair<net.fortuna.ical4j.model.Calendar>> gotCalendar =
        calDavCollection.getComponent(Calendars.getUid(putCalendar).getValue());
    assertTrue("Added component must be found in the collection.",
               gotCalendar.isPresent());

    VEvent vEventGot = (VEvent) gotCalendar.get().getComponent().getComponent(VEvent.VEVENT);
    assertEquals("VEvent summary must be maintained.",
                 vEventGot.getSummary().getValue(),
                 vEventPut.getSummary().getValue());

    assertEquals("vEvent description must be maintained.",
                 vEventGot.getDescription().getValue(),
                 vEventPut.getDescription().getValue());

    assertEquals("VEvent start date must be maintained.",
                 vEventGot.getStartDate().getDate().getTime(),
                 putStartDate.getTime());

    assertEquals("VEvent end date must be maintained.",
                 vEventGot.getEndDate().getDate().getTime(),
                 putEndDate.getTime());

    assertTrue("VEvent should have an ETag", calDavCollection.getComponentETags().size() > 0);

    calDavCollection.removeComponent(Calendars.getUid(putCalendar).getValue());
    assertTrue("Removed component must not be found in the collection.",
               !calDavCollection.getComponent(Calendars.getUid(putCalendar).getValue()).isPresent());
  }

}
