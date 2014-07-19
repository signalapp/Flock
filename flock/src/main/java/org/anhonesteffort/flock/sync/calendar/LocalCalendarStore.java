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

package org.anhonesteffort.flock.sync.calendar;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.LocalComponentStore;

import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class LocalCalendarStore implements LocalComponentStore<LocalEventCollection> {

  private static final String TAG = "org.anhonesteffort.flock.sync.calendar.LocalCalendarStore";

  public static final int MAXIMUM_COLLECTIONS = 100; // is this reasonable?
  
  private ContentProviderClient client;
  private Account               account;

  public LocalCalendarStore(ContentProviderClient client, Account account) {
    this.client  = client;
    this.account = account;
  }

  public LocalCalendarStore(Context context, Account account) {
    this.account = account;
    client       = context.getContentResolver().acquireContentProviderClient
                     (CalendarsSyncScheduler.CONTENT_AUTHORITY);
  }

  @Override
  public Optional<LocalEventCollection> getCollection(String remotePath)
      throws RemoteException
  {
    final String[] PROJECTION     = new String[]{CalendarContract.Calendars._ID,
                                                 CalendarContract.Calendars.NAME};
    final String   SELECTION      = CalendarContract.Calendars.DELETED + "=0 " +
                                    "AND " + CalendarContract.Calendars.SYNC_EVENTS + "=1 " +
                                    "AND " + CalendarContract.Calendars.NAME + "=?";
    final String[] SELECTION_ARGS = new String[]{remotePath};

    Cursor cursor = client.query(LocalEventCollection.getCollectionsUri(account),
                                 PROJECTION,
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    List<LocalEventCollection> collections = new LinkedList<LocalEventCollection>();

    if (cursor.moveToNext())
      collections.add(new LocalEventCollection(client,
                                               account,
                                               cursor.getLong(0),
                                               cursor.getString(1)));
    cursor.close();

    if (collections.size() > 0)
      return Optional.of(collections.get(0));

    return Optional.absent();
  }

  @Override
  public void addCollection(String remotePath) throws RemoteException {
    addCollection(remotePath, "UNKNOWN", 0xffffffff);
  }

  @Override
  public void addCollection(String remotePath, String displayName) throws RemoteException {
    addCollection(remotePath, displayName, 0xffffffff);
  }

  public void addCollection(String remotePath, String displayName, int color) throws RemoteException {
    if (getCollection(remotePath).isPresent())
    {
      Log.w(TAG, "attempted to create duplicate collection at " + remotePath);
      throw new RemoteException("Collection already exists!");
    }

    ContentValues values = new ContentValues();

    values.put(CalendarContract.Calendars.ACCOUNT_NAME, account.name);
    values.put(CalendarContract.Calendars.ACCOUNT_TYPE, account.type);

    values.put(LocalEventCollection.COLUMN_NAME_COLLECTION_COPIED, 0);

    values.put(CalendarContract.Calendars.NAME,                  remotePath);
    values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName);
    values.put(CalendarContract.Calendars.CALENDAR_COLOR,        color);

    values.put(CalendarContract.Calendars.OWNER_ACCOUNT, account.name);
    values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
               CalendarContract.Calendars.CAL_ACCESS_OWNER);

    values.put(CalendarContract.Calendars.ALLOWED_REMINDERS,
               CalendarContract.Reminders.METHOD_ALERT);

    values.put(CalendarContract.Calendars.ALLOWED_AVAILABILITY,
               CalendarContract.Events.AVAILABILITY_BUSY + "," +
               CalendarContract.Events.AVAILABILITY_FREE);

    values.put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES,
               CalendarContract.Attendees.TYPE_NONE + "," +
               CalendarContract.Attendees.TYPE_OPTIONAL + "," +
               CalendarContract.Attendees.TYPE_REQUIRED + "," +
               CalendarContract.Attendees.TYPE_RESOURCE);

    values.put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 0);
    values.put(CalendarContract.Calendars.CAN_MODIFY_TIME_ZONE,  1);

    values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
    values.put(CalendarContract.Calendars.VISIBLE,     1);

    client.insert(LocalEventCollection.getCollectionsUri(account), values);
  }

  @Override
  public void removeCollection(String remotePath) throws RemoteException{
    final String   SELECTION      = CalendarContract.Calendars.NAME + "=?";
    final String[] SELECTION_ARGS = new String[]{remotePath};

    client.delete(LocalEventCollection.getCollectionsUri(account),
                  SELECTION,
                  SELECTION_ARGS);
  }

  public void setCollectionCopied(Long localId, boolean isCopied) throws RemoteException {
    final String   COLLECTION_SELECTION      = CalendarContract.Calendars._ID + "=?";
    final String[] COLLECTION_SELECTION_ARGS = new String[] {localId.toString()};
    final Uri      COLLECTION_URI            = LocalEventCollection.getCollectionsUri(account);

    ContentValues updateValues = new ContentValues();
    updateValues.put(LocalEventCollection.COLUMN_NAME_COLLECTION_COPIED, isCopied ? 1 : 0);

    client.update(COLLECTION_URI, updateValues, COLLECTION_SELECTION, COLLECTION_SELECTION_ARGS);
  }

  public void setCollectionPath(Long localId, String path) throws RemoteException {
    final String   COLLECTION_SELECTION      = CalendarContract.Calendars._ID + "=?";
    final String[] COLLECTION_SELECTION_ARGS = new String[] {localId.toString()};
    final Uri      COLLECTION_URI            = LocalEventCollection.getCollectionsUri(account);

    ContentValues updateValues = new ContentValues();
    updateValues.put(CalendarContract.Calendars.NAME, path);

    client.update(COLLECTION_URI, updateValues, COLLECTION_SELECTION, COLLECTION_SELECTION_ARGS);
  }

  @Override
  public List<LocalEventCollection> getCollections() throws RemoteException {
    final String[] PROJECTION =
        new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.NAME};

    String SELECTION = CalendarContract.Calendars.DELETED + "=0 AND " +
                       CalendarContract.Calendars.SYNC_EVENTS + "=1";

    if (account.type.equals(DavAccount.SYNC_ACCOUNT_TYPE))
      SELECTION += " AND " + LocalEventCollection.COLUMN_NAME_COLLECTION_COPIED + "=0";

    Cursor cursor = client.query(LocalEventCollection.getCollectionsUri(account),
                                 PROJECTION,
                                 SELECTION, null, null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    List<LocalEventCollection> collections = new LinkedList<LocalEventCollection>();

    while (cursor.moveToNext())
      collections.add(new LocalEventCollection(client,
                                               account,
                                               cursor.getLong(0),
                                               cursor.getString(1)));
    cursor.close();

    return collections;
  }

  public List<LocalEventCollection> getCollectionsIgnoreSync() throws RemoteException {
    final String[] PROJECTION =
        new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.NAME};

    String SELECTION = CalendarContract.Calendars.DELETED + "=0";

    if (account.type.equals(DavAccount.SYNC_ACCOUNT_TYPE))
      SELECTION += " AND " + LocalEventCollection.COLUMN_NAME_COLLECTION_COPIED + "=0";

    Cursor cursor = client.query(LocalEventCollection.getCollectionsUri(account),
                                 PROJECTION,
                                 SELECTION, null, null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    List<LocalEventCollection> collections = new LinkedList<LocalEventCollection>();

    while (cursor.moveToNext())
      collections.add(new LocalEventCollection(client,
                                               account,
                                               cursor.getLong(0),
                                               cursor.getString(1)));
    cursor.close();

    return collections;
  }

  public List<LocalEventCollection> getCopiedCollections() throws RemoteException {
    final String[] PROJECTION =
        new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.NAME};

    final String SELECTION = LocalEventCollection.COLUMN_NAME_COLLECTION_COPIED + "=1 AND " +
                             CalendarContract.Calendars.DELETED + "=0 AND " +
                             CalendarContract.Calendars.SYNC_EVENTS + "=1";

    if (!account.type.equals(DavAccount.SYNC_ACCOUNT_TYPE))
      throw new RemoteException("Unable to determine which collections are copied!");

    Cursor cursor = client.query(LocalEventCollection.getCollectionsUri(account),
                                 PROJECTION,
                                 SELECTION, null, null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    List<LocalEventCollection> collections = new LinkedList<LocalEventCollection>();

    while (cursor.moveToNext())
      collections.add(new LocalEventCollection(client,
                                               account,
                                               cursor.getLong(0),
                                               cursor.getString(1)));
    cursor.close();

    return collections;
  }

}
