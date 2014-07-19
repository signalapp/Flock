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
import org.apache.jackrabbit.webdav.xml.Namespace;

/**
 * Programmer: rhodey
 */
public class InvalidComponentException extends Exception {

  private boolean   isServersFault;
  private Namespace namespace;
  private String    path;
  private String    uid;

  public InvalidComponentException(String    message,
                                   boolean   isServersFault,
                                   Namespace namespace,
                                   String    path)
  {
    super(message);

    this.isServersFault = isServersFault;
    this.namespace      = namespace;
    this.path           = path;
  }

  public InvalidComponentException(String    message,
                                   boolean   isServersFault,
                                   Namespace namespace,
                                   String    path,
                                   String    uid)
  {
    super(message);

    this.isServersFault = isServersFault;
    this.namespace      = namespace;
    this.path           = path;
    this.uid            = uid;
  }

  public InvalidComponentException(String    message,
                                   boolean   isServersFault,
                                   Namespace namespace,
                                   String    path,
                                   Throwable cause)
  {
    super(message, cause);

    this.isServersFault = isServersFault;
    this.namespace      = namespace;
    this.path           = path;
  }

  public InvalidComponentException(String    message,
                                   boolean   isServersFault,
                                   Namespace namespace,
                                   String    path,
                                   String    uid,
                                   Throwable cause)
  {
    super(message, cause);

    this.isServersFault = isServersFault;
    this.namespace      = namespace;
    this.path           = path;
    this.uid            = uid;
  }

  public boolean isServersFault() {
    return isServersFault;
  }

  public Namespace getNamespace() {
    return namespace;
  }

  public String getPath() {
    return path;
  }

  public Optional<String>  getUid() {
    return Optional.fromNullable(uid);
  }

  @Override
  public String toString() {
    if (getCause() == null) {
      if (!getUid().isPresent()) {
        return "message: " + getMessage() + ", is servers fault: " + isServersFault +
               ", namespace: " + namespace.getURI() + ", path: " + path;
      }
      else {
        return "message: " + getMessage() + ", is servers fault: " + isServersFault +
               ", namespace: " + namespace.getURI() + ", path: " + path + ", uid: " + getUid().get();
      }
    }
    else {
      if (!getUid().isPresent()) {
        return "message: " + getMessage() + ", is servers fault: " + isServersFault +
               ", namespace: " + namespace.getURI() + ", path: " + path + ", cause: " + getCause();
      }
      else {
        return "message: " + getMessage() + ", is servers fault: " + isServersFault +
               ", namespace: " + namespace.getURI() + ", path: " + path +
               ", uid: " + getUid().get() + ", cause: " + getCause();
      }
    }

  }

}
