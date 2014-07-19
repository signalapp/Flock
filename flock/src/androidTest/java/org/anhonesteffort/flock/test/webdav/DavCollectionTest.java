package org.anhonesteffort.flock.test.webdav;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;

/**
 * Programmer: rhodey
 * Date: 2/4/14
 */
public class DavCollectionTest extends AndroidTestCase {

  private CalDavCollection davCollection;

  @Override
  protected void setUp() throws Exception {
    CalDavStore calDavStore = new CalDavStore(DavTestParams.WEBDAV_HOST,
                                              DavTestParams.USERNAME,
                                              DavTestParams.PASSWORD,
                                              Optional.<String>absent(),
                                              Optional.<String>absent());

    Optional<String> calendarHomeSet = calDavStore.getCalendarHomeSet();
    String           COLLECTION_PATH = calendarHomeSet.get().concat("calendar/");

    davCollection = calDavStore.getCollection(COLLECTION_PATH).get();
  }

  public void testEditProperties() throws Exception {
    final String DISPLAY_NAME = "calendar";

    davCollection.setDisplayName(DISPLAY_NAME);

    assertEquals("Collection must persist property changes.",
                 DISPLAY_NAME,
                 davCollection.getDisplayName().get());
  }

  public void testCTag() throws Exception  {
    Optional<String> cTag = davCollection.getCTag();
    assertTrue("All dav collections should have a CTag.", cTag.isPresent());
  }

}
