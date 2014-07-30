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

package org.anhonesteffort.flock.sync;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.webdav.AbstractDavComponentCollection;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Programmer: rhodey
 */
public class HidingDavCollectionMixin {

  protected static final String          PROPERTY_NAME_FLOCK_COLLECTION = "X-FLOCK-COLLECTION";
  public    static final DavPropertyName PROPERTY_FLOCK_COLLECTION      = DavPropertyName.create(
      PROPERTY_NAME_FLOCK_COLLECTION,
      OwsWebDav.NAMESPACE
  );

  private AbstractDavComponentCollection collection;
  private MasterCipher                   masterCipher;

  public HidingDavCollectionMixin(AbstractDavComponentCollection collection,
                                  MasterCipher                   masterCipher)
  {
    this.collection   = collection;
    this.masterCipher = masterCipher;
  }

  public DavPropertyNameSet getPropertyNamesForFetch() {
    DavPropertyNameSet hidingCollectionProps = new DavPropertyNameSet();

    hidingCollectionProps.add(PROPERTY_FLOCK_COLLECTION);

    return hidingCollectionProps;
  }

  public boolean isFlockCollection() throws PropertyParseException {
    return collection.getProperty(PROPERTY_FLOCK_COLLECTION, String.class).isPresent();
  }

  public void makeFlockCollection(String hiddenDisplayName, DavPropertyNameSet removeProperties)
      throws DavException, IOException, GeneralSecurityException
  {
    DavPropertySet addProperties = new DavPropertySet();

    addProperties.add(new DefaultDavProperty<String>(PROPERTY_FLOCK_COLLECTION,   "true"));
    addProperties.add(new DefaultDavProperty<String>(DavPropertyName.DISPLAYNAME, HidingUtil.encryptEncodeAndPrefix(masterCipher, hiddenDisplayName)));

    collection.patchProperties(addProperties, removeProperties);
  }

  public Optional<String> getHiddenDisplayName()
      throws PropertyParseException, InvalidMacException, GeneralSecurityException, IOException
  {
    Optional<String> displayName = collection.getDisplayName();

    if (displayName.isPresent())
      return Optional.of(HidingUtil.decodeAndDecryptIfNecessary(masterCipher, displayName.get()));
    else
      return Optional.absent();
  }

  public void setHiddenDisplayName(String displayName)
      throws DavException, IOException, GeneralSecurityException
  {
    collection.setDisplayName(HidingUtil.encryptEncodeAndPrefix(masterCipher, displayName));
  }

}
