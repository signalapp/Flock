/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.anhonesteffort.flock.sync;

import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.apache.jackrabbit.webdav.xml.Namespace;

/**
 * rhodey
 */
public class InvalidRemoteComponentException extends InvalidComponentException {

  public InvalidRemoteComponentException(InvalidComponentException e) {
    super(e.getMessage(), e.getNamespace(), e.getPath(), e.getCause());

    if (e.getUid().isPresent())
      this.uid = e.getUid().get();
  }

  public InvalidRemoteComponentException(String    message,
                                         Namespace namespace,
                                         String    path)
  {
    super(message, namespace, path);
  }

  public InvalidRemoteComponentException(String    message,
                                         Namespace namespace,
                                         String    path,
                                         String    uid)
  {
    super(message, namespace, path, uid);
  }

  public InvalidRemoteComponentException(String    message,
                                         Namespace namespace,
                                         String    path,
                                         Throwable cause)
  {
    super(message, namespace, path, cause);
  }

  public InvalidRemoteComponentException(String    message,
                                         Namespace namespace,
                                         String    path,
                                         String    uid,
                                         Throwable cause)
  {
    super(message, namespace, path, uid, cause);
  }

}
