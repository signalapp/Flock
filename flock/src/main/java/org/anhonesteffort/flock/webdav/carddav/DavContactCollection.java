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

package org.anhonesteffort.flock.webdav.carddav;

import com.google.common.base.Optional;
import ezvcard.VCard;

import org.anhonesteffort.flock.webdav.DavComponentCollection;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;

/**
 * Programmer: rhodey
 */
public interface DavContactCollection extends DavComponentCollection<VCard> {

  public Optional<String> getDescription() throws PropertyParseException;

  public void setDescription(String description) throws IOException, DavException;

  public Optional<Long> getMaxResourceSize() throws PropertyParseException;

}
