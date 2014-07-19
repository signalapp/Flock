package org.anhonesteffort.flock.test.sync.addressbook;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.property.StructuredName;
import ezvcard.property.Uid;
import org.anhonesteffort.flock.test.sync.MockMasterCipher;
import org.anhonesteffort.flock.sync.addressbook.HidingCardDavCollection;
import org.anhonesteffort.flock.sync.addressbook.HidingCardDavStore;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.test.webdav.DavTestParams;

import java.util.UUID;

/**
 * Programmer: rhodey
 * Date: 2/25/14
 */
public class HidingCardDavCollectionTest extends AndroidTestCase {

  private HidingCardDavCollection hidingCardDavCollection;

  @Override
  protected void setUp() throws Exception {
    HidingCardDavStore hidingCardDavStore = new HidingCardDavStore(new MockMasterCipher(),
                                                                   DavTestParams.WEBDAV_HOST,
                                                                   DavTestParams.USERNAME,
                                                                   DavTestParams.PASSWORD,
                                                                   Optional.<String>absent(),
                                                                   Optional.<String>absent());

    Optional<String> addressbookHomeSet = hidingCardDavStore.getAddressbookHomeSet();
    String           COLLECTION_PATH    = addressbookHomeSet.get().concat("addressbook/");

    hidingCardDavCollection = hidingCardDavStore.getCollection(COLLECTION_PATH).get();
  }

  public void testEditProperties() throws Exception {
    final Optional<String> ORIGINAL_DISPLAY_NAME = hidingCardDavCollection.getHiddenDisplayName();

    final String NEW_DISPLAY_NAME = "Only 1337 people in here";

    hidingCardDavCollection.setHiddenDisplayName(NEW_DISPLAY_NAME);

    assertEquals("Addressbook display name must be maintained.",
                 NEW_DISPLAY_NAME,
                 hidingCardDavCollection.getHiddenDisplayName().get());

    if (ORIGINAL_DISPLAY_NAME.isPresent())
      hidingCardDavCollection.setDisplayName(ORIGINAL_DISPLAY_NAME.get());
  }

  public void testAddGetRemoveComponent() throws Exception {
    final StructuredName structuredName = new StructuredName();
    structuredName.setFamily("Strangelove");
    structuredName.setGiven("idk");
    structuredName.addPrefix("Dr");
    structuredName.addSuffix("");

    VCard putVCard = new VCard();
    putVCard.setVersion(VCardVersion.V3_0);
    putVCard.setUid(new Uid(UUID.randomUUID().toString()));
    putVCard.setStructuredName(structuredName);
    putVCard.setFormattedName("you need this too");

    hidingCardDavCollection.addComponent(putVCard);

    Optional<ComponentETagPair<VCard>> gotVCard = hidingCardDavCollection.getComponent(putVCard.getUid().getValue());
    assertTrue("Added component must be found in collection.", gotVCard.isPresent());

    assertEquals("vCard structured name must be maintained within the collection.",
                 gotVCard.get().getComponent().getStructuredName().getFamily(),
                 putVCard.getStructuredName().getFamily());

    hidingCardDavCollection.removeComponent(putVCard.getUid().getValue());

    assertTrue("Removed component must not be found in collection.",
               !hidingCardDavCollection.getComponent(putVCard.getUid().getValue()).isPresent());
  }
  
}
