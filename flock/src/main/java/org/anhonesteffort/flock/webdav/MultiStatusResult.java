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

package org.anhonesteffort.flock.webdav;

import java.util.List;

/**
 * rhodey
 */
public class MultiStatusResult<T> {

  protected List<ComponentETagPair<T>>      componentETagPairs;
  protected List<InvalidComponentException> invalidComponentExceptions;

  public MultiStatusResult(List<ComponentETagPair<T>>      componentETagPairs,
                           List<InvalidComponentException> invalidComponentExceptions)
  {
    this.componentETagPairs         = componentETagPairs;
    this.invalidComponentExceptions = invalidComponentExceptions;
  }

  public List<ComponentETagPair<T>> getComponentETagPairs() {
    return componentETagPairs;
  }

  public List<InvalidComponentException> getInvalidComponentExceptions() {
    return invalidComponentExceptions;
  }

}
