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
import android.accounts.AccountManagerFuture;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.AccountAuthenticator;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore;
import org.anhonesteffort.flock.sync.addressbook.LocalContactCollection;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;

import java.io.IOException;
import java.util.List;
import javax.net.ssl.SSLException;

/**
 * Programmer: rhodey
 */
public class UnregisterAccountActivity extends AccountAndKeyRequiredActivity {

  private static final String TAG = "org.anhonesteffort.flock.DeleteAccountActivity";

  private AlertDialog alertDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!accountAndKeyAvailableAndMigrationComplete())
      return;

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.unregister_account);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_unregister_account);

    initButtons();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        break;
    }

    return false;
  }

  private void initButtons() {
    findViewById(R.id.button_delete_account).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        EditText cipherPassphrase = (EditText) findViewById(R.id.cipher_passphrase);

        Optional<String> storedPassphrase = KeyStore.getMasterPassphrase(getBaseContext());
        if (cipherPassphrase.getText() != null &&
            !cipherPassphrase.getText().toString().equals(storedPassphrase.get()))
        {
          Toast.makeText(getBaseContext(),
                         R.string.error_invalid_encryption_password,
                         Toast.LENGTH_LONG).show();
        }
        else
          handleRemoveAccount();
      }

    });
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.d(TAG, "onPause()");

    if (alertDialog != null)
      alertDialog.dismiss();
  }

  private void handleAccountRemoved() {
    Log.d(TAG, "handleAccountRemoved()");

    new KeySyncScheduler(getBaseContext()).onAccountRemoved();
    new AddressbookSyncScheduler(getBaseContext()).onAccountRemoved();
    new CalendarsSyncScheduler(getBaseContext()).onAccountRemoved();

    Toast.makeText(getBaseContext(),
                   R.string.account_has_been_unregistered,
                   Toast.LENGTH_SHORT).show();

    finish();
  }

  private void handleRemoveAccount() {
    Log.d(TAG, "handleRemoveAccount()");

    AlertDialog.Builder builder = new AlertDialog.Builder(UnregisterAccountActivity.this);

    builder.setTitle(R.string.title_unregister_account);
    builder.setMessage(R.string.are_you_sure_you_want_to_unregister_your_account);
    builder.setNegativeButton(R.string.cancel, null);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        removeAccountAsync();
      }

    });

    alertDialog = builder.show();
  }

  private void removeAccountAsync() {
    new AsyncTask<String, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "removeAccountAsync()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      private void handleAddressbookCleanup() {
        LocalAddressbookStore        store       = new LocalAddressbookStore(getBaseContext(), account);
        List<LocalContactCollection> collections = store.getCollections();

        for (LocalContactCollection collection : collections)
          collection.setCTag(null);

        account.setCardDavCollection(getBaseContext(), null);
      }

      private void handleRemoveAccountFromDevice(Bundle result) {
        Log.d(TAG, "handleRemoveAccountFromDevice()");

        AccountAuthenticator.setAllowAccountRemoval(getBaseContext(), true);
        AccountManagerFuture<Boolean> future =
            AccountManager.get(getBaseContext()).removeAccount(account.getOsAccount(), null, null);

        try {

          if (!future.getResult()) {
            Log.e(TAG, "I don't know what android did");
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_ACCOUNT_MANAGER_ERROR);
          }

          else {

            handleAddressbookCleanup();
            DavAccountHelper.invalidateAccount(getBaseContext());
            KeyStore.invalidateKeyMaterial(getBaseContext());
            PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit().clear().commit();

            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          }

        } catch (Exception e) {
          Log.e(TAG, "I don't know what android did", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_ACCOUNT_MANAGER_ERROR);
        }
      }

      private void handleRemoveAccountFromServer(Bundle result) {
        try {

          RegistrationApi registrationApi = new RegistrationApi(getBaseContext());
          registrationApi.deleteAccount(account);

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (RegistrationApiException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle   result           = new Bundle();
        String   cipherPassphrase = ((TextView)findViewById(R.id.cipher_passphrase)).getText().toString().trim();

        Optional<String> storedPassphrase = KeyStore.getMasterPassphrase(getBaseContext());
        if (!cipherPassphrase.equals(storedPassphrase.get())) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_INVALID_CIPHER_PASSPHRASE);
          return result;
        }

        handleRemoveAccountFromServer(result);
        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleRemoveAccountFromDevice(result);

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleAccountRemoved();
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);

        ((TextView)findViewById(R.id.cipher_passphrase)).setText("");
      }
    }.execute();
  }
}
