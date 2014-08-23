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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.MasterCipher;

import java.io.IOException;

/**
 * Programmer: rhodey
 */
public class AccountAndKeyRequiredActivity extends Activity {

  private static final String TAG = "org.anhonesteffort.flock.AccountAndKeyRequiredActivity";

  protected DavAccount   account;
  protected MasterCipher masterCipher;

  protected static DavAccount handleGetAccountOrFail(Activity activity) {
    Intent               nextIntent;
    Optional<DavAccount> davAccount = DavAccountHelper.getAccount(activity);

    if (!davAccount.isPresent()) {
      if (!DavAccountHelper.isAccountRegistered(activity)) {
        Log.w(TAG, "dav account missing and account not registered, directing to setup activity");
        nextIntent = new Intent(activity, SetupActivity.class);
        nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      }

      else {
        Log.w(TAG, "dav account missing and account is registered, directing to correct password");
        nextIntent = new Intent(activity, CorrectPasswordActivity.class);
        nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Toast.makeText(activity,
                       R.string.error_password_unavailable_please_login,
                       Toast.LENGTH_SHORT).show();
      }

      activity.startActivity(nextIntent);
      activity.finish();
      return null;
    }

    else
      return davAccount.get();
  }

  protected static MasterCipher handleGetMasterCipherOrFail(Activity activity) {
    try {

      Optional<MasterCipher> cipher = KeyHelper.getMasterCipher(activity);
      if (cipher.isPresent())
        return cipher.get();
      else {
        Log.e(TAG, "master cipher is missing, fuck");
        throw new IOException("Where did master chipher GO!?!?");
      }

    } catch (IOException e) {
      // TODO: import account from scratch...
      activity.finish();
      return null;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    account      = handleGetAccountOrFail(this);
    masterCipher = handleGetMasterCipherOrFail(this);
  }

  protected boolean accountAndKeyAvailableAndMigrationComplete() {
    if (MigrationHelperBroadcastReceiver.getUiDisabledForMigration(getBaseContext())) {
      Toast.makeText(getBaseContext(),
                     R.string.migration_in_progress_please_wait,
                     Toast.LENGTH_LONG).show();

      finish();
      return false;
    }

    return account != null && masterCipher != null;
  }

}
