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

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.sync.HidingDavCollectionMixin;
import org.anhonesteffort.flock.webdav.WebDavConstants;
import org.anhonesteffort.flock.webdav.carddav.CardDavConstants;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.HidingDavStore;
import org.anhonesteffort.flock.crypto.HidingUtil;
import org.anhonesteffort.flock.webdav.DavClient;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.carddav.CardDavCollection;
import org.anhonesteffort.flock.webdav.carddav.CardDavStore;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class HidingCardDavStore implements HidingDavStore<HidingCardDavCollection> {

  private MasterCipher masterCipher;
  private CardDavStore cardDavStore;

  public HidingCardDavStore(MasterCipher     masterCipher,
                            String           hostHREF,
                            String           username,
                            String           password,
                            Optional<String> currentUserPrincipal,
                            Optional<String> addressBookHomeSet)
      throws DavException, IOException
  {
    this.masterCipher = masterCipher;
    this.cardDavStore = new CardDavStore(hostHREF, username, password, currentUserPrincipal, addressBookHomeSet);
  }

  public HidingCardDavStore(MasterCipher     masterCipher,
                            DavClient        client,
                            Optional<String> currentUserPrincipal,
                            Optional<String> addressBookHomeSet)
  {
    this.masterCipher = masterCipher;
    this.cardDavStore = new CardDavStore(client, currentUserPrincipal, addressBookHomeSet);
  }

  @Override
  public String getHostHREF() {
    return cardDavStore.getHostHREF();
  }

  public Optional<String> getAddressbookHomeSet()
      throws PropertyParseException, DavException, IOException
  {
    return cardDavStore.getAddressbookHomeSet();
  }

  @Override
  public Optional<HidingCardDavCollection> getCollection(String path) throws DavException, IOException {
    HidingCardDavCollection targetCollection = new HidingCardDavCollection(cardDavStore, path, masterCipher);
    DavPropertyNameSet      collectionProps  = targetCollection.getPropertyNamesForFetch();
    PropFindMethod          propFindMethod   = new PropFindMethod(path, collectionProps, PropFindMethod.DEPTH_0);

    try {

      cardDavStore.getClient().execute(propFindMethod);

      MultiStatus             multiStatus         = propFindMethod.getResponseBodyAsMultiStatus();
      MultiStatusResponse[]   responses           = multiStatus.getResponses();
      List<CardDavCollection> returnedCollections = CardDavStore.getCollectionsFromMultiStatusResponses(cardDavStore, responses);

      if (returnedCollections.size() == 0)
        Optional.absent();

      return Optional.of(new HidingCardDavCollection(returnedCollections.get(0), masterCipher));

    } catch (DavException e) {

      if (e.getErrorCode() == WebDavConstants.SC_NOT_FOUND)
        return Optional.absent();

      throw e;

    } finally {
      propFindMethod.releaseConnection();
    }
  }

  @Override
  public List<HidingCardDavCollection> getCollections()
      throws PropertyParseException, DavException, IOException
  {
    Optional<String> addressbookHomeSetUri = getAddressbookHomeSet();
    if (!addressbookHomeSetUri.isPresent())
      throw new PropertyParseException("No addressbook-home-set property found for user.",
                                       getHostHREF(), CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_HOME_SET);

    HidingCardDavCollection hack             = new HidingCardDavCollection(cardDavStore, "hack", masterCipher);
    DavPropertyNameSet      addressbookProps = hack.getPropertyNamesForFetch();

    PropFindMethod method = new PropFindMethod(getHostHREF().concat(addressbookHomeSetUri.get()),
                                               addressbookProps,
                                               PropFindMethod.DEPTH_1);

    try {

      cardDavStore.getClient().execute(method);

      MultiStatus           multiStatus = method.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] responses   = multiStatus.getResponses();

      List<HidingCardDavCollection> hidingCollections = new LinkedList<HidingCardDavCollection>();
      List<CardDavCollection>       collections       = CardDavStore.getCollectionsFromMultiStatusResponses(cardDavStore, responses);

      for (CardDavCollection collection : collections)
        hidingCollections.add(new HidingCardDavCollection(collection, masterCipher));

      return hidingCollections;

    } finally {
      method.releaseConnection();
    }
  }

  @Override
  public void addCollection(String path)
      throws DavException, IOException, GeneralSecurityException
  {
    DavPropertySet properties = new DavPropertySet();

    properties.add(new DefaultDavProperty<Object>(HidingDavCollectionMixin.PROPERTY_FLOCK_COLLECTION, "true"));
    cardDavStore.addCollection(path, properties);
  }

  public void addCollection(String path,
                            String displayName)
      throws DavException, IOException, GeneralSecurityException
  {
    DavPropertySet properties        = new DavPropertySet();
    String         hiddenDisplayName = HidingUtil.encryptEncodeAndPrefix(masterCipher, displayName);

    properties.add(new DefaultDavProperty<Object>(HidingDavCollectionMixin.PROPERTY_FLOCK_COLLECTION, "true"));
    properties.add(new DefaultDavProperty<String>(DavPropertyName.DISPLAYNAME, hiddenDisplayName));

    cardDavStore.addCollection(path, properties);
  }

  @Override
  public void removeCollection(String path) throws DavException, IOException {
    cardDavStore.removeCollection(path);
  }

  @Override
  public void releaseConnections() {
    cardDavStore.closeHttpConnection();
  }

}
