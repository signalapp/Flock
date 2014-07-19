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

import org.apache.jackrabbit.webdav.property.DavPropertyName;

/**
 * Programmer: rhodey
 */
public class PropertyParseException extends Exception {

  private String          path;
  private DavPropertyName propertyName;


  public PropertyParseException(String message, String path, DavPropertyName propertyName) {
    super(message);

    this.path         = path;
    this.propertyName = propertyName;
  }

  public PropertyParseException(String          message,
                                String          path,
                                DavPropertyName propertyName,
                                Throwable       cause)
  {
    super(message, cause);

    this.path         = path;
    this.propertyName = propertyName;
  }

  public String getPath() {
    return path;
  }

  public DavPropertyName getPropertyName() {
    return propertyName;
  }

  @Override
  public String toString() {
    if (getCause() == null) {
      return "message: " + getMessage() + ", path: " + path +
             ", property name: " + propertyName;
    }
    return "message: " + getMessage() + ", path: " + path +
           ", property name: " + propertyName + ", cause: " + getCause();
  }

}

