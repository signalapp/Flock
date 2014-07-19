package org.anhonesteffort.flock.test.sync.calendar;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.test.sync.MockMasterCipher;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavStore;
import org.anhonesteffort.flock.test.webdav.DavTestParams;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;

import java.net.URLEncoder;

/**
 * Programmer: rhodey
 * Date: 2/25/14
 */
public class HidingCalDavStoreTest extends AndroidTestCase {

  private HidingCalDavStore hidingCalDavStore;

  @Override
  protected void setUp() throws Exception {
    hidingCalDavStore = new HidingCalDavStore(new MockMasterCipher(),
                                              DavTestParams.WEBDAV_HOST,
                                              DavTestParams.USERNAME,
                                              DavTestParams.PASSWORD,
                                              Optional.<String>absent(),
                                              Optional.<String>absent());
  }

  public void testGetCollections() throws Exception {
    final String DEFAULT_COLLECTION_OWNER = "/principals/__uids__/" + URLEncoder.encode(DavTestParams.USERNAME) + "/";

    CalDavCollection collection = hidingCalDavStore.getCollections().get(0);

    assertEquals("Default calendar collection must be owned by " + DavTestParams.USERNAME,
                 collection.getOwnerHref().get(),
                 DEFAULT_COLLECTION_OWNER);
  }

  public void testAddGetRemoveSimpleCollection() throws Exception {
    Optional<String> calendarHomeSet = hidingCalDavStore.getCalendarHomeSet();
    assertTrue("Calendar home set property must be found.", calendarHomeSet.isPresent());

    final String COLLECTION_PATH = calendarHomeSet.get().concat("delete-me/");

    hidingCalDavStore.addCollection(COLLECTION_PATH);
    assertTrue("Added collection must be found in the store.",
               hidingCalDavStore.getCollection(COLLECTION_PATH).isPresent());

    hidingCalDavStore.removeCollection(COLLECTION_PATH);
    assertTrue("Removed collection must not be found in the store.",
               !hidingCalDavStore.getCollection(COLLECTION_PATH).isPresent());
  }

  public void testAddRemoveCollectionWithProperties() throws Exception {
    final String  DISPLAY_NAME = "Test Collection";
    final Integer COLOR        = 0xFFFFFFFF;

    Optional<String> calendarHomeSet = hidingCalDavStore.getCalendarHomeSet();
    assertTrue("Calendar home set property must be found.", calendarHomeSet.isPresent());

    final String COLLECTION_PATH = calendarHomeSet.get().concat("delete-me/");

    hidingCalDavStore.addCollection(COLLECTION_PATH, DISPLAY_NAME, COLOR);
    Optional<HidingCalDavCollection> collection = hidingCalDavStore.getCollection(COLLECTION_PATH);

    assertTrue(  "Added collection must be found in the store.",      collection.isPresent());
    assertEquals("Added collection display name must be maintained.", collection.get().getHiddenDisplayName().get(), DISPLAY_NAME);
    assertEquals("Added collection color must be maintained.",        collection.get().getHiddenColor().get(),       COLOR);

    hidingCalDavStore.removeCollection(COLLECTION_PATH);
    assertTrue("Removed collection must not be found in the store.",
               !hidingCalDavStore.getCollection(COLLECTION_PATH).isPresent());
  }

}
