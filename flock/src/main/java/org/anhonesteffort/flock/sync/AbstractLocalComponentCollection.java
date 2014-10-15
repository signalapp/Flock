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

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Programmer: rhodey
 * Date: 2/4/14
 */
// TODO: I really don't think we need all of these null cursor checks...
public abstract class AbstractLocalComponentCollection<T> implements LocalComponentCollection<T> {

  private static final String TAG = "org.anhonesteffort.flock.sync.AbstractLocalComponentCollection";

  protected       ContentProviderClient client;
  protected       Account               account;
  protected       String                remotePath;
  protected final Long                  localId;

  protected ArrayList<ContentProviderOperation> pendingOperations;

  public AbstractLocalComponentCollection(ContentProviderClient client,
                                          Account               account,
                                          String                remotePath,
                                          Long                  localId)
  {
    this.client     = client;
    this.account    = account;
    this.remotePath = remotePath;
    this.localId    = localId;

    pendingOperations = new ArrayList<ContentProviderOperation>();
  }

  public Account getAccount() {
    return account;
  }

  @Override
  public String getPath() {
    return remotePath;
  }

  public Long getLocalId() {
    return localId;
  }

  protected abstract Uri getSyncAdapterUri(Uri base);
  protected abstract Uri handleAddAccountQueryParams(Uri uri);
  protected abstract Uri getUriForComponents();

  protected abstract String getColumnNameCollectionLocalId();
  protected abstract String getColumnNameComponentLocalId();
  protected abstract String getColumnNameComponentUid();
  protected abstract String getColumnNameComponentETag();

  protected abstract String getColumnNameDirty();
  protected abstract String getColumnNameDeleted();
  protected abstract String getColumnNameQueuedForMigration();
  protected abstract String getColumnNameAccountType();

  public List<Long> getNewComponentIds() throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentLocalId(), getColumnNameComponentUid()};
    final String   SELECTION  = "(" + getColumnNameComponentUid() + " IS NULL OR "  +
                                      getColumnNameQueuedForMigration() + "=1) AND " +
                                      getColumnNameCollectionLocalId()  + "=" + localId;

    Cursor     cursor = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    List<Long> newIds = new LinkedList<Long>();

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext()) {
      if (!newIds.contains(cursor.getLong(0))) // android gets weird sometimes :(
        newIds.add(cursor.getLong(0));
    }
    cursor.close();

    return newIds;
  }

  public List<Pair<Long, String>> getUpdatedComponentIds() throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentLocalId(), getColumnNameComponentUid()};
    final String   SELECTION  = getColumnNameDirty() + "=1 AND " +
                                getColumnNameComponentUid() + " IS NOT NULL AND " +
                                getColumnNameCollectionLocalId() + "=" + localId;

    Cursor                   cursor  = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    List<Pair<Long, String>> idPairs = new LinkedList<Pair<Long, String>>();

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext()) {
      if (!idPairs.contains(new Pair<Long, String>(cursor.getLong(0), cursor.getString(1)))) // android gets weird sometimes :(
        idPairs.add(new Pair<Long, String>(cursor.getLong(0), cursor.getString(1)));
    }
    cursor.close();

    return idPairs;
  }

  public List<Pair<Long, String>> getDeletedComponentIds()  throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentLocalId(), getColumnNameComponentUid()};
    final String   SELECTION  = getColumnNameDeleted() + "=1 AND " +
                                getColumnNameComponentUid() + " IS NOT NULL AND " +
                                getColumnNameCollectionLocalId() + "=" + localId;

    Cursor                   cursor  = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    List<Pair<Long, String>> idPairs = new LinkedList<Pair<Long, String>>();

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext()) {
      if (!idPairs.contains(new Pair<Long, String>(cursor.getLong(0), cursor.getString(1)))) // android gets weird sometimes :(
        idPairs.add(new Pair<Long, String>(cursor.getLong(0), cursor.getString(1)));
    }
    cursor.close();

    return idPairs;
  }

  public List<Long> getComponentIds() throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentLocalId(), getColumnNameComponentUid()};
          String   selection  = null;

    if (account != null) {
      selection = getColumnNameDeleted() + "=0 AND " +
                  getColumnNameCollectionLocalId() + "=" + localId;
    }
    else {
      selection = getColumnNameDeleted() + "=0 AND " +
                  getColumnNameCollectionLocalId() + "=" + localId + " AND " +
                  getColumnNameAccountType() + " IS NULL";
    }

    Cursor     cursor = client.query(getUriForComponents(), PROJECTION, selection, null, null);
    List<Long> componentIds = new LinkedList<Long>();

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext()) {
      if (!componentIds.contains(cursor.getLong(0))) // android gets weird sometimes :(
        componentIds.add(cursor.getLong(0));
    }
    cursor.close();

    return componentIds;
  }

  public Optional<Long> getLocalIdForUid(String uid) throws RemoteException {
    final String[] PROJECTION     = new String[]{getColumnNameComponentLocalId()};
    final String[] SELECTION_ARGS = new String[]{uid};
          String   selection      = null;

    if (account != null) {
      selection = getColumnNameComponentUid()      + "=? AND " +
                  getColumnNameCollectionLocalId() + "=" + localId;
    }
    else {
      selection = getColumnNameComponentUid()      + "=? AND " +
                  getColumnNameCollectionLocalId() + "=" + localId + " AND " +
                  getColumnNameAccountType()       + " IS NULL";
    }

    Cursor cursor = client.query(getUriForComponents(),
                                 PROJECTION,
                                 selection,
                                 SELECTION_ARGS,
                                 null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    Optional<Long> result = Optional.absent();
    if (cursor.moveToNext())
      result = Optional.fromNullable(cursor.getLong(0));

    cursor.close();
    return result;
  }

  public Optional<String> getETagForUid(String uid) throws RemoteException {
    final String[] PROJECTION     = new String[]{getColumnNameComponentETag()};
    final String   SELECTION      = getColumnNameComponentUid() + "=? AND " +
                                    getColumnNameCollectionLocalId() + "=" + localId;
    final String[] SELECTION_ARGS = new String[]{uid};

    Cursor cursor = client.query(getUriForComponents(),
                                 PROJECTION,
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    Optional<String> result = Optional.absent();
    if (cursor.moveToNext())
      result = Optional.fromNullable(cursor.getString(0));

    cursor.close();
    return result;
  }

  public Optional<String> getUidForLocalId(Long localId) throws RemoteException {
    final String[] PROJECTION     = new String[]{getColumnNameComponentUid()};
    final String   SELECTION      = getColumnNameComponentLocalId()  + "=" + localId + " AND " +
                                    getColumnNameCollectionLocalId() + "=" + this.localId;

    Cursor cursor = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    Optional<String> result = Optional.absent();
    if (cursor.moveToNext())
      result = Optional.fromNullable(cursor.getString(0));

    cursor.close();
    return result;
  }

  public String populateComponentUid(Long localId)
      throws OperationApplicationException, RemoteException
  {
    Optional<String> uid = getUidForLocalId(localId);

    if (uid.isPresent()) {
      Log.d(TAG, "populateComponentUid() uid already exists, ignoring");
      return uid.get();
    }

    String rand = UUID.randomUUID().toString();
    Log.d(TAG, "populateComponentUid() gonna populate " + localId + " with " + rand);

    pendingOperations.add(ContentProviderOperation
        .newUpdate(ContentUris.withAppendedId(getUriForComponents(), localId))
        .withValue(getColumnNameComponentUid(), rand)
        .withYieldAllowed(false)
        .build());

    commitPendingOperations();
    return rand;
  }

  public void removeComponent(Long localId) {
    Log.d(TAG, "removeComponent() localId " + localId);

    pendingOperations.add(ContentProviderOperation
        .newDelete(ContentUris.withAppendedId(getUriForComponents(), localId))
        .withYieldAllowed(true)
        .build());
  }

  @Override
  public void removeComponent(String remoteUId) throws RemoteException {
    final String   SELECTION      = getColumnNameComponentUid()      + "=? AND " +
                                    getColumnNameCollectionLocalId() + "=" + localId;
    final String[] SELECTION_ARGS = new String[]{remoteUId};

    pendingOperations.add(ContentProviderOperation
        .newDelete(getUriForComponents())
        .withSelection(SELECTION, SELECTION_ARGS)
        .withYieldAllowed(true)
        .build());
  }

  @Override
  public void removeAllComponents() throws RemoteException {
    final String SELECTION     = getColumnNameCollectionLocalId() + "=" + localId;
    final Uri    COMPONENT_URI = getUriForComponents().buildUpon().clearQuery().build();
    final Uri    CONTENT_URI   = handleAddAccountQueryParams(COMPONENT_URI);

    pendingOperations.add(ContentProviderOperation
        .newDelete(CONTENT_URI)
        .withSelection(SELECTION, null)
        .withYieldAllowed(true)
        .build());
  }

  @Override
  public HashMap<String, String> getComponentETags() throws RemoteException {
    final String[] PROJECTION = new String[]{getColumnNameComponentUid(), getColumnNameComponentETag()};
    final String   SELECTION  = getColumnNameComponentUid() + " IS NOT NULL " +
                                "AND " + getColumnNameComponentETag() + " IS NOT NULL " +
                                "AND " + getColumnNameDeleted() + "=0 AND " +
                                getColumnNameCollectionLocalId() + "=" + localId;

    Cursor                  cursor = client.query(getUriForComponents(), PROJECTION, SELECTION, null, null);
    HashMap<String, String> pairs  = new HashMap<String, String>();

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext())
      pairs.put(cursor.getString(0), cursor.getString(1));
    cursor.close();

    return pairs;
  }

  public void cleanComponent(Long localId) {
    Log.d(TAG, "cleanComponent() localId " + localId);

    pendingOperations.add(ContentProviderOperation
        .newUpdate(ContentUris.withAppendedId(getUriForComponents(), localId))
        .withValue(getColumnNameDirty(), 0)
        .withValue(getColumnNameQueuedForMigration(), 0)
        .build());
  }

  public void dirtyComponent(Long localId) {
    Log.d(TAG, "dirtyComponent() localId " + localId);

    pendingOperations.add(ContentProviderOperation
        .newUpdate(ContentUris.withAppendedId(getUriForComponents(), localId))
        .withValue(getColumnNameDirty(), 1).build());
  }

  public void setUidToNull(Long localId) {
    Log.d(TAG, "setUidToNull() localId " + localId);

    pendingOperations.add(ContentProviderOperation
        .newUpdate(ContentUris.withAppendedId(getUriForComponents(), localId))
        .withValue(getColumnNameComponentUid(), null)
        .build());
  }

  public void queueForMigration(Long localId)
      throws RemoteException
  {
      pendingOperations.add(ContentProviderOperation
          .newUpdate(ContentUris.withAppendedId(getUriForComponents(), localId))
          .withValue(getColumnNameQueuedForMigration(), 1)
          .withYieldAllowed(false)
          .build());
  }

  public int commitPendingOperations()
      throws OperationApplicationException, RemoteException
  {
    ContentProviderResult[] result = new ContentProviderResult[0];

    if (!pendingOperations.isEmpty())
      result = client.applyBatch(pendingOperations);

    pendingOperations.clear();
    return result.length;
  }

}
