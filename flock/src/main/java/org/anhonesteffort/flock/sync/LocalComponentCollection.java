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

import android.os.RemoteException;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;

import java.util.HashMap;
import java.util.List;

/**
 * Programmer: rhodey
 */
public interface LocalComponentCollection<T> {

  public String getPath();

  public Optional<String> getCTag() throws RemoteException;

  public void setCTag(String ctag) throws RemoteException;

  public Optional<String> getDisplayName() throws RemoteException;

  public void setDisplayName(String displayName) throws RemoteException;

  public HashMap<String, String> getComponentETags() throws RemoteException;

  public Optional<ComponentETagPair<T>> getComponent(String uid) throws RemoteException, InvalidComponentException;

  public List<ComponentETagPair<T>> getComponents() throws RemoteException, InvalidComponentException;

  public void addComponent(ComponentETagPair<T> component) throws RemoteException, InvalidComponentException;

  public void updateComponent(ComponentETagPair<T> component) throws RemoteException, InvalidComponentException;

  public void removeComponent(String path) throws RemoteException;

}
