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

package org.anhonesteffort.flock.sync.key;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Programmer: rhodey
 */
public class KeyProviderStub extends ContentProvider {

  @Override
  public boolean onCreate() {
    return false;
  }

  @Override
  public Cursor query(Uri uri, String[] strings, String s, String[] strings2, String s2) {
    return null;
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues contentValues) {
    return null;
  }

  @Override
  public int delete(Uri uri, String s, String[] strings) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
    return 0;
  }

}
