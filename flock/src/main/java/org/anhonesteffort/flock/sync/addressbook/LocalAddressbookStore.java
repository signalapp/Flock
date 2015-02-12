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

package org.anhonesteffort.flock.sync.addressbook;

import android.content.ContentProviderClient;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.LocalComponentStore;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class LocalAddressbookStore implements LocalComponentStore<LocalContactCollection> {

  private static final String TAG = "org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore";

  private Context               context;
  private ContentProviderClient client;
  private DavAccount            account;

  // NOTICE: Android only allows for one "address book" per account, so without multi-account
  // NOTICE... support we get one CardDAV collection and thus one address book.
  public LocalAddressbookStore(Context context,
                               ContentProviderClient client,
                               DavAccount account)
  {
    this.context = context;
    this.client  = client;
    this.account = account;
  }

  public LocalAddressbookStore(Context context,
                               DavAccount account)
  {
    this.context = context;
    this.account = account;

    client = context.getContentResolver().acquireContentProviderClient
               (AddressbookSyncScheduler.CONTENT_AUTHORITY);
  }

  @Override
  public Optional<LocalContactCollection> getCollection(String remotePath)
    throws RemoteException
  {
    Optional<String> collectionPath = account.getCardDavCollectionPath(context);
    String           remotePathDecoded;

    try {

      remotePathDecoded = (URLDecoder.decode(remotePath, "UTF8"));

    } catch (UnsupportedEncodingException e) {
      Log.e(TAG, "caught exception while returning collection", e);
      throw new RemoteException(e.toString()); // HACK :(
    }

    if (collectionPath.isPresent() &&
        (collectionPath.get().equals(remotePath) || collectionPath.get().equals(remotePathDecoded)))
    {
      return Optional.of(new LocalContactCollection(context,
                                                    client,
                                                    account.getOsAccount(),
                                                    collectionPath.get()));
    }

    return Optional.absent();
  }

  @Override
  public void addCollection(String remotePath) {
    account.setCardDavCollection(context, remotePath);
  }

  @Override
  public void addCollection(String remotePath, String displayName) {
    account.setCardDavCollection(context, remotePath);

    LocalContactCollection collection = new LocalContactCollection(context, client, account.getOsAccount(), remotePath);
    collection.setDisplayName(displayName);
  }

  @Override
  public void removeCollection(String remotePath) {
    account.setCardDavCollection(context, null);
  }

  @Override
  public List<LocalContactCollection> getCollections() {
    List<LocalContactCollection> collections    = new LinkedList<LocalContactCollection>();
    Optional<String>             collectionPath = account.getCardDavCollectionPath(context);

    if (collectionPath.isPresent())
      collections.add(new LocalContactCollection(context, client, account.getOsAccount(), collectionPath.get()));

    return collections;
  }
}
