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
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.anhonesteffort.flock.sync.key.KeySyncUtil;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Programmer: rhodey
 */
public class CorrectEncryptionPasswordActivity extends Activity {

  private static final String TAG = "org.anhonesteffort.flock.CorrectEncryptionPasswordActivity";

  private AsyncTask asyncTask;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.correct_encryption_password);
    getActionBar().setTitle(R.string.title_correct_encryption_password);

    findViewById(R.id.test_master_passphrase_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleTestMasterPassphrase();
      }
    });
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);
  }

  private void handleEncryptionPasswordValid() {
    Log.d(TAG, "handleEncryptionPasswordValid()");

    Toast.makeText(getBaseContext(),
                   R.string.password_corrected,
                   Toast.LENGTH_SHORT).show();

    new KeySyncScheduler(getBaseContext()).requestSync();
    finish();
  }

  private void handleTestMasterPassphrase() {
    asyncTask = new AsyncTask<String, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleTestMasterPassphrase()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result           = new Bundle();
        String masterPassphrase = ((TextView)findViewById(R.id.cipher_passphrase)).getText().toString().trim();

        if (TextUtils.isEmpty(masterPassphrase)) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_INVALID_CIPHER_PASSPHRASE);
          return result;
        }

        KeyStore.saveMasterPassphrase(getBaseContext(), masterPassphrase);

        try {

          if (KeyHelper.masterPassphraseIsValid(getBaseContext())) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
            KeySyncUtil.cancelCipherPassphraseNotification(getBaseContext());
          }
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_INVALID_CIPHER_PASSPHRASE);

        } catch (GeneralSecurityException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          Log.e(TAG, "doInBackground()", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleEncryptionPasswordValid();
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);

        ((TextView) findViewById(R.id.cipher_passphrase)).setText("");
      }

    }.execute();
  }

}
