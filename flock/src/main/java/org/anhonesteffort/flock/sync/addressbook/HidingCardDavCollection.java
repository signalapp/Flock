/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package org.anhonesteffort.flock.sync.addressbook;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.parameter.ImageType;
import ezvcard.property.Photo;
import ezvcard.property.RawProperty;
import ezvcard.property.StructuredName;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.sync.DecryptedMultiStatusResult;
import org.anhonesteffort.flock.sync.InvalidLocalComponentException;
import org.anhonesteffort.flock.sync.InvalidRemoteComponentException;
import org.anhonesteffort.flock.webdav.MultiStatusResult;
import org.anhonesteffort.flock.webdav.carddav.CardDavConstants;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.HidingDavCollection;
import org.anhonesteffort.flock.sync.HidingDavCollectionMixin;
import org.anhonesteffort.flock.crypto.HidingUtil;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.carddav.CardDavCollection;
import org.anhonesteffort.flock.webdav.carddav.CardDavStore;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class HidingCardDavCollection extends CardDavCollection implements HidingDavCollection<VCard> {

  private static final String TAG = "org.anhonesteffort.flock.sync.addressbook.HidingCardDavCollection";

  private static final String PROPERTY_NAME_FLOCK_HIDDEN        = "X-FLOCK-HIDDEN";
  private static final String PARAMETER_NAME_FLOCK_HIDDEN_PHOTO = "X-FLOCK-HIDDEN-PHOTO";

  private MasterCipher             masterCipher;
  private HidingDavCollectionMixin delegate;

  protected HidingCardDavCollection(CardDavStore cardDavStore,
                                    String       path,
                                    MasterCipher masterCipher)
  {
    super(cardDavStore, path);

    this.masterCipher = masterCipher;
    this.delegate     = new HidingDavCollectionMixin(this, masterCipher);
  }

  protected HidingCardDavCollection(CardDavCollection cardDavCollection,
                                    MasterCipher      masterCipher)
  {
    super((CardDavStore) cardDavCollection.getStore(),
          cardDavCollection.getPath(),
          cardDavCollection.getProperties());

    this.masterCipher = masterCipher;
    this.delegate     = new HidingDavCollectionMixin(this, masterCipher);
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForFetch() {
    DavPropertyNameSet addressbookProps = super.getPropertyNamesForFetch();
    DavPropertyNameSet hidingProps      = delegate.getPropertyNamesForFetch();

    addressbookProps.addAll(hidingProps);
    return addressbookProps;
  }

  @Override
  public boolean isFlockCollection() throws PropertyParseException {
    return delegate.isFlockCollection();
  }

  @Override
  public void makeFlockCollection(String displayName)
      throws DavException, IOException, GeneralSecurityException
  {
    delegate.makeFlockCollection(displayName, new DavPropertyNameSet());
  }

  @Override
  public Optional<String> getHiddenDisplayName()
      throws PropertyParseException, InvalidMacException,
      GeneralSecurityException, IOException
  {
    return delegate.getHiddenDisplayName();
  }

  @Override
  public void setHiddenDisplayName(String displayName)
      throws DavException, IOException, GeneralSecurityException
  {
    delegate.setHiddenDisplayName(displayName);
  }

  protected ComponentETagPair<VCard> getHiddenComponent(ComponentETagPair<VCard> exposedComponentPair)
      throws InvalidRemoteComponentException, InvalidMacException, GeneralSecurityException, IOException
  {
    VCard       exposedVCard   = exposedComponentPair.getComponent();
    RawProperty protectedVCard = exposedVCard.getExtendedProperty(PROPERTY_NAME_FLOCK_HIDDEN);

    if (protectedVCard == null)
      return exposedComponentPair;

    String recoveredVCardText = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedVCard.getValue());

    try {

      VCard recoveredVCard = Ezvcard.parse(recoveredVCardText.replace("\\n", "\n")).first();

      if (exposedVCard.getPhotos().size() > 0) {
        Photo  protectedPhoto            = exposedVCard.getPhotos().get(0);
        String parameterFlockHiddenPhoto = protectedPhoto.getParameter(PARAMETER_NAME_FLOCK_HIDDEN_PHOTO);
        if (parameterFlockHiddenPhoto != null && parameterFlockHiddenPhoto.equals("true")) {
          byte[] recoveredPhotoData = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedPhoto.getData());
          Photo  recoveredPhoto     = new Photo(recoveredPhotoData, ImageType.PNG);
          recoveredVCard.addPhoto(recoveredPhoto);
        }
      }

      return new ComponentETagPair<VCard>(recoveredVCard, exposedComponentPair.getETag());

    } catch (RuntimeException e) {
      if (exposedVCard.getUid() != null) {
        throw new InvalidRemoteComponentException("caught exception while parsing vcard from multi-status response",
                                                  CardDavConstants.CARDDAV_NAMESPACE, getPath(),
                                                  exposedVCard.getUid().getValue(), e);
      }
      throw new InvalidRemoteComponentException("caught exception while parsing vcard from multi-status response",
                                                CardDavConstants.CARDDAV_NAMESPACE, getPath(), e);
    }
  }

  @Override
  public Optional<ComponentETagPair<VCard>> getHiddenComponent(String uid)
      throws InvalidRemoteComponentException, DavException,
      InvalidMacException, GeneralSecurityException, IOException
  {
    try {

      Optional<ComponentETagPair<VCard>> originalComponentPair = super.getComponent(uid);
      if (!originalComponentPair.isPresent())
        return Optional.absent();

      return Optional.of(getHiddenComponent(originalComponentPair.get()));

    } catch (InvalidComponentException e) {
      throw new InvalidRemoteComponentException(e);
    }
  }

  @Override
  public DecryptedMultiStatusResult<VCard> getHiddenComponents(List<String> uids)
      throws DavException, GeneralSecurityException, IOException
  {
    MultiStatusResult<VCard>          exposedComponentPairs   = super.getComponents(uids);
    DecryptedMultiStatusResult<VCard> recoveredComponentPairs = new DecryptedMultiStatusResult<VCard>(
        new LinkedList<ComponentETagPair<VCard>>(),
        exposedComponentPairs.getInvalidComponentExceptions(),
        new LinkedList<InvalidMacException>()
    );

    for (ComponentETagPair<VCard> exposedComponentPair : exposedComponentPairs.getComponentETagPairs()) {
      try {

        recoveredComponentPairs.getComponentETagPairs().add(getHiddenComponent(exposedComponentPair));

      } catch (InvalidRemoteComponentException e) {
        recoveredComponentPairs.getInvalidComponentExceptions().add(e);
      } catch (InvalidMacException e) {
        recoveredComponentPairs.getInvalidMacExceptions().add(e);
      }
    }

    return recoveredComponentPairs;
  }

  @Override
  public DecryptedMultiStatusResult<VCard> getHiddenComponents()
      throws DavException, GeneralSecurityException, IOException
  {
    MultiStatusResult<VCard>          exposedComponentPairs   = super.getComponents();
    DecryptedMultiStatusResult<VCard> recoveredComponentPairs = new DecryptedMultiStatusResult<VCard>(
        new LinkedList<ComponentETagPair<VCard>>(),
        exposedComponentPairs.getInvalidComponentExceptions(),
        new LinkedList<InvalidMacException>()
    );

    for (ComponentETagPair<VCard> exposedComponentPair : exposedComponentPairs.getComponentETagPairs()) {
      try {

        recoveredComponentPairs.getComponentETagPairs().add(getHiddenComponent(exposedComponentPair));

      } catch (InvalidRemoteComponentException e) {
        recoveredComponentPairs.getInvalidComponentExceptions().add(e);
      } catch (InvalidMacException e) {
        recoveredComponentPairs.getInvalidMacExceptions().add(e);
      }
    }

    return recoveredComponentPairs;
  }

  protected void putHiddenComponentToServer(VCard exposedVCard, Optional<String> ifMatchETag)
      throws InvalidLocalComponentException, GeneralSecurityException, IOException, DavException
  {
    if (exposedVCard.getUid() == null)
      throw new InvalidLocalComponentException("Cannot put a VCard to server without UID!",
                                               CardDavConstants.CARDDAV_NAMESPACE, getPath());

    VCard protectedVCard = new VCard();
    protectedVCard.setVersion(exposedVCard.getVersion());
    protectedVCard.setUid(exposedVCard.getUid());

    StructuredName structuredName = new StructuredName();
    structuredName.setGiven("Open");
    structuredName.addAdditional("Whisper");
    structuredName.setFamily("Systems");
    protectedVCard.setStructuredName(structuredName);
    protectedVCard.setFormattedName("Open Whisper Systems");

    if (exposedVCard.getPhotos().size() > 0) {
      Photo  exposedPhoto       = exposedVCard.getPhotos().get(0);
      byte[] protectedPhotoData = HidingUtil.encryptEncodeAndPrefix(masterCipher, exposedPhoto.getData());
      Photo  protectedPhoto     = new Photo(protectedPhotoData, ImageType.PNG);
      exposedVCard.removeProperties(Photo.class);

      protectedPhoto.addParameter(PARAMETER_NAME_FLOCK_HIDDEN_PHOTO, "true");
      protectedVCard.addPhoto(    protectedPhoto);
    }

    protectedVCard.addExtendedProperty(PROPERTY_NAME_FLOCK_HIDDEN,
                                       HidingUtil.encryptEncodeAndPrefix(masterCipher, Ezvcard.write(exposedVCard).go()));

    try {

      super.putComponentToServer(protectedVCard, ifMatchETag);

    } catch (InvalidComponentException e) {
      throw new InvalidLocalComponentException(e);
    }
  }

  @Override
  public void addHiddenComponent(VCard component)
      throws InvalidLocalComponentException, DavException, GeneralSecurityException, IOException
  {
    putHiddenComponentToServer(component, Optional.<String>absent());
  }

  @Override
  public void updateHiddenComponent(ComponentETagPair<VCard> component)
      throws InvalidLocalComponentException, DavException, GeneralSecurityException, IOException
  {
    putHiddenComponentToServer(component.getComponent(), component.getETag());
  }

  @Override
  public void closeHttpConnection() {
    getStore().closeHttpConnection();
  }

}
