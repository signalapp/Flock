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

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ConstraintViolationException;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.util.Calendars;
import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.webdav.AbstractDavComponentCollection;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.MultiStatusResult;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.WebDavConstants;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.PutMethod;
import org.apache.jackrabbit.webdav.client.methods.ReportMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.security.report.PrincipalMatchReport;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Programmer: rhodey
 */
public class CalDavCollection extends AbstractDavComponentCollection<Calendar> implements DavCalendarCollection {

  public CalDavCollection(CalDavStore calDavStore, String path) {
    super(calDavStore, path);
  }

  public CalDavCollection(CalDavStore calDavStore,
                          String      path,
                          String      displayName,
                          String      description,
                          Integer     color)
  {
    super(calDavStore, path, displayName);
    properties.add(new DefaultDavProperty<String>(CalDavConstants.PROPERTY_NAME_CALENDAR_DESCRIPTION, description));
    properties.add(new DefaultDavProperty<Integer>(CalDavConstants.PROPERTY_NAME_CALENDAR_COLOR,        color));
  }

  public CalDavCollection(CalDavStore calDavStore, String path, DavPropertySet properties) {
    super(calDavStore, path, properties);
    this.properties = properties;
  }

  @Override
  protected String getComponentPathFromUid(String uid) {
    return getPath().concat(uid.concat(CalDavConstants.ICAL_FILE_EXTENSION));
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForFetch() {
    DavPropertyNameSet calendarProps = super.getPropertyNamesForFetch();

    calendarProps.add(CalDavConstants.PROPERTY_NAME_CALENDAR_DESCRIPTION);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_SUPPORTED_CALENDAR_COMPONENT_SET);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_CALENDAR_TIMEZONE);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_SUPPORTED_CALENDAR_DATA);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_MAX_ATTENDEES_PER_INSTANCE);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_MAX_DATE_TIME);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_MIN_DATE_TIME);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_MAX_INSTANCES);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_MAX_RESOURCE_SIZE);

    calendarProps.add(CalDavConstants.PROPERTY_NAME_CALENDAR_COLOR);
    calendarProps.add(CalDavConstants.PROPERTY_NAME_CALENDAR_ORDER);

    return calendarProps;
  }

  @Override
  protected ReportType getQueryReportType() {
    return ReportType.register(CalDavConstants.PROPERTY_CALENDAR_QUERY,
        CalDavConstants.CALDAV_NAMESPACE,
        PrincipalMatchReport.class);
  }

  @Override
  protected ReportType getMultiGetReportType() {
    return ReportType.register(CalDavConstants.PROPERTY_CALENDAR_MULTIGET,
        CalDavConstants.CALDAV_NAMESPACE,
        PrincipalMatchReport.class);
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForReports() {
    DavPropertyNameSet calendarProperties = new DavPropertyNameSet();
    calendarProperties.add(CalDavConstants.PROPERTY_NAME_CALENDAR_DATA);
    calendarProperties.add(DavPropertyName.GETETAG);
    return calendarProperties;
  }

  @Override
  public Optional<String> getDescription() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_CALENDAR_DESCRIPTION, String.class);
  }

  @Override
  public void setDescription(String description) throws DavException, IOException {
    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(CalDavConstants.PROPERTY_NAME_CALENDAR_DESCRIPTION, description));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  @Override
  public Optional<Calendar> getTimeZone() throws PropertyParseException {
    try {

      Optional<String> calendarTimeZone = getProperty(CalDavConstants.PROPERTY_NAME_CALENDAR_TIMEZONE, String.class);
      if (calendarTimeZone.isPresent())
        return Optional.of(new CalendarBuilder().build(new StringReader(calendarTimeZone.get())));

    } catch (IOException e) {
      throw new PropertyParseException("caught exception while building time zone.",
                                       getPath(), CalDavConstants.PROPERTY_NAME_CALENDAR_TIMEZONE, e);
    } catch (ParserException e) {
      throw new PropertyParseException("caught exception while building time zone.",
                                       getPath(), CalDavConstants.PROPERTY_NAME_CALENDAR_TIMEZONE, e);
    }
    return Optional.absent();
  }

  @Override
  public void setTimeZone(Calendar timezone) throws DavException, IOException {
    DavPropertySet updateProperties = new DavPropertySet();
    timezone.getProperties().add(new ProdId(((CalDavStore)getStore()).getProductId()));
    updateProperties.add(new DefaultDavProperty<String>(CalDavConstants.PROPERTY_NAME_CALENDAR_TIMEZONE, timezone.toString()));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  @Override
  public List<String> getSupportedComponentSet() throws PropertyParseException {
    List<String> supportedComponents            = new ArrayList<String>();
    Optional<ArrayList> supportedCalCompSetProp =
        getProperty(CalDavConstants.PROPERTY_NAME_SUPPORTED_CALENDAR_COMPONENT_SET, ArrayList.class);

    if (supportedCalCompSetProp.isPresent()) {
      for (Node child : (ArrayList<Node>) supportedCalCompSetProp.get()) {
        if (child instanceof Element) {
          Node nameNode = child.getAttributes().getNamedItem(CalDavConstants.ATTRIBUTE_NAME);
          if (nameNode != null)
            supportedComponents.add(nameNode.getTextContent());
        }
      }
    }

    return supportedComponents;
  }

  @Override
  public Optional<Long> getMaxResourceSize() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_MAX_RESOURCE_SIZE, Long.class);
  }

  @Override
  public Optional<String> getMinDateTime() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_MIN_DATE_TIME, String.class);
  }

  @Override
  public Optional<String> getMaxDateTime() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_MAX_DATE_TIME, String.class);
  }

  @Override
  public Optional<Integer> getMaxInstances() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_MAX_INSTANCES, Integer.class);
  }

  @Override
  public Optional<Integer> getMaxAttendeesPerInstance() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_MAX_ATTENDEES_PER_INSTANCE, Integer.class);
  }

  @Override
  public Optional<Integer> getColor() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_CALENDAR_COLOR, Integer.class);
  }

  @Override
  public void setColor(int color) throws DavException, IOException {
    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<Integer>(CalDavConstants.PROPERTY_NAME_CALENDAR_COLOR, color));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  @Override
  public Optional<Integer> getOrder() throws PropertyParseException {
    return getProperty(CalDavConstants.PROPERTY_NAME_CALENDAR_ORDER, Integer.class);
  }

  @Override
  public void setOrder(Integer order) throws DavException, IOException {
    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<Integer>(CalDavConstants.PROPERTY_NAME_CALENDAR_ORDER, order));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  @Override
  protected MultiStatusResult<Calendar> getComponentsFromMultiStatus(MultiStatusResponse[] msResponses) {
    List<ComponentETagPair<Calendar>> calendars  = new LinkedList<ComponentETagPair<Calendar>>();
    List<InvalidComponentException>   exceptions = new LinkedList<InvalidComponentException>();

    for (MultiStatusResponse response : msResponses) {
      Calendar       calendar    = null;
      String         eTag        = null;
      DavPropertySet propertySet = response.getProperties(WebDavConstants.SC_OK);

      if (propertySet.get(CalDavConstants.PROPERTY_NAME_CALENDAR_DATA) != null) {
        String calendarData = (String) propertySet.get(CalDavConstants.PROPERTY_NAME_CALENDAR_DATA).getValue();

        // OwnCloud :(
        if (!calendarData.contains("\r"))
          calendarData = calendarData.replace("\n", "\r\n");

        try {

          calendar = new CalendarBuilder().build(new StringReader(calendarData));

        } catch (IOException e) {
          exceptions.add(
              new InvalidComponentException("Caught exception while parsing MultiStatus",
                                            CalDavConstants.CALDAV_NAMESPACE, getPath(), e)
          );
        } catch (ParserException e) {
          exceptions.add(
              new InvalidComponentException("Caught exception while parsing MultiStatus",
                                            CalDavConstants.CALDAV_NAMESPACE, getPath(), e)
          );
        }
      }

      if (propertySet.get(DavPropertyName.GETETAG) != null)
        eTag = (String) propertySet.get(DavPropertyName.GETETAG).getValue();

      if (calendar != null)
        calendars.add(new ComponentETagPair<Calendar>(calendar, Optional.fromNullable(eTag)));
    }

    return new MultiStatusResult<Calendar>(calendars, exceptions);
  }

  private MultiStatusResult<Calendar> getComponentsByType(String componentType)
      throws DavException, IOException
  {
    try {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      Element  resourceFilter = DomUtil.createElement(document,
                                                      CalDavConstants.PROPERTY_FILTER,
                                                      CalDavConstants.CALDAV_NAMESPACE);
      Element calendarFilter  = DomUtil.createElement(document,
                                                      CalDavConstants.PROPERTY_COMP_FILTER,
                                                      CalDavConstants.CALDAV_NAMESPACE);
      Element componentFilter = DomUtil.createElement(document,
                                                      CalDavConstants.PROPERTY_COMP_FILTER,
                                                      CalDavConstants.CALDAV_NAMESPACE);

      componentFilter.setAttribute(CalDavConstants.ATTRIBUTE_NAME, componentType);
      calendarFilter.setAttribute(CalDavConstants.ATTRIBUTE_NAME, Calendar.VCALENDAR);
      calendarFilter.appendChild(componentFilter);
      resourceFilter.appendChild(calendarFilter);

      ReportInfo reportInfo = new ReportInfo(getQueryReportType(), 1, getPropertyNamesForReports());
      reportInfo.setContentElement(resourceFilter);

      ReportMethod reportMethod = new ReportMethod(getPath(), reportInfo);

      try {

        client.execute(reportMethod);

        if (reportMethod.getStatusCode() == DavServletResponse.SC_MULTI_STATUS)
          return getComponentsFromMultiStatus(reportMethod.getResponseBodyAsMultiStatus().getResponses());

        throw new DavException(reportMethod.getStatusCode(), reportMethod.getStatusText());

      } finally {
        reportMethod.releaseConnection();
      }

    } catch (ParserConfigurationException e) {
      throw new IOException("Caught exception while building document.", e);
    }
  }

  public MultiStatusResult<Calendar> getEventComponents()
      throws DavException, IOException
  {
    return getComponentsByType(Component.VEVENT);
  }

  public MultiStatusResult<Calendar> getToDoComponents()
      throws DavException, IOException
  {
    return getComponentsByType(Component.VTODO);
  }

  @Override
  protected void putComponentToServer(Calendar calendar, Optional<String> ifMatchETag)
      throws InvalidComponentException, DavException, IOException
  {
    calendar.getProperties().remove(ProdId.PRODID);
    calendar.getProperties().add(new ProdId(((CalDavStore)getStore()).getProductId()));

    try {

      if (Calendars.getUid(calendar) == null)
        throw new InvalidComponentException("Cannot put iCal to server without UID!",
                                            CalDavConstants.CALDAV_NAMESPACE, getPath());

      String    calendarUid = Calendars.getUid(calendar).getValue();
      PutMethod putMethod   = new PutMethod(getComponentPathFromUid(calendarUid));

      if (ifMatchETag.isPresent())
        putMethod.addRequestHeader("If-Match", ifMatchETag.get()); // TODO: constant for this.
      else
        putMethod.addRequestHeader("If-None-Match", "*"); // TODO: constant for this.

      try {

        CalendarOutputter     calendarOutputter = new CalendarOutputter();
        ByteArrayOutputStream byteStream        = new ByteArrayOutputStream();

        calendarOutputter.output(calendar, byteStream);
        putMethod.setRequestEntity(new ByteArrayRequestEntity(byteStream.toByteArray(), CalDavConstants.HEADER_CONTENT_TYPE_CALENDAR));

        client.execute(putMethod);
        int status = putMethod.getStatusCode();

        if (status == WebDavConstants.SC_REQUEST_ENTITY_TOO_LARGE ||
            status == WebDavConstants.SC_FORBIDDEN)
        {
          throw new InvalidComponentException("Put method returned bad status " + status,
                                              CalDavConstants.CALDAV_NAMESPACE, getPath(), calendarUid);
        }

        if (status < WebDavConstants.SC_OK ||
            status > WebDavConstants.SC_NO_CONTENT)
        {
          throw new DavException(status, putMethod.getStatusText());
        }

      } finally {
        putMethod.releaseConnection();
      }

    } catch (ConstraintViolationException e) {
      throw new InvalidComponentException("Caught exception while parsing UID from calendar",
                                          CalDavConstants.CALDAV_NAMESPACE, getPath(), e);
    } catch (ValidationException e) {
      throw new InvalidComponentException("Caught exception whie outputting calendar to stream",
                                          CalDavConstants.CALDAV_NAMESPACE, getPath(), e);
    }
  }
}
