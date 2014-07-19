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

import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.apache.jackrabbit.webdav.xml.Namespace;

/**
 * Programmer: rhodey
 */
public class InvalidLocalComponentException extends InvalidComponentException {

  private long localId;

  public InvalidLocalComponentException(String    message,
                                        Namespace namespace,
                                        String    path,
                                        long      localId)
  {
    super(message, false, namespace, path);

    this.localId = localId;
  }

  public InvalidLocalComponentException(String    message,
                                        Namespace namespace,
                                        String    path,
                                        long      localId,
                                        Throwable cause)
  {
    super(message, false, namespace, path, cause);

    this.localId = localId;
  }

  public InvalidLocalComponentException(String    message,
                                        Namespace namespace,
                                        String    path,
                                        long      localId,
                                        String    uid)
  {
    super(message, false, namespace, path, uid);

    this.localId = localId;
  }

  public InvalidLocalComponentException(String    message,
                                        Namespace namespace,
                                        String    path,
                                        long      localId,
                                        String    uid,
                                        Throwable cause)
  {
    super(message, false, namespace, path, uid, cause);

    this.localId = localId;
  }

  public Long getLocalId() {
    return localId;
  }

  @Override
  public String toString() {
    return super.toString() + ", local id: " + localId;
  }

}
