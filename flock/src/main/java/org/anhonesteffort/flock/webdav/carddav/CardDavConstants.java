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
package org.anhonesteffort.flock.webdav.carddav;

import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.xml.Namespace;

/**
 * Programmer: rhodey
 */
public class CardDavConstants {

  public static final Namespace CARDDAV_NAMESPACE = Namespace.getNamespace("C", "urn:ietf:params:xml:ns:carddav");

  public static final String RESOURCE_TYPE_ADDRESSBOOK = "addressbook";

  public static final String HEADER_CONTENT_TYPE_VCARD = "text/vcard";
  public static final String VCARD_FILE_EXTENSION      = ".vcf";

  public static final String PROPERTY_ADDRESSBOOK_HOME_SET    = "addressbook-home-set";
  public static final String PROPERTY_ADDRESSBOOK_DESCRIPTION = "addressbook-description";
  public static final String PROPERTY_MAX_RESOURCE_SIZE       = "max-resource-size";
  public static final String PROPERTY_SUPPORTED_ADDRESS_DATA  = "supported-address-data";
  public static final String PROPERTY_ADDRESS_DATA            = "address-data";
  public static final String PROPERTY_ADDRESSBOOK_QUERY       = "addressbook-query";
  public static final String PROPERTY_ADDRESSBOOK_MULTIGET    = "addressbook-multiget";

  public static final DavPropertyName PROPERTY_NAME_ADDRESSBOOK_HOME_SET = DavPropertyName.create(
      PROPERTY_ADDRESSBOOK_HOME_SET,
      CARDDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_ADDRESSBOOK_DESCRIPTION = DavPropertyName.create(
      PROPERTY_ADDRESSBOOK_DESCRIPTION,
      CARDDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_MAX_RESOURCE_SIZE = DavPropertyName.create(
      PROPERTY_MAX_RESOURCE_SIZE,
      CARDDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_SUPPORTED_ADDRESS_DATA = DavPropertyName.create(
      PROPERTY_SUPPORTED_ADDRESS_DATA,
      CARDDAV_NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_ADDRESS_DATA = DavPropertyName.create(
      PROPERTY_ADDRESS_DATA,
      CARDDAV_NAMESPACE
  );
}
