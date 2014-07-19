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

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavPropertyName;

/**
 * Programmer: rhodey
 */
public class WebDavConstants {

  public static final String PROPERTY_RESOURCE_ID            = "resource-id";
  public static final String PROPERTY_CURRENT_USER_PRINCIPAL = "current-user-principal";
  public static final String PROPERTY_SUPPORTED_REPORT_SET   = "supported-report-set";
  public static final String PROPERTY_SYNC_TOKEN             = "sync-token";
  public static final String PROPERTY_QUOTA_AVAILABLE_BYTES  = "quota-available-bytes";
  public static final String PROPERTY_QUOTA_USED_BYTES       = "quota-used-bytes";

  public static final DavPropertyName PROPERTY_NAME_PROP = DavPropertyName.create(
      DavConstants.XML_PROP,
      DavConstants.NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_RESOURCE_ID = DavPropertyName.create(
      PROPERTY_RESOURCE_ID,
      DavConstants.NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_CURRENT_USER_PRINCIPAL = DavPropertyName.create(
      PROPERTY_CURRENT_USER_PRINCIPAL,
      DavConstants.NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_SUPPORTED_REPORT_SET = DavPropertyName.create(
      PROPERTY_SUPPORTED_REPORT_SET,
      DavConstants.NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_SYNC_TOKEN = DavPropertyName.create(
      PROPERTY_SYNC_TOKEN,
      DavConstants.NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_QUOTA_USED_BYTES = DavPropertyName.create(
      PROPERTY_QUOTA_USED_BYTES,
      DavConstants.NAMESPACE
  );

  public static final DavPropertyName PROPERTY_NAME_QUOTA_AVAILABLE_BYTES = DavPropertyName.create(
      PROPERTY_QUOTA_AVAILABLE_BYTES,
      DavConstants.NAMESPACE
  );
}
