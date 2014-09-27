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
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Programmer: rhodey
 */
public interface DavComponentCollection<T> {

  public String getPath();

  public <P> Optional<P> getProperty(DavPropertyName propertyName, Class<P> type)
      throws PropertyParseException;

  public Optional<String> getCTag() throws PropertyParseException;

  public List<String> getResourceTypes() throws PropertyParseException;

  public Optional<String> getOwnerHref() throws PropertyParseException;

  public Optional<Long> getQuotaAvailableBytes() throws PropertyParseException;

  public Optional<Long> getQuotaUsedBytes() throws PropertyParseException;

  public Optional<String> getResourceId() throws PropertyParseException;

  public Optional<String> getSyncToken() throws PropertyParseException;

  public Optional<String> getDisplayName() throws PropertyParseException;

  public void setDisplayName(String displayName) throws DavException, IOException;

  public void patchProperties(DavPropertySet setProperties, DavPropertyNameSet removeProperties)
      throws DavException, IOException;

  public HashMap<String, String> getComponentETags() throws DavException, IOException;

  public Optional<ComponentETagPair<T>> getComponent(String uid) throws InvalidComponentException, DavException, IOException;

  public MultiStatusResult<T> getComponents(List<String> uids) throws DavException, IOException;

  public MultiStatusResult<T> getComponents() throws DavException, IOException;

  public void addComponent(T component) throws InvalidComponentException, DavException, IOException;

  public void updateComponent(ComponentETagPair<T> component) throws InvalidComponentException, DavException, IOException;

  public void removeComponent(String path) throws DavException, IOException;

}
