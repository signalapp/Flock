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

package org.anhonesteffort.flock.sync.calendar;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.sync.HidingDavCollectionMixin;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.HidingDavStore;
import org.anhonesteffort.flock.crypto.HidingUtil;
import org.anhonesteffort.flock.webdav.DavClient;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
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
public class HidingCalDavStore implements HidingDavStore<HidingCalDavCollection> {

  private MasterCipher masterCipher;
  private CalDavStore  calDavStore;

  public HidingCalDavStore(MasterCipher     masterCipher,
                           String           hostHREF,
                           String           username,
                           String           password,
                           Optional<String> currentUserPrincipal,
                           Optional<String> calendarHomeSet)
      throws DavException, IOException
  {
    this.masterCipher = masterCipher;
    this.calDavStore  = new CalDavStore(hostHREF, username, password, currentUserPrincipal, calendarHomeSet);
  }

  public HidingCalDavStore(MasterCipher     masterCipher,
                           DavClient        client,
                           Optional<String> currentUserPrincipal,
                           Optional<String> calendarHomeSet) {
    this.masterCipher = masterCipher;
    this.calDavStore  = new CalDavStore(client, currentUserPrincipal, calendarHomeSet);
  }

  @Override
  public String getHostHREF() {
    return calDavStore.getHostHREF();
  }

  public Optional<String> getCalendarHomeSet()
      throws PropertyParseException, DavException, IOException
  {
    return calDavStore.getCalendarHomeSet();
  }

  @Override
  public Optional<HidingCalDavCollection> getCollection(String path) throws DavException, IOException {
    HidingCalDavCollection targetCollection = new HidingCalDavCollection(calDavStore, path, masterCipher);
    DavPropertyNameSet     collectionProps  = targetCollection.getPropertyNamesForFetch();
    PropFindMethod         propFindMethod   = new PropFindMethod(path, collectionProps, PropFindMethod.DEPTH_0);

    try {

      calDavStore.getClient().execute(propFindMethod);

      MultiStatus           multiStatus = propFindMethod.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] responses   = multiStatus.getResponses();

      List<CalDavCollection> returnedCollections = CalDavStore.getCollectionsFromMultiStatusResponses(calDavStore, responses);

      if (returnedCollections.size() == 0)
        Optional.absent();

      return Optional.of(new HidingCalDavCollection(returnedCollections.get(0), masterCipher));

    } catch (DavException e) {

      if (e.getErrorCode() == DavServletResponse.SC_NOT_FOUND)
        return Optional.absent();

      throw e;

    } finally {
      propFindMethod.releaseConnection();
    }
  }

  @Override
  public List<HidingCalDavCollection> getCollections()
      throws PropertyParseException, DavException, IOException
  {
    Optional<String> calHomeSetUri = getCalendarHomeSet();
    if (!calHomeSetUri.isPresent())
      throw new PropertyParseException("No calendar-home-set property found for user.",
                                       getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

    HidingCalDavCollection hack          = new HidingCalDavCollection(calDavStore, "hack", masterCipher);
    DavPropertyNameSet     calendarProps = hack.getPropertyNamesForFetch();

    PropFindMethod method = new PropFindMethod(getHostHREF().concat(calHomeSetUri.get()),
                                               calendarProps,
                                               PropFindMethod.DEPTH_1);

    try {

      calDavStore.getClient().execute(method);

      MultiStatus           multiStatus = method.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] responses   = multiStatus.getResponses();

      List<HidingCalDavCollection> hidingCollections = new LinkedList<HidingCalDavCollection>();
      List<CalDavCollection>       collections       = CalDavStore.getCollectionsFromMultiStatusResponses(calDavStore, responses);

      for (CalDavCollection collection : collections)
        hidingCollections.add(new HidingCalDavCollection(collection, masterCipher));

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
    calDavStore.addCollection(path, properties);
  }

  public void addCollection(String  path,
                            String  displayName,
                            Integer color)
      throws DavException, IOException, GeneralSecurityException
  {
    DavPropertySet properties        = new DavPropertySet();
    String         hiddenDisplayName = HidingUtil.encryptEncodeAndPrefix(masterCipher, displayName);
    String         hiddenColor       = HidingUtil.encryptEncodeAndPrefix(masterCipher, color.toString());

    properties.add(new DefaultDavProperty<Object>(HidingDavCollectionMixin.PROPERTY_FLOCK_COLLECTION, "true"));
    properties.add(new DefaultDavProperty<String>(DavPropertyName.DISPLAYNAME,                  hiddenDisplayName));
    properties.add(new DefaultDavProperty<String>(HidingCalDavCollection.PROPERTY_HIDDEN_COLOR, hiddenColor));

    calDavStore.addCollection(path, properties);
  }

  @Override
  public void removeCollection(String path) throws DavException, IOException {
    calDavStore.removeCollection(path);
  }

  public void releaseConnections() {
    calDavStore.closeHttpConnection();
  }


}
