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

import org.anhonesteffort.flock.util.guava.Optional;

/**
 * Programmer: rhodey
 */
public class ComponentETagPair<T> {

  private final T                component;
  private final Optional<String> eTag;

  public ComponentETagPair(T component, Optional<String> eTag) {
    this.component = component;
    this.eTag      = eTag;
  }

  public T getComponent() {
    return component;
  }

  public Optional<String> getETag() {
    return eTag;
  }

}
