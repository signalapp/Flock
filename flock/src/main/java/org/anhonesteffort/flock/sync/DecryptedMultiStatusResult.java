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

import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.MultiStatusResult;

import java.util.LinkedList;
import java.util.List;

/**
 * rhodey
 */
public class DecryptedMultiStatusResult<T> {

  protected List<ComponentETagPair<T>>            componentETagPairs;
  protected List<InvalidRemoteComponentException> invalidComponentExceptions;
  protected List<InvalidMacException>             invalidMacExceptions;

  public DecryptedMultiStatusResult(List<ComponentETagPair<T>>      componentETagPairs,
                                    List<InvalidComponentException> invalidComponentExceptions,
                                    List<InvalidMacException>       invalidMacExceptions)
  {
    this.componentETagPairs   = componentETagPairs;
    this.invalidMacExceptions = invalidMacExceptions;

    this.invalidComponentExceptions = new LinkedList<InvalidRemoteComponentException>();
    for (InvalidComponentException e : invalidComponentExceptions)
      this.invalidComponentExceptions.add(new InvalidRemoteComponentException(e));
  }

  public List<ComponentETagPair<T>> getComponentETagPairs() {
    return componentETagPairs;
  }

  public List<InvalidRemoteComponentException> getInvalidComponentExceptions() {
    return invalidComponentExceptions;
  }

  public List<InvalidMacException> getInvalidMacExceptions() {
    return invalidMacExceptions;
  }

}
