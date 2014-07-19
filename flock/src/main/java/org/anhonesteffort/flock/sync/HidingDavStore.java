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
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Programmer: rhodey
 */
public interface HidingDavStore<C extends HidingDavCollection<?>> {

  public String getHostHREF();

  public Optional<C> getCollection(String path) throws DavException, IOException;

  public List<C> getCollections() throws PropertyParseException, DavException, IOException;

  public void addCollection(String path) throws DavException, IOException, GeneralSecurityException;

  public void removeCollection(String path) throws DavException, IOException;

  public void releaseConnections();

}
