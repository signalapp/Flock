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

import com.google.common.base.Optional;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;

/**
 * Programmer: rhodey
 */
public interface HidingDavCollection<T> {

  public String getPath();

  public Optional<String> getCTag() throws PropertyParseException;

  public boolean isFlockCollection() throws PropertyParseException;

  public void makeFlockCollection(String displayName) throws DavException, IOException, GeneralSecurityException;

  public void fetchProperties() throws DavException, IOException;

  public Optional<String> getHiddenDisplayName()
      throws PropertyParseException, InvalidMacException, GeneralSecurityException, IOException;

  public void setHiddenDisplayName(String displayName)
      throws DavException, IOException, GeneralSecurityException;

  public HashMap<String, String> getComponentETags() throws DavException, IOException;

  public Optional<ComponentETagPair<T>> getHiddenComponent(String uid)
      throws InvalidComponentException, DavException,
      InvalidMacException, GeneralSecurityException, IOException;

  public List<ComponentETagPair<T>> getHiddenComponents()
      throws InvalidComponentException, DavException,
      InvalidMacException, GeneralSecurityException, IOException;

  public void addHiddenComponent(T component)
      throws InvalidComponentException, DavException, GeneralSecurityException, IOException;

  public void updateHiddenComponent(ComponentETagPair<T> component)
      throws InvalidComponentException, DavException, GeneralSecurityException, IOException;

  public void removeComponent(String path) throws DavException, IOException;

}
