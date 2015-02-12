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

package org.anhonesteffort.flock.webdav.caldav;

import org.anhonesteffort.flock.webdav.WebDavConstants;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.webdav.AbstractDavComponentStore;
import org.anhonesteffort.flock.webdav.DavClient;
import org.anhonesteffort.flock.webdav.DavComponentStore;
import org.anhonesteffort.flock.webdav.ExtendedMkCol;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.commons.httpclient.Header;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.client.methods.MkColMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class CalDavStore extends AbstractDavComponentStore<CalDavCollection>
    implements DavComponentStore<CalDavCollection>
{
  protected Optional<String> calendarHomeSet = Optional.absent();

  public CalDavStore(String           hostHREF,
                     String           username,
                     String           password,
                     Optional<String> currentUserPrincipal,
                     Optional<String> calendarHomeSet)
      throws DavException, IOException
  {
    super(hostHREF, username, password, currentUserPrincipal);
    this.calendarHomeSet = calendarHomeSet;
  }

  public CalDavStore(DavClient        client,
                     Optional<String> currentUserPrincipal,
                     Optional<String> calendarHomeSet)
  {
    super(client, currentUserPrincipal);
    this.calendarHomeSet = calendarHomeSet;
  }

  @Override
  protected String getWellKnownUri() {
    return "/.well-known/caldav";
  }

  public Optional<String> getCalendarHomeSet()
      throws PropertyParseException, DavException, IOException
  {
    if (calendarHomeSet.isPresent())
      return calendarHomeSet;

    if (!getCurrentUserPrincipal().isPresent())
      throw new PropertyParseException("DAV:current-user-principal unavailable, server must support rfc5785.",
                                       getHostHREF(), WebDavConstants.PROPERTY_NAME_CURRENT_USER_PRINCIPAL);

    DavPropertyNameSet principalsProps = new DavPropertyNameSet();
    principalsProps.add(CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);
    principalsProps.add(DavPropertyName.DISPLAYNAME);

    String         propFindUri    = getHostHREF().concat(getCurrentUserPrincipal().get());
    PropFindMethod propFindMethod = new PropFindMethod(propFindUri,
                                                       principalsProps,
                                                       PropFindMethod.DEPTH_0);

    try {

      getClient().execute(propFindMethod);

      MultiStatus           multiStatus = propFindMethod.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] msResponses = multiStatus.getResponses();

      for (MultiStatusResponse msResponse : msResponses) {
        DavPropertySet foundProperties = msResponse.getProperties(WebDavConstants.SC_OK);
        DavProperty    homeSetProperty = foundProperties.get(CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

        for (Status status : msResponse.getStatus()) {
          if (status.getStatusCode() == WebDavConstants.SC_OK) {

            if (homeSetProperty != null && homeSetProperty.getValue() instanceof ArrayList) {
              for (Object child : (ArrayList<?>) homeSetProperty.getValue()) {
                if (child instanceof Element) {
                  String calendarHomeSetUri = ((Element) child).getTextContent();
                  if (!(calendarHomeSetUri.endsWith("/")))
                    calendarHomeSetUri = calendarHomeSetUri.concat("/");

                  calendarHomeSet = Optional.of(calendarHomeSetUri);
                  return calendarHomeSet;
                }
              }
            }

            // OwnCloud :(
            else if (homeSetProperty != null && homeSetProperty.getValue() instanceof Element) {
              String calendarHomeSetUri = ((Element) homeSetProperty.getValue()).getTextContent();
              if (!(calendarHomeSetUri.endsWith("/")))
                calendarHomeSetUri = calendarHomeSetUri.concat("/");

              calendarHomeSet = Optional.of(calendarHomeSetUri);
              return calendarHomeSet;
            }
          }
        }
      }

    } finally {
      propFindMethod.releaseConnection();
    }

    return Optional.absent();
  }

  @Override
  public void addCollection(String path)
      throws DavException, IOException
  {
    addCollection(path, new DavPropertySet());
  }

  public void addCollection(String path, DavPropertySet properties)
      throws DavException, IOException
  {
    ArrayList<DavPropertyName> resourceTypes = new ArrayList<DavPropertyName>();
    resourceTypes.add(DavPropertyName.create(DavConstants.XML_COLLECTION, DavConstants.NAMESPACE));
    resourceTypes.add(DavPropertyName.create("calendar",                  CalDavConstants.CALDAV_NAMESPACE));                         // TODO: constant for this...
    properties.add(new DefaultDavProperty<ArrayList<DavPropertyName>>(DavPropertyName.RESOURCETYPE, resourceTypes));

    MkColMethod   mkColMethod   = new MkColMethod(getHostHREF().concat(path));
    ExtendedMkCol extendedMkCol = new ExtendedMkCol(properties);

    try {

      mkColMethod.setRequestBody(extendedMkCol);
      getClient().execute(mkColMethod);

      if (!mkColMethod.succeeded())
        throw new DavException(mkColMethod.getStatusCode(), mkColMethod.getStatusText());

    } finally {
      mkColMethod.releaseConnection();
    }
  }

  public void addCollection(String path,
                            String displayName,
                            String description,
                            int    color)
      throws DavException, IOException
  {
    DavPropertySet properties = new DavPropertySet();
    properties.add(new DefaultDavProperty<String>( DavPropertyName.DISPLAYNAME, displayName));
    properties.add(new DefaultDavProperty<String>( CalDavConstants.PROPERTY_NAME_CALENDAR_DESCRIPTION, description));
    properties.add(new DefaultDavProperty<Integer>(CalDavConstants.PROPERTY_NAME_CALENDAR_COLOR, color));
    addCollection(path, properties);
  }

  // TODO: I don't like this...
  public static List<CalDavCollection> getCollectionsFromMultiStatusResponses(CalDavStore           store,
                                                                              MultiStatusResponse[] msResponses)
  {
    List<CalDavCollection> collections = new LinkedList<CalDavCollection>();

    for (MultiStatusResponse msResponse : msResponses) {
      DavPropertySet foundProperties = msResponse.getProperties(WebDavConstants.SC_OK);
      String         collectionUri   = msResponse.getHref();

      for (Status status : msResponse.getStatus()) {
        if (status.getStatusCode() == WebDavConstants.SC_OK) {

          boolean        isCalendarCollection = false;
          DavPropertySet collectionProperties = new DavPropertySet();

          DavProperty resourceTypeProperty = foundProperties.get(DavPropertyName.RESOURCETYPE);
          if (resourceTypeProperty != null) {

            Object resourceTypeValue = resourceTypeProperty.getValue();
            if (resourceTypeValue instanceof ArrayList) {
              for (Node child : (ArrayList<Node>) resourceTypeValue) {
                if (child instanceof Element) {
                  String localName = child.getLocalName();
                  if (localName != null) {
                    isCalendarCollection = isCalendarCollection                                                ||
                                           localName.equals(CalDavConstants.RESOURCE_TYPE_CALENDAR)            ||
                                           localName.equals(CalDavConstants.RESOURCE_TYPE_CALENDAR_PROXY_READ) ||
                                           localName.equals(CalDavConstants.RESOURCE_TYPE_CALENDAR_PROXY_WRITE);
                  }
                }
              }
            }
          }

          if (isCalendarCollection) {
            for (DavProperty property : foundProperties) {
              if (property != null)
                collectionProperties.add(property);
            }
            collections.add(new CalDavCollection(store, collectionUri, collectionProperties));
          }

        }
      }
    }

    return collections;
  }

  @Override
  public Optional<CalDavCollection> getCollection(String path) throws DavException, IOException {
    CalDavCollection   targetCollection = new CalDavCollection(this, path);
    DavPropertyNameSet collectionProps  = targetCollection.getPropertyNamesForFetch();
    PropFindMethod     propFindMethod   = new PropFindMethod(path, collectionProps, PropFindMethod.DEPTH_0);

    try {

      getClient().execute(propFindMethod);

      MultiStatus            multiStatus         = propFindMethod.getResponseBodyAsMultiStatus();
      MultiStatusResponse[]  responses           = multiStatus.getResponses();
      List<CalDavCollection> returnedCollections = getCollectionsFromMultiStatusResponses(this, responses);

      if (returnedCollections.size() == 0)
        return Optional.absent();

      return Optional.of(returnedCollections.get(0));

    } catch (DavException e) {

      if (e.getErrorCode() == WebDavConstants.SC_NOT_FOUND)
        return Optional.absent();

      throw e;
    } finally {
      propFindMethod.releaseConnection();
    }
  }

  @Override
  public List<CalDavCollection> getCollections()
      throws PropertyParseException, DavException, IOException
  {
    Optional<String> calHomeSetUri = getCalendarHomeSet();
    if (!calHomeSetUri.isPresent())
      throw new PropertyParseException("No calendar-home-set property found for user.",
                                       getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

    CalDavCollection   hack          = new CalDavCollection(this, "");
    DavPropertyNameSet calendarProps = hack.getPropertyNamesForFetch();

    PropFindMethod method = new PropFindMethod(getHostHREF().concat(calHomeSetUri.get()),
                                               calendarProps,
                                               PropFindMethod.DEPTH_1);

    try {

      getClient().execute(method);

      MultiStatus           multiStatus = method.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] responses   = multiStatus.getResponses();

      return getCollectionsFromMultiStatusResponses(this, responses);

    } finally {
      method.releaseConnection();
    }
  }
}
