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

package org.anhonesteffort.flock;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.AndroidDavClient;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.sync.addressbook.HidingCardDavStore;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavStore;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.anhonesteffort.flock.webdav.carddav.CardDavStore;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Programmer: rhodey
 */
// TODO: this is getting to be a mess :(
public class DavAccountHelper {

  private static final String PREFERENCES_NAME = "org.anhonesteffort.flock.DavAccountHelper";
  private static final String KEY_DAV_USERNAME = "org.anhonesteffort.flock.auth.AccountAuthenticator.KEY_DAV_USERNAME";
  private static final String KEY_DAV_PASSWORD = "org.anhonesteffort.flock.auth.AccountAuthenticator.KEY_DAV_PASSWORD";
  private static final String KEY_DAV_HOST     = "org.anhonesteffort.flock.auth.AccountAuthenticator.KEY_DAV_HOST";

  private static SharedPreferences getSharedPreferences(Context context) {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
  }

  public static String correctUsername(Context context, String username) {
    if (!username.contains("@"))
      return username.concat(context.getString(R.string.at_flock_sync));

    return username;
  }

  public static boolean isAccountRegistered(Context context) {
    return AccountManager.get(context).getAccountsByType(DavAccount.SYNC_ACCOUNT_TYPE).length > 0;
  }

  public static Optional<String> getAccountUsername(Context context) {
    return Optional.fromNullable(getSharedPreferences(context).getString(KEY_DAV_USERNAME, null));
  }

  public static void setAccountUsername(Context context, String username) {
    getSharedPreferences(context).edit().putString(KEY_DAV_USERNAME, username).commit();
  }

  public static Optional<String> getAccountPassword(Context context) {
    return Optional.fromNullable(getSharedPreferences(context).getString(KEY_DAV_PASSWORD, null));
  }

  public static void setAccountPassword(Context context, String password) {
    getSharedPreferences(context).edit().putString(KEY_DAV_PASSWORD, password).commit();
  }

  public static void invalidateAccountPassword(Context context) {
    getSharedPreferences(context).edit().remove(KEY_DAV_PASSWORD).commit();
  }

  public static Optional<String> getAccountDavHREF(Context context) {
    return Optional.fromNullable(getSharedPreferences(context).getString(KEY_DAV_HOST, null));
  }

  public static void setAccountDavHREF(Context context, String username) {
    getSharedPreferences(context).edit().putString(KEY_DAV_HOST, username).commit();
  }

  public static void invalidateAccount(Context context) {
    getSharedPreferences(context).edit().remove(KEY_DAV_HOST).commit();
    getSharedPreferences(context).edit().remove(KEY_DAV_USERNAME).commit();
    getSharedPreferences(context).edit().remove(KEY_DAV_PASSWORD).commit();
  }

  public static boolean isUsingOurServers(DavAccount account) {
    return account.getDavHostHREF().equals(OwsWebDav.HREF_WEBDAV_HOST);
  }

  public static boolean isUsingOurServers(Context context) {
    return getAccountDavHREF(context).isPresent() &&
           getAccountDavHREF(context).get().equals(OwsWebDav.HREF_WEBDAV_HOST);
  }

  public static Optional<DavAccount> getAccount(Context context) {
    Optional<String> davHREF         = getAccountDavHREF(context);
    Optional<String> accountUsername = getAccountUsername(context);
    Optional<String> accountPassword = getAccountPassword(context);

    if (!isAccountRegistered(context) || !davHREF.isPresent() ||
        !accountUsername.isPresent() || !accountPassword.isPresent())
    {
      return Optional.absent();
    }

    return Optional.of(new DavAccount(accountUsername.get(), accountPassword.get(), davHREF.get()));
  }

  public static AndroidDavClient getAndroidDavClient(Context context, DavAccount account)
    throws MalformedURLException
  {
    URL davHost = new URL(account.getDavHostHREF());

    return new AndroidDavClient(context,
                                davHost,
                                account.getUserId(),
                                account.getAuthToken());
  }

  private static String getOwsCurrentUserPrincipal(DavAccount account) throws IOException {
    return "/principals/__uids__/" + URLEncoder.encode(account.getUserId().toUpperCase(), "UTF8") + "/";
  }

  private static String getOwsCalendarHomeSet(DavAccount account) throws IOException {
    return "/calendars/__uids__/" + URLEncoder.encode(account.getUserId().toUpperCase(), "UTF8") + "/";
  }

  private static String getOwsAddressbookHomeSet(DavAccount account) throws IOException {
    return "/addressbooks/__uids__/" + URLEncoder.encode(account.getUserId().toUpperCase(), "UTF8") + "/";
  }

  public static CardDavStore getCardDavStore(Context context, DavAccount account)
      throws IOException
  {
    if (isUsingOurServers(account))
      return new CardDavStore(getAndroidDavClient(context, account),
                              Optional.of(getOwsCurrentUserPrincipal(account)),
                              Optional.of(getOwsAddressbookHomeSet(account)));

    return new CardDavStore(getAndroidDavClient(context, account),
                            Optional.<String>absent(),
                            Optional.<String>absent());
  }

  public static CalDavStore getCalDavStore(Context context, DavAccount account)
      throws IOException
  {
    if (isUsingOurServers(account))
      return new CalDavStore(getAndroidDavClient(context, account),
                             Optional.of(getOwsCurrentUserPrincipal(account)),
                             Optional.of(getOwsCalendarHomeSet(account)));

    return new CalDavStore(getAndroidDavClient(context, account),
                           Optional.<String>absent(),
                           Optional.<String>absent());
  }

  public static DavKeyStore getDavKeyStore(Context context, DavAccount account)
      throws IOException
  {
    if (isUsingOurServers(account))
      return new DavKeyStore(getAndroidDavClient(context, account),
                             Optional.of(getOwsCurrentUserPrincipal(account)),
                             Optional.of(getOwsCalendarHomeSet(account)));

    return new DavKeyStore(getAndroidDavClient(context, account),
                           Optional.<String>absent(),
                           Optional.<String>absent());
  }

  public static HidingCardDavStore getHidingCardDavStore(Context      context,
                                                         DavAccount   account,
                                                         MasterCipher masterCipher)
    throws IOException
  {
    if (isUsingOurServers(account))
      return new HidingCardDavStore(masterCipher,
                                    getAndroidDavClient(context, account),
                                    Optional.of(getOwsCurrentUserPrincipal(account)),
                                    Optional.of(getOwsAddressbookHomeSet(account)));

    return new HidingCardDavStore(masterCipher,
                                  getAndroidDavClient(context, account),
                                  Optional.<String>absent(),
                                  Optional.<String>absent());
  }

  public static HidingCalDavStore getHidingCalDavStore(Context      context,
                                                       DavAccount   account,
                                                       MasterCipher masterCipher)
      throws IOException
  {
    if (isUsingOurServers(account))
      return new HidingCalDavStore(masterCipher,
                                   getAndroidDavClient(context, account),
                                   Optional.of(getOwsCurrentUserPrincipal(account)),
                                   Optional.of(getOwsCalendarHomeSet(account)));

    return new HidingCalDavStore(masterCipher,
                                 getAndroidDavClient(context, account),
                                 Optional.<String>absent(),
                                 Optional.<String>absent());
  }

  public static boolean isAuthenticated(Context context, DavAccount account)
      throws PropertyParseException, DavException, IOException
  {
    CardDavStore cardDavStore = getCardDavStore(context, account);

    try {

      cardDavStore.getCollections();
      return true;

    } catch (DavException e) {

      if (e.getErrorCode() == OwsWebDav.STATUS_PAYMENT_REQUIRED)
        return true;
      else if (e.getErrorCode() == DavServletResponse.SC_UNAUTHORIZED)
        return false;
      else
        throw e;

    } finally {
      cardDavStore.closeHttpConnection();
    }
  }

  public static boolean isExpired(Context context, DavAccount account)
      throws PropertyParseException, DavException, IOException
  {
    CardDavStore cardDavStore = getCardDavStore(context, account);

    try {

      cardDavStore.getCollections();
      return false;

    } catch (DavException e) {

      if (e.getErrorCode() == OwsWebDav.STATUS_PAYMENT_REQUIRED)
        return true;
      else
        throw e;

    } finally {
      cardDavStore.closeHttpConnection();
    }
  }
}