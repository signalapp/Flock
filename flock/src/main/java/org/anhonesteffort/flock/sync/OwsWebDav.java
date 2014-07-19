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

import org.apache.jackrabbit.webdav.xml.Namespace;

/**
 * Programmer: rhodey
 */
public class OwsWebDav {

  public static final String WEBDAV_HOST      = "flock-sync.whispersystems.org";
  public static final int    WEBDAV_PORT      = 443;
  public static final String HREF_WEBDAV_HOST = "https://" + WEBDAV_HOST + ":" + WEBDAV_PORT;

  public static final int STATUS_PAYMENT_REQUIRED = 402;

  public static final Namespace NAMESPACE = Namespace.getNamespace("org.anhonesteffort.flock");

  public static String getAddressbookPathForUsername(String username) {
    return "/addressbooks/__uids__/" + username + "/addressbook/";
  }

}


