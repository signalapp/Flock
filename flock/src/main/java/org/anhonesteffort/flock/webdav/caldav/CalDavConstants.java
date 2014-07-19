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

import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.util.HashSet;
import java.util.Set;

/**
 * Programmer: rhodey
 */
public class CalDavConstants {

  public static final Namespace CALDAV_NAMESPACE          = Namespace.getNamespace("C", "urn:ietf:params:xml:ns:caldav");
  public static final Namespace CALENDAR_SERVER_NAMESPACE = Namespace.getNamespace("S", "http://calendarserver.org/ns/");
  public static final Namespace ICAL_NAMESPACE            = Namespace.getNamespace("I", "http://apple.com/ns/ical/");

  public static final String RESOURCE_TYPE_CALENDAR             = "calendar";
  public static final String RESOURCE_TYPE_CALENDAR_PROXY_READ  = "calendar-proxy-read";
  public static final String RESOURCE_TYPE_CALENDAR_PROXY_WRITE = "calendar-proxy-write";

  public static final String HEADER_CONTENT_TYPE_CALENDAR = "text/calendar";
  public static final String ICAL_FILE_EXTENSION          = ".ics";
  public static final String ATTRIBUTE_NAME               = "name";

  public static final String PROPERTY_CTAG                             = "getctag";
  public static final String PROPERTY_CALENDAR_DESCRIPTION             = "calendar-description";
  public static final String PROPERTY_CALENDAR_COLOR                   = "calendar-color";
  public static final String PROPERTY_CALENDAR_ORDER                   = "calendar-order";
  public static final String PROPERTY_CALENDAR_HOME_SET                = "calendar-home-set";
  public static final String PROPERTY_SUPPORTED_CALENDAR_COMPONENT_SET = "supported-calendar-component-set";
  public static final String PROPERTY_CALENDAR_TIMEZONE                = "calendar-timezone";
  public static final String PROPERTY_SUPPORTED_CALENDAR_DATA          = "supported-calendar-data";
  public static final String PROPERTY_MAX_RESOURCE_SIZE                = "max-resource-size";
  public static final String PROPERTY_MIN_DATE_TIME                    = "min-date-time";
  public static final String PROPERTY_MAX_DATE_TIME                    = "max-date-time";
  public static final String PROPERTY_MAX_INSTANCES                    = "max-instances";
  public static final String PROPERTY_MAX_ATTENDEES_PER_INSTANCE       = "max-attendees-per-instance";
  public static final String PROPERTY_CALENDAR_DATA                    = "calendar-data";
  public static final String PROPERTY_CALENDAR_QUERY                   = "calendar-query";
  public static final String PROPERTY_CALENDAR_MULTIGET                = "calendar-multiget";
  public static final String PROPERTY_FILTER                           = "filter";
  public static final String PROPERTY_COMP_FILTER                      = "comp-filter";

  public static final DavPropertyName PROPERTY_NAME_CALENDAR_DESCRIPTION = DavPropertyName.create(
      PROPERTY_CALENDAR_DESCRIPTION,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_CALENDAR_TIMEZONE = DavPropertyName.create(
      PROPERTY_CALENDAR_TIMEZONE,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_SUPPORTED_CALENDAR_COMPONENT_SET = DavPropertyName.create(
      PROPERTY_SUPPORTED_CALENDAR_COMPONENT_SET,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_SUPPORTED_CALENDAR_DATA = DavPropertyName.create(
      PROPERTY_SUPPORTED_CALENDAR_DATA,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_MAX_RESOURCE_SIZE = DavPropertyName.create(
      PROPERTY_MAX_RESOURCE_SIZE,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_MIN_DATE_TIME = DavPropertyName.create(
      PROPERTY_MIN_DATE_TIME,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_MAX_DATE_TIME = DavPropertyName.create(
      PROPERTY_MAX_DATE_TIME,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_MAX_INSTANCES = DavPropertyName.create(
      PROPERTY_MAX_INSTANCES,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_MAX_ATTENDEES_PER_INSTANCE = DavPropertyName.create(
      PROPERTY_MAX_ATTENDEES_PER_INSTANCE,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_CALENDAR_DATA = DavPropertyName.create(
      PROPERTY_CALENDAR_DATA,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_CALENDAR_HOME_SET = DavPropertyName.create(
      PROPERTY_CALENDAR_HOME_SET,
      CALDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_CTAG = DavPropertyName.create(
      CalDavConstants.PROPERTY_CTAG,
      CalDavConstants.CALENDAR_SERVER_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_CALENDAR_COLOR = DavPropertyName.create(
      CalDavConstants.PROPERTY_CALENDAR_COLOR,
      CalDavConstants.ICAL_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_CALENDAR_ORDER = DavPropertyName.create(
      CalDavConstants.PROPERTY_CALENDAR_ORDER,
      CalDavConstants.ICAL_NAMESPACE
  );
}
