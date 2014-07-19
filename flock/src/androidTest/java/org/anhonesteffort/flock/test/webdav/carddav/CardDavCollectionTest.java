package org.anhonesteffort.flock.test.webdav.carddav;

import android.test.AndroidTestCase;

import com.google.common.base.Optional;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.property.StructuredName;
import ezvcard.property.Uid;
import org.anhonesteffort.flock.test.webdav.DavTestParams;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.carddav.CardDavCollection;
import org.anhonesteffort.flock.webdav.carddav.CardDavStore;

import java.util.UUID;

/**
 * Programmer: rhodey
 * Date: 2/4/14
 */
public class CardDavCollectionTest extends AndroidTestCase {

  private CardDavCollection cardDavCollection;

  @Override
  protected void setUp() throws Exception {
    CardDavStore cardDavStore = new CardDavStore(DavTestParams.WEBDAV_HOST,
                                                 DavTestParams.USERNAME,
                                                 DavTestParams.PASSWORD,
                                                 Optional.<String>absent(),
                                                 Optional.<String>absent());

    Optional<String> addressbookHomeSet = cardDavStore.getAddressbookHomeSet();
    String           COLLECTION_PATH    = addressbookHomeSet.get().concat("addressbook/");

    cardDavCollection = cardDavStore.getCollection(COLLECTION_PATH).get();
  }

  public void testEditProperties() throws Exception {
    final Optional<String> ORIGINAL_DISPLAY_NAME = cardDavCollection.getDisplayName();
    final Optional<String> ORIGINAL_DESCRIPTION  = cardDavCollection.getDescription();

    final String NEW_DISPLAY_NAME = "Only 1337 people in here";
    final String NEW_DESCRIPTION  = "CardDAV things insist that addressbook is one word.";

    cardDavCollection.setDisplayName(NEW_DISPLAY_NAME);
    cardDavCollection.setDescription(NEW_DESCRIPTION);

    assertEquals("Addressbook display name must be maintained.",
                 NEW_DISPLAY_NAME,
                 cardDavCollection.getDisplayName().get());
    assertEquals("Addressbook description must be maintained.",
                 NEW_DESCRIPTION,
                 cardDavCollection.getDescription().get());

    if (ORIGINAL_DISPLAY_NAME.isPresent())
      cardDavCollection.setDisplayName(ORIGINAL_DISPLAY_NAME.get());
    if (ORIGINAL_DESCRIPTION.isPresent())
      cardDavCollection.setDescription(ORIGINAL_DESCRIPTION.get());
  }

  public void testAddGetRemoveComponent() throws Exception {
    final StructuredName structuredName = new StructuredName();
    structuredName.setFamily("Strangelove");
    structuredName.setGiven("?");
    structuredName.addPrefix("Dr");
    structuredName.addSuffix("");

    VCard putVCard = new VCard();
    putVCard.setVersion(VCardVersion.V3_0);
    putVCard.setUid(new Uid(UUID.randomUUID().toString()));
    putVCard.setStructuredName(structuredName);
    putVCard.setFormattedName("you need this too");

    cardDavCollection.addComponent(putVCard);

    Optional<ComponentETagPair<VCard>> gotVCard = cardDavCollection.getComponent(putVCard.getUid().getValue());
    assertTrue("Added component must be found in collection.", gotVCard.isPresent());

    assertEquals("vCard structured name must be maintained within the collection.",
                 gotVCard.get().getComponent().getStructuredName().getFamily(),
                 structuredName.getFamily());

    cardDavCollection.removeComponent(putVCard.getUid().getValue());

    assertTrue("Removed component must not be found in collection.",
               !cardDavCollection.getComponent(putVCard.getUid().getValue()).isPresent());
  }

}
