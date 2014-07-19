package org.anhonesteffort.flock.test.sync.calendar;

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
import org.anhonesteffort.flock.test.sync.MockMasterCipher;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavStore;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.test.webdav.DavTestParams;

import java.util.Calendar;
import java.util.UUID;

/**
 * Programmer: rhodey
 * Date: 2/25/14
 */
public class HidingCalDavCollectionTest extends AndroidTestCase {

  private HidingCalDavCollection hidingCalDavCollection;

  @Override
  protected void setUp() throws Exception {
    HidingCalDavStore hidingCalDavStore = new HidingCalDavStore(new MockMasterCipher(),
                                                                DavTestParams.WEBDAV_HOST,
                                                                DavTestParams.USERNAME,
                                                                DavTestParams.PASSWORD,
                                                                Optional.<String>absent(),
                                                                Optional.<String>absent());

    Optional<String> calendarHomeSet = hidingCalDavStore.getCalendarHomeSet();
    String           COLLECTION_PATH = calendarHomeSet.get().concat("calendar/");

    hidingCalDavCollection = hidingCalDavStore.getCollection(COLLECTION_PATH).get();
  }

  public void testEditTimeZone() throws Exception {
    net.fortuna.ical4j.model.Calendar putCalendar = new net.fortuna.ical4j.model.Calendar();
    putCalendar.getProperties().add(Version.VERSION_2_0);
    putCalendar.getProperties().add(CalScale.GREGORIAN);

    TimeZoneRegistry registry    = TimeZoneRegistryFactory.getInstance().createRegistry();
    TimeZone         timezone    = registry.getTimeZone("America/Mexico_City");
    VTimeZone        putTimeZone = timezone.getVTimeZone();

    putCalendar.getComponents().add(putTimeZone);
    hidingCalDavCollection.setTimeZone(putCalendar);

    VTimeZone gotTimeZone = (VTimeZone) hidingCalDavCollection.getTimeZone().get().getComponent(VTimeZone.VTIMEZONE);

    assertEquals("Time zone thing must be maintained.",
                 putTimeZone.getTimeZoneId().getValue(),
                 gotTimeZone.getTimeZoneId().getValue());
  }

  public void testEditProperties() throws Exception {
    final Optional<String>  ORIGINAL_DISPLAY_NAME = hidingCalDavCollection.getHiddenDisplayName();
    final Optional<Integer> ORIGINAL_COLOR        = hidingCalDavCollection.getHiddenColor();

    final String  NEW_DISPLAY_NAME = "GOTO FAIL";
    final Integer NEW_COLOR        = 0xFFFFFFFF;

    hidingCalDavCollection.setHiddenDisplayName(NEW_DISPLAY_NAME);
    hidingCalDavCollection.setHiddenColor(NEW_COLOR);

    assertEquals("Display name should be maintained.", NEW_DISPLAY_NAME, hidingCalDavCollection.getHiddenDisplayName().get());
    assertEquals("Color should be maintained.",        NEW_COLOR,        hidingCalDavCollection.getHiddenColor().get());

    if (ORIGINAL_DISPLAY_NAME.isPresent())
      hidingCalDavCollection.setDisplayName(ORIGINAL_DISPLAY_NAME.get());
    if (ORIGINAL_COLOR.isPresent())
      hidingCalDavCollection.setColor(ORIGINAL_COLOR.get());
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

    hidingCalDavCollection.addComponent(putCalendar);

    Optional<ComponentETagPair<net.fortuna.ical4j.model.Calendar>> gotCalendar = hidingCalDavCollection.getComponent(Calendars.getUid(putCalendar).getValue());
    assertTrue("Added component must be found in the collection.", gotCalendar.isPresent());

    VEvent vEventGot = (VEvent) putCalendar.getComponent(VEvent.VEVENT);
    assertEquals("vEvent summary must be maintained.",
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

    hidingCalDavCollection.removeComponent(Calendars.getUid(putCalendar).getValue());
    assertTrue("Removed component must not be found in the collection.",
               !hidingCalDavCollection.getComponent(Calendars.getUid(putCalendar).getValue()).isPresent());
  }

}
