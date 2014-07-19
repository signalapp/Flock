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

  protected static final String          PROPERTY_NAME_KEY_MATERIAL_SALT      = "X-KEY-MATERIAL-SALT";
  protected static final String          PROPERTY_NAME_ENCRYPTED_KEY_MATERIAL = "X-ENCRYPTED-KEY-MATERIAL";

  protected static final DavPropertyName PROPERTY_KEY_MATERIAL_SALT      = DavPropertyName.create(
      PROPERTY_NAME_KEY_MATERIAL_SALT,
      OwsWebDav.NAMESPACE
  );
  protected static final DavPropertyName PROPERTY_ENCRYPTED_KEY_MATERIAL = DavPropertyName.create(
      PROPERTY_NAME_ENCRYPTED_KEY_MATERIAL,
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
    hidingCollectionProps.add(PROPERTY_KEY_MATERIAL_SALT);
    hidingCollectionProps.add(PROPERTY_ENCRYPTED_KEY_MATERIAL);

    return hidingCollectionProps;
  }

  public Optional<String> getKeyMaterialSalt() throws PropertyParseException {
    return collection.getProperty(PROPERTY_KEY_MATERIAL_SALT, String.class);
  }

  public void setKeyMaterialSalt(String keyMaterialSalt)
      throws DavException, IOException
  {
    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(PROPERTY_KEY_MATERIAL_SALT,
                                                        keyMaterialSalt));

    collection.patchProperties(updateProperties, new DavPropertyNameSet());
  }

  public Optional<String> getEncryptedKeyMaterial() throws PropertyParseException {
    return collection.getProperty(PROPERTY_ENCRYPTED_KEY_MATERIAL, String.class);
  }

  public void setEncryptedKeyMaterial(String encryptedKeyMaterial)
      throws DavException, IOException
  {
    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(PROPERTY_ENCRYPTED_KEY_MATERIAL,
                                                        encryptedKeyMaterial));

    collection.patchProperties(updateProperties, new DavPropertyNameSet());
  }

  public Optional<String> getHiddenDisplayName()
      throws PropertyParseException, InvalidMacException,
      GeneralSecurityException, IOException
  {
    Optional<String> displayName = collection.getDisplayName();

    if (displayName.isPresent())
      return Optional.of(HidingUtil.decodeAndDecryptIfNecessary(masterCipher, displayName.get()));

    return Optional.absent();
  }

  public void setHiddenDisplayName(String displayName)
      throws DavException, IOException,
      InvalidMacException, GeneralSecurityException
  {
    collection.setDisplayName(HidingUtil.encryptEncodeAndPrefix(masterCipher, displayName));
  }

}
