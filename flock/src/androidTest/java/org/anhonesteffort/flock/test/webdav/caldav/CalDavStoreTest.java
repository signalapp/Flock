package org.anhonesteffort.flock.test.webdav.caldav;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.test.webdav.DavTestParams;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;

import java.net.URLEncoder;

/**
 * Programmer: rhodey
 * Date: 2/4/14
 */
public class CalDavStoreTest extends AndroidTestCase {

  private CalDavStore calDavStore;

  @Override
  protected void setUp() throws Exception {
    calDavStore = new CalDavStore(DavTestParams.WEBDAV_HOST,
                                  DavTestParams.USERNAME,
                                  DavTestParams.PASSWORD,
                                  Optional.<String>absent(),
                                  Optional.<String>absent());
  }

  public void testDavCurrentUserPrincipal() throws Exception {
    Optional<String> currentUserPrincipal = calDavStore.getCurrentUserPrincipal();
    assertTrue("DAV:current-user-principal should be something.", currentUserPrincipal.isPresent());
  }

  public void testGetCollections() throws Exception {
    final String DEFAULT_COLLECTION_OWNER = "/principals/__uids__/" + URLEncoder.encode(DavTestParams.USERNAME) + "/";

    CalDavCollection collection = calDavStore.getCollections().get(0);

    assertEquals("Default calendar collection must be owned by " + DavTestParams.USERNAME,
                 collection.getOwnerHref().get(),
                 DEFAULT_COLLECTION_OWNER);
  }

  public void testAddGetRemoveSimpleCollection() throws Exception {
    Optional<String> calendarHomeSet = calDavStore.getCalendarHomeSet();
    assertTrue("Calendar home set property must be found.", calendarHomeSet.isPresent());

    final String COLLECTION_PATH = calendarHomeSet.get().concat("test-collection/");

    calDavStore.addCollection(COLLECTION_PATH);
    assertTrue("Added collection must be found in the store.",
               calDavStore.getCollection(COLLECTION_PATH).isPresent());

    calDavStore.removeCollection(COLLECTION_PATH);
    assertTrue("Removed collection must not be found in the store.",
               !calDavStore.getCollection(COLLECTION_PATH).isPresent());
  }

  public void testAddRemoveCollectionWithProperties() throws Exception {
    final String  DISPLAY_NAME = "Test Collection";
    final String  DESCRIPTION  = "This is a test collection.";
    final Integer COLOR        = 0xFF;

    Optional<String> calendarHomeSet = calDavStore.getCalendarHomeSet();
    assertTrue("Calendar home set property must be found.", calendarHomeSet.isPresent());

    final String COLLECTION_PATH = calendarHomeSet.get().concat("test-collection/");

    calDavStore.addCollection(COLLECTION_PATH, DISPLAY_NAME, DESCRIPTION, COLOR);
    Optional<CalDavCollection> collection = calDavStore.getCollection(COLLECTION_PATH);

    assertTrue("Added collection must be found in the store.",
               collection.isPresent());
    assertEquals("Added collection display name must be maintained.",
                 collection.get().getDisplayName().get(), DISPLAY_NAME);
    assertEquals("Added collection description must be maintained.",
                 collection.get().getDescription().get(), DESCRIPTION);
    assertEquals("Added collection color must be maintained.",
                 collection.get().getColor().get(), COLOR);

    calDavStore.removeCollection(COLLECTION_PATH);
    assertTrue("Removed collection must not be found in the store.",
               !calDavStore.getCollection(COLLECTION_PATH).isPresent());
  }

}
