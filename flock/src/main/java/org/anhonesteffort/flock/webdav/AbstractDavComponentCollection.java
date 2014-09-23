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

package org.anhonesteffort.flock.webdav;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.client.methods.PropPatchMethod;
import org.apache.jackrabbit.webdav.client.methods.ReportMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Programmer: rhodey
 */
public abstract class AbstractDavComponentCollection<T> implements DavComponentCollection<T> {

  private final AbstractDavComponentStore<?> store;
  protected     DavClient                    client;
  private       String                       path;
  protected     DavPropertySet               properties;

  protected AbstractDavComponentCollection(AbstractDavComponentStore<?> store,
                                           String                       path) {
    this.store   = store;
    this.client  = store.davClient;
    this.path    = path;
    properties   = new DavPropertySet();
  }

  protected AbstractDavComponentCollection(AbstractDavComponentStore<?> store,
                                           String                       path,
                                           DavPropertySet               properties)
  {
    this.store      = store;
    this.client     = store.davClient;
    this.path       = path;
    this.properties = properties;
  }

  protected AbstractDavComponentCollection(AbstractDavComponentStore<?> store,
                                           String                       path,
                                           String                       displayName)
  {
    this.store  = store;
    this.client = store.davClient;
    this.path   = path;

    properties = new DavPropertySet();
    properties.add(new DefaultDavProperty<String>(DavPropertyName.DISPLAYNAME, displayName));
  }

  public void setClient(DavClient client) {
    this.client = client;
  }
  
  public AbstractDavComponentStore<?> getStore() {
    return store;
  }

  @Override
  public String getPath() {
    if (!path.endsWith("/"))
      path = path.concat("/");

    return path;
  }

  protected abstract String getComponentPathFromUid(String uid);

  protected Optional<String> getUidFromComponentPath(String path) {
    int extension_index = path.lastIndexOf(".");
    int file_name_index = path.lastIndexOf("/");

    if (file_name_index == -1 || extension_index ==  -1 ||
        file_name_index == (path.length() - 1))
      return Optional.absent();

    return Optional.of(path.substring(file_name_index + 1, extension_index));
  }

  protected DavPropertyNameSet getPropertyNamesForFetch() {
    DavPropertyNameSet baseProperties = new DavPropertyNameSet();
    baseProperties.add(DavPropertyName.RESOURCETYPE);
    baseProperties.add(DavPropertyName.DISPLAYNAME);
    baseProperties.add(SecurityConstants.OWNER);

    baseProperties.add(WebDavConstants.PROPERTY_NAME_PROP);
    baseProperties.add(SecurityConstants.CURRENT_USER_PRIVILEGE_SET);
    baseProperties.add(WebDavConstants.PROPERTY_NAME_QUOTA_AVAILABLE_BYTES);
    baseProperties.add(WebDavConstants.PROPERTY_NAME_QUOTA_USED_BYTES);
    baseProperties.add(WebDavConstants.PROPERTY_NAME_RESOURCE_ID);
    baseProperties.add(WebDavConstants.PROPERTY_NAME_SUPPORTED_REPORT_SET); // TODO getter method for this.
    baseProperties.add(WebDavConstants.PROPERTY_NAME_SYNC_TOKEN);

    baseProperties.add(CalDavConstants.PROPERTY_NAME_CTAG);

    return baseProperties;
  }

  protected abstract ReportType getMultiGetReportType();

  protected abstract ReportType getQueryReportType();

  protected abstract DavPropertyNameSet getPropertyNamesForReports();

  public DavPropertySet getProperties() {
    return properties;
  }

  // TODO: make this cleaner?
  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <P> Optional<P> getProperty(DavPropertyName propertyName, Class<P> type)
      throws PropertyParseException
  {
    if (properties.get(propertyName) != null) {
      try {

        Object value = properties.get(propertyName).getValue();

        if (Collection.class.isAssignableFrom(type)) {
          P result = type.newInstance();
          if (value instanceof Collection<?>)
            ((Collection<?>) result).addAll((Collection) value);
          return Optional.of(result);
        }
        else {
          Constructor<P> constructor = type.getConstructor(value.getClass());
          return Optional.of(constructor.newInstance(value));
        }

      } catch (Exception e) {
        throw new PropertyParseException("caught exception while getting property " +
                                         propertyName.getName(), path, propertyName, e);
      }
    }

    return Optional.absent();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> getResourceTypes() throws PropertyParseException {
    List<String>  resourceTypes    = new LinkedList<String>();
    Optional<ArrayList> resourceTypeProp = getProperty(DavPropertyName.RESOURCETYPE, ArrayList.class);

    if (!resourceTypeProp.isPresent())
      return resourceTypes;

    for (Node child : (ArrayList<Node>) resourceTypeProp.get()) {
      if (child instanceof Element) {
        String nameNode = child.getNodeName();
        if (nameNode != null)
          resourceTypes.add(nameNode);
      }
    }

    return resourceTypes;
  }

  @Override
  public Optional<String> getDisplayName() throws PropertyParseException {
    return getProperty(DavPropertyName.DISPLAYNAME, String.class);
  }

  @Override
  public Optional<String> getCTag() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_CTAG, String.class);
  }

  @Override
  public void setDisplayName(String displayName) throws DavException, IOException {
    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(DavPropertyName.DISPLAYNAME, displayName));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  @Override
  public Optional<String> getOwnerHref() throws PropertyParseException {
    Optional<ArrayList> ownerProp = getProperty(SecurityConstants.OWNER, ArrayList.class);

    if (ownerProp.isPresent()) {
      for (Node child : (ArrayList<Node>) ownerProp.get()) {
        if (child instanceof Element) {
          String nameNode = child.getNodeName();
          if ((nameNode != null) && (DavConstants.XML_HREF.equals(nameNode)))
            return Optional.of(child.getTextContent());
        }
      }
    }

    return Optional.absent();
  }

  @Override
  public Optional<Long> getQuotaAvailableBytes() throws PropertyParseException {
    return getProperty(WebDavConstants.PROPERTY_NAME_QUOTA_AVAILABLE_BYTES, Long.class);
  }

  @Override
  public Optional<Long> getQuotaUsedBytes() throws PropertyParseException {
    return getProperty(WebDavConstants.PROPERTY_NAME_QUOTA_USED_BYTES, Long.class);
  }

  @Override
  public Optional<String> getResourceId() throws PropertyParseException {
    Optional<ArrayList> resourceIdProp = getProperty(WebDavConstants.PROPERTY_NAME_RESOURCE_ID, ArrayList.class);

    if (resourceIdProp.isPresent()) {
      for (Node child : (ArrayList<Node>) resourceIdProp.get()) {
        if (child instanceof Element) {
          String nameNode = child.getNodeName();
          if ((nameNode != null) && (DavConstants.XML_HREF.equals(nameNode)))
            return Optional.of(child.getTextContent());
        }
      }
    }

    return Optional.absent();
  }

  // TODO: make use of this in REPORT methods if present.
  @Override
  public Optional<String> getSyncToken() throws PropertyParseException {
    return getProperty(WebDavConstants.PROPERTY_NAME_SYNC_TOKEN, String.class);
  }

  public void fetchProperties(DavPropertyNameSet fetchProps) throws DavException, IOException {
    PropFindMethod propFindMethod = new PropFindMethod(getPath(), fetchProps, PropFindMethod.DEPTH_0);

    try {

      client.execute(propFindMethod);

      if (propFindMethod.getStatusCode() == DavServletResponse.SC_MULTI_STATUS) {
        MultiStatus           multiStatus = propFindMethod.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses   = multiStatus.getResponses();

        DavPropertySet foundProperties = responses[0].getProperties(DavServletResponse.SC_OK);
        if (foundProperties != null)
          properties = foundProperties;
      } else
        throw new DavException(propFindMethod.getStatusCode(), propFindMethod.getStatusText());

    } finally {
      propFindMethod.releaseConnection();
    }
  }

  public void fetchProperties() throws DavException, IOException {
    DavPropertyNameSet fetchProps     = getPropertyNamesForFetch();
    PropFindMethod     propFindMethod = new PropFindMethod(getPath(), fetchProps, PropFindMethod.DEPTH_0);

    try {

      client.execute(propFindMethod);

      if (propFindMethod.getStatusCode() == DavServletResponse.SC_MULTI_STATUS) {
        MultiStatus           multiStatus = propFindMethod.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses   = multiStatus.getResponses();

        DavPropertySet foundProperties = responses[0].getProperties(DavServletResponse.SC_OK);
        if (foundProperties != null)
          properties = foundProperties;
      }
      else
        throw new DavException(propFindMethod.getStatusCode(), propFindMethod.getStatusText());

    } finally {
      propFindMethod.releaseConnection();
    }
  }

  @Override
  public void patchProperties(DavPropertySet setProperties, DavPropertyNameSet removeProperties)
      throws DavException, IOException
  {
    PropPatchMethod       propPatchMethod = new PropPatchMethod(getPath(), setProperties, removeProperties);
    ByteArrayOutputStream stream          = new ByteArrayOutputStream();

    try {

      propPatchMethod.getRequestEntity().writeRequest(stream);
      client.execute(propPatchMethod);

      if (propPatchMethod.getStatusCode() != DavServletResponse.SC_MULTI_STATUS)
        throw new DavException(propPatchMethod.getStatusCode(), propPatchMethod.getStatusText());

      fetchProperties();

    } finally {
      propPatchMethod.releaseConnection();
    }
  }

  @Override
  public HashMap<String, String> getComponentETags() throws DavException, IOException {
    HashMap<String, String> componentETagPairs  = new HashMap<String, String>();

    DavPropertyNameSet fetchProps = new DavPropertyNameSet();
    fetchProps.add(DavPropertyName.GETETAG);

    PropFindMethod propFindMethod = new PropFindMethod(getPath(), fetchProps, PropFindMethod.DEPTH_1);

    try {

      client.execute(propFindMethod);

      if (propFindMethod.getStatusCode() == DavServletResponse.SC_MULTI_STATUS) {
        MultiStatus           multiStatus = propFindMethod.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses   = multiStatus.getResponses();

        for (MultiStatusResponse msResponse : responses) {
          DavPropertySet   foundProperties = msResponse.getProperties(DavServletResponse.SC_OK);
          Optional<String> componentUid    = getUidFromComponentPath(msResponse.getHref());

          if (componentUid.isPresent() && foundProperties.get(DavPropertyName.PROPERTY_GETETAG) != null) {
            DavProperty getETagProp = foundProperties.get(DavPropertyName.PROPERTY_GETETAG);
            componentETagPairs.put(componentUid.get(), (String) getETagProp.getValue());
          }
        }
      }
      else
        throw new DavException(propFindMethod.getStatusCode(),
                               "PROPFIND response not multi-status, response: " + propFindMethod.getStatusLine());

    } finally {
      propFindMethod.releaseConnection();
    }

    return componentETagPairs;
  }

  protected abstract List<ComponentETagPair<T>> getComponentsFromMultiStatus(MultiStatusResponse[] msResponses)
      throws InvalidComponentException;

  @Override
  public Optional<ComponentETagPair<T>> getComponent(String uid)
      throws InvalidComponentException, DavException, IOException
  {
    ReportInfo reportInfo = new ReportInfo(getMultiGetReportType(), 1, getPropertyNamesForReports());

    try {

      Document document      = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      Element  componentHREF = DomUtil.createElement(document, DavConstants.XML_HREF, DavConstants.NAMESPACE);
      componentHREF.setTextContent(getComponentPathFromUid(uid));
      reportInfo.setContentElement(componentHREF);

      ReportMethod reportMethod = new ReportMethod(getPath(), reportInfo);

      try {

        client.execute(reportMethod);
        if (reportMethod.getStatusCode() == DavServletResponse.SC_MULTI_STATUS) {
          try {

            List<ComponentETagPair<T>> components =
                getComponentsFromMultiStatus(reportMethod.getResponseBodyAsMultiStatus().getResponses());

            if (components.size() == 0)
              return Optional.absent();
            return Optional.of(components.get(0));

          } catch (InvalidComponentException e) {
            if (e.getCause() == null)
              throw new InvalidComponentException(e.getMessage(), e.isServersFault(), e.getNamespace(), e.getPath(), uid);

            throw new InvalidComponentException(e.getMessage(), e.isServersFault(), e.getNamespace(), e.getPath(), uid, e.getCause());
          }
        } else if (reportMethod.getStatusCode() == DavServletResponse.SC_NOT_FOUND)
          return Optional.absent();
        else
          throw new DavException(reportMethod.getStatusCode(), reportMethod.getStatusText());

      } finally {
        reportMethod.releaseConnection();
      }

    } catch (ParserConfigurationException e) {
      throw new IOException("Caught exception while building document.", e);
    }
  }

  @Override
  public List<ComponentETagPair<T>> getComponents()
      throws InvalidComponentException, DavException, IOException
  {
    ReportInfo   reportInfo   = new ReportInfo(getQueryReportType(), 1, getPropertyNamesForReports());
    ReportMethod reportMethod = new ReportMethod(getPath(), reportInfo);

    try {

      client.execute(reportMethod);

      if (reportMethod.getStatusCode() == DavServletResponse.SC_MULTI_STATUS)
        return getComponentsFromMultiStatus(reportMethod.getResponseBodyAsMultiStatus().getResponses());

      throw new DavException(reportMethod.getStatusCode(), reportMethod.getStatusText());

    } finally {
      reportMethod.releaseConnection();
    }
  }

  protected abstract void putComponentToServer(T calendar, Optional<String> ifMatchETag)
      throws DavException, IOException, InvalidComponentException;

  @Override
  public void addComponent(T component)
      throws InvalidComponentException, DavException, IOException
  {
    putComponentToServer(component, Optional.<String>absent());
    fetchProperties();
  }

  @Override
  public void updateComponent(ComponentETagPair<T> component)
      throws InvalidComponentException, DavException, IOException
  {
    putComponentToServer(component.getComponent(), component.getETag());
    fetchProperties();
  }

  @Override
  public void removeComponent(String path) throws DavException, IOException {
    DeleteMethod deleteMethod = new DeleteMethod(getComponentPathFromUid(path));

    try {

      client.execute(deleteMethod);
      if (!deleteMethod.succeeded() && deleteMethod.getStatusCode() != DavServletResponse.SC_OK)
        throw new DavException(deleteMethod.getStatusCode(), deleteMethod.getStatusText());

    } finally {
      deleteMethod.releaseConnection();
    }
  }
}
