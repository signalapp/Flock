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

/**
 * Programmer: rhodey
 */

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.anhonesteffort.flock.R;
import org.anhonesteffort.flock.SetupActivity;


public class AccountAuthenticator extends AbstractAccountAuthenticator {

  private static final String PREFERENCES_NAME          = "AccountAuthenticator-Preferences";
  public  static final String KEY_ALLOW_ACCOUNT_REMOVAL = "AccountAuthenticator.KEY_ALLOW_ACCOUNT_REMOVAL";

  private Context context;

  public AccountAuthenticator(Context context) {
    super(context);
    this.context = context;
  }

  @Override
  public String getAuthTokenLabel(String authTokenType) {
    return context.getString(R.string.app_name);
  }

  public static void setAllowAccountRemoval(Context context, boolean isAllowed) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME,
                                                              Context.MODE_MULTI_PROCESS);
    settings.edit().putBoolean(KEY_ALLOW_ACCOUNT_REMOVAL, isAllowed).commit();
  }

  @Override
  public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account) {
    SharedPreferences settings  = context.getSharedPreferences(PREFERENCES_NAME,
                                                               Context.MODE_MULTI_PROCESS);
    Boolean           isAllowed = settings.getBoolean(KEY_ALLOW_ACCOUNT_REMOVAL, false);

    Bundle resultBundle = new Bundle();
    resultBundle.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, isAllowed);
    return resultBundle;
  }

  @Override
  public Bundle addAccount(AccountAuthenticatorResponse response,
                           String                       accountType,
                           String                       authTokenType,
                           String[]                     requiredFeatures,
                           Bundle                       options)
      throws NetworkErrorException
  {
    Bundle promptUserBundle = new Bundle();
    Intent promptUserIntent = new Intent(context, SetupActivity.class);

    promptUserIntent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    promptUserBundle.putParcelable(AccountManager.KEY_INTENT, promptUserIntent);
    return promptUserBundle;
  }

  @Override
  public Bundle getAuthToken(AccountAuthenticatorResponse response,
                             Account                      account,
                             String                       authTokenType,
                             Bundle                       options)
      throws NetworkErrorException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle updateCredentials(AccountAuthenticatorResponse response,
                                  Account                      account,
                                  String                       authTokenType,
                                  Bundle                       options)
      throws NetworkErrorException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle hasFeatures(AccountAuthenticatorResponse response,
                            Account                      account,
                            String[]                     features)
      throws NetworkErrorException
  {
    Bundle result = new Bundle();
    result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
    return result;
  }

  @Override
  public Bundle confirmCredentials(AccountAuthenticatorResponse response,
                                   Account                      account,
                                   Bundle                       options)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle editProperties(AccountAuthenticatorResponse response,
                               String                       accountType)
  {
    throw new UnsupportedOperationException();
  }

}
