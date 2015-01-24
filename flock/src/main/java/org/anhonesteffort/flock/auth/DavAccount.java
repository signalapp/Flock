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

package org.anhonesteffort.flock.auth;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.common.base.Optional;


/**
 * Programmer: rhodey
 */
public class DavAccount {

  public  static final String SYNC_ACCOUNT_TYPE       = "openwhispersystems.org";
  private static final String KEY_CARD_DAV_COLLECTION = "org.anhonesteffort.flock.auth.DavAccount.KEY_CARD_DAV_COLLECTION";

  private static final String KEY_USER_ID       = "KEY_USER_ID";
  private static final String KEY_AUTH_TOKEN    = "KEY_AUTH_TOKEN";
  private static final String KEY_DAV_HOST_HREF = "KEY_DAV_HOST_HREF";

  private final Account osAccount;

  private String userId;
  private String authToken;
  private String davHostHREF;

  public DavAccount(String userId,
                    String authToken,
                    String davHostHREF)
  {
    osAccount = new Account(userId, SYNC_ACCOUNT_TYPE);

    this.userId      = userId;
    this.authToken   = authToken;
    this.davHostHREF = davHostHREF;
  }

  public String getUserId() {
    return userId;
  }

  public String getAuthToken() {
    return authToken;
  }

  public String getDavHostHREF() {
    return davHostHREF;
  }

  public Optional<String> getCardDavCollectionPath(Context context) {
    SharedPreferences preferences =
        context.getSharedPreferences(SYNC_ACCOUNT_TYPE, Context.MODE_PRIVATE);

    return Optional.fromNullable(preferences.getString(KEY_CARD_DAV_COLLECTION, null));
  }

  public void setCardDavCollection(Context context, String path) {
    SharedPreferences preferences =
        context.getSharedPreferences(SYNC_ACCOUNT_TYPE, Context.MODE_PRIVATE);

    preferences.edit().putString(KEY_CARD_DAV_COLLECTION, path).apply();
  }

  public Account getOsAccount() {
    return osAccount;
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();

    bundle.putString(KEY_USER_ID,       userId);
    bundle.putString(KEY_AUTH_TOKEN,    authToken);
    bundle.putString(KEY_DAV_HOST_HREF, davHostHREF);

    return bundle;
  }

  public static Optional<DavAccount> build(Bundle bundledAccount) {
    if (bundledAccount == null || bundledAccount.getString(KEY_USER_ID) == null)
      return Optional.absent();

    return Optional.of(new DavAccount(
        bundledAccount.getString(KEY_USER_ID),
        bundledAccount.getString(KEY_AUTH_TOKEN),
        bundledAccount.getString(KEY_DAV_HOST_HREF)
    ));
  }

}
