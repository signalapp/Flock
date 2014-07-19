package org.anhonesteffort.flock.test.webdav.carddav;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.test.webdav.DavTestParams;
import org.anhonesteffort.flock.webdav.carddav.CardDavCollection;
import org.anhonesteffort.flock.webdav.carddav.CardDavStore;

import java.net.URLEncoder;

/**
 * Programmer: rhodey
 * Date: 2/4/14
 */
public class CardDavStoreTest extends AndroidTestCase {

  private CardDavStore cardDavStore;

  @Override
  protected void setUp() throws Exception {
    cardDavStore = new CardDavStore(DavTestParams.WEBDAV_HOST,
                                    DavTestParams.USERNAME,
                                    DavTestParams.PASSWORD,
                                    Optional.<String>absent(),
                                    Optional.<String>absent());
  }

  public void testDavCurrentUserPrincipal() throws Exception {
    Optional<String> currentUserPrincipal = cardDavStore.getCurrentUserPrincipal();
    assertTrue("DAV:current-user-principal should be something.", currentUserPrincipal.isPresent());
  }

  public void testGetCollections() throws Exception {
    final String DEFAULT_COLLECTION_OWNER = "/principals/__uids__/" + URLEncoder.encode(DavTestParams.USERNAME) + "/";

    CardDavCollection collection  = cardDavStore.getCollections().get(0);

    assertEquals("Default addressbook collection must be owned by " + DavTestParams.USERNAME,
                 collection.getOwnerHref().get(),
                 DEFAULT_COLLECTION_OWNER);
  }

  // TODO: implement address book creation in Darwin Calendar Server so this can be tested.
  /*
  @Test
  public void addGetRemoveSimpleCollection() throws Exception {
    Optional<String> addressbookHomeSet = cardDavStore.getAddressbookHomeSet();
    assertTrue("Address book home set property must be found.", addressbookHomeSet.isPresent());

    final String COLLECTION_PATH = addressbookHomeSet.get().concat("test-collection/");

    cardDavStore.addCollection(COLLECTION_PATH);
    assertTrue("Added collection must be found in the store.", cardDavStore.getCollection(COLLECTION_PATH).isPresent());

    cardDavStore.removeCollection(COLLECTION_PATH);
    assertTrue("Removed collection must not be found in the store.", !cardDavStore.getCollection(COLLECTION_PATH).isPresent());
  } */

}
