package org.anhonesteffort.flock.test.sync.addressbook;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.test.sync.MockMasterCipher;
import org.anhonesteffort.flock.sync.addressbook.HidingCardDavStore;
import org.anhonesteffort.flock.test.webdav.DavTestParams;
import org.anhonesteffort.flock.webdav.carddav.CardDavCollection;

import java.net.URLEncoder;

/**
 * Programmer: rhodey
 * Date: 2/25/14
 */
public class HidingCardDavStoreTest extends AndroidTestCase {

  private HidingCardDavStore hidingCardDavStore;

  @Override
  protected void setUp() throws Exception {
    hidingCardDavStore = new HidingCardDavStore(new MockMasterCipher(),
                                                DavTestParams.WEBDAV_HOST,
                                                DavTestParams.USERNAME,
                                                DavTestParams.PASSWORD,
                                                Optional.<String>absent(),
                                                Optional.<String>absent());
  }

  public void testGetCollections() throws Exception {
    final String DEFAULT_COLLECTION_OWNER = "/principals/__uids__/" + URLEncoder.encode(DavTestParams.USERNAME) + "/";

    CardDavCollection collection  = hidingCardDavStore.getCollections().get(0);

    assertEquals("Default addressbook collection must be owned by " + DavTestParams.USERNAME,
                 collection.getOwnerHref().get(),
                 DEFAULT_COLLECTION_OWNER);
  }

}
