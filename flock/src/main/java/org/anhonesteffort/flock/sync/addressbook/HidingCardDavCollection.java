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

import android.util.Log;

import com.google.common.base.Optional;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.parameter.ImageType;
import ezvcard.property.Photo;
import ezvcard.property.RawProperty;
import ezvcard.property.StructuredName;

import org.anhonesteffort.flock.webdav.carddav.CardDavConstants;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.HidingDavCollection;
import org.anhonesteffort.flock.sync.HidingDavCollectionMixin;
import org.anhonesteffort.flock.sync.HidingUtil;
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
  public Optional<String> getHiddenDisplayName()
      throws PropertyParseException, InvalidMacException,
      GeneralSecurityException, IOException
  {
    return delegate.getHiddenDisplayName();
  }

  @Override
  public void setHiddenDisplayName(String displayName)
      throws DavException, IOException,
      InvalidMacException, GeneralSecurityException
  {
    delegate.setHiddenDisplayName(displayName);
  }

  @Override
  public Optional<String> getEncryptedKeyMaterial() throws PropertyParseException {
    return delegate.getEncryptedKeyMaterial();
  }

  @Override
  public void setEncryptedKeyMaterial(String encryptedKeyMaterial)
      throws DavException, IOException
  {
    delegate.setEncryptedKeyMaterial(encryptedKeyMaterial);
  }

  @Override
  public Optional<ComponentETagPair<VCard>> getHiddenComponent(String uid)
      throws InvalidComponentException, DavException,
      InvalidMacException, GeneralSecurityException, IOException
  {
    Optional<ComponentETagPair<VCard>> originalComponentPair = super.getComponent(uid);
    if (!originalComponentPair.isPresent())
      return Optional.absent();

    VCard       exposedVCard   = originalComponentPair.get().getComponent();
    RawProperty protectedVCard = exposedVCard.getExtendedProperty(PROPERTY_NAME_FLOCK_HIDDEN);

    if (protectedVCard == null)
      return originalComponentPair;

    String recoveredVCardText = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedVCard.getValue());
    VCard  recoveredVCard     = Ezvcard.parse(recoveredVCardText.replace("\\n", "\n")).first();

    if (exposedVCard.getPhotos().size() > 0) {
      Photo  protectedPhoto            = exposedVCard.getPhotos().get(0);
      String parameterFlockHiddenPhoto = protectedPhoto.getParameter(PARAMETER_NAME_FLOCK_HIDDEN_PHOTO);
      if (parameterFlockHiddenPhoto != null && parameterFlockHiddenPhoto.equals("true")) {
        byte[] recoveredPhotoData = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedPhoto.getData());
        Photo  recoveredPhoto     = new Photo(recoveredPhotoData, ImageType.JPEG);
        recoveredVCard.addPhoto(recoveredPhoto);
      }
      else
        Log.e(TAG, "received vcard a photo missing the " + PARAMETER_NAME_FLOCK_HIDDEN_PHOTO + " parameter.");
    }
    return Optional.of(new ComponentETagPair<VCard>(recoveredVCard,
                                                    originalComponentPair.get().getETag()));
  }

  @Override
  public List<ComponentETagPair<VCard>> getHiddenComponents()
      throws InvalidComponentException, DavException,
      InvalidMacException, GeneralSecurityException, IOException
  {
    List<ComponentETagPair<VCard>> exposedComponentPairs   = super.getComponents();
    List<ComponentETagPair<VCard>> recoveredComponentPairs = new LinkedList<ComponentETagPair<VCard>>();

    for (ComponentETagPair<VCard> exposedComponentPair : exposedComponentPairs) {
      VCard       exposedVCard   = exposedComponentPair.getComponent();
      RawProperty protectedVCard = exposedVCard.getExtendedProperty(PROPERTY_NAME_FLOCK_HIDDEN);

      if (protectedVCard == null)
        recoveredComponentPairs.add(exposedComponentPair);

      else {
        String recoveredVCardText = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedVCard.getValue());
        VCard  recoveredVCard     = Ezvcard.parse(recoveredVCardText.replace("\\n", "\n")).first();

        if (exposedVCard.getPhotos().size() > 0) {
          Photo protectedPhoto = exposedVCard.getPhotos().get(0);
          if (protectedPhoto.getParameter(PARAMETER_NAME_FLOCK_HIDDEN_PHOTO).equals("true")) {
            byte[] recoveredPhotoData = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedPhoto.getData());
            Photo  recoveredPhoto     = new Photo(recoveredPhotoData, ImageType.JPEG);
            recoveredVCard.addPhoto(recoveredPhoto);
          }
        }
        recoveredComponentPairs.add(new ComponentETagPair<VCard>(recoveredVCard,
                                                                 exposedComponentPair.getETag()));
      }
    }

    return recoveredComponentPairs;
  }

  protected void putHiddenComponentToServer(VCard exposedVCard, Optional<String> ifMatchETag)
      throws InvalidComponentException, GeneralSecurityException, IOException, DavException
  {
    if (exposedVCard.getUid() == null)
      throw new InvalidComponentException("Cannot put a VCard to server without UID!", false,
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
      Photo  protectedPhoto     = new Photo(protectedPhotoData, ImageType.JPEG);
      exposedVCard.removeProperties(Photo.class);

      protectedPhoto.addParameter(PARAMETER_NAME_FLOCK_HIDDEN_PHOTO, "true");
      protectedVCard.addPhoto(    protectedPhoto);
    }

    protectedVCard.addExtendedProperty(PROPERTY_NAME_FLOCK_HIDDEN,
                                       HidingUtil.encryptEncodeAndPrefix(masterCipher, Ezvcard.write(exposedVCard).go()));

    super.putComponentToServer(protectedVCard, ifMatchETag);
  }

  @Override
  public void addHiddenComponent(VCard component)
      throws InvalidComponentException, DavException, GeneralSecurityException, IOException
  {
    putHiddenComponentToServer(component, Optional.<String>absent());
    fetchProperties();
  }

  @Override
  public void updateHiddenComponent(ComponentETagPair<VCard> component)
      throws InvalidComponentException, DavException, GeneralSecurityException, IOException
  {
    putHiddenComponentToServer(component.getComponent(), component.getETag());
    fetchProperties();
  }

}
