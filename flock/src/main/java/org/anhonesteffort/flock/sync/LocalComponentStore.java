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

import org.anhonesteffort.flock.util.guava.Optional;

import java.util.List;

/**
 * Programmer: rhodey
 */
public interface LocalComponentStore<C extends LocalComponentCollection<?>> {

  public Optional<C> getCollection(String path) throws RemoteException;

  public List<C> getCollections() throws RemoteException;

  public void addCollection(String path) throws RemoteException;

  public void addCollection(String path, String displayName) throws RemoteException;

  public void removeCollection(String path) throws RemoteException;

}
