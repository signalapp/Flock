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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.OperationApplicationException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore;
import org.anhonesteffort.flock.sync.addressbook.LocalContactCollection;

/**
 * rhodey
 */
public class DeleteAllContactsActivity extends AccountAndKeyRequiredActivity {

  private static final String TAG = "org.anhonesteffort.flock.DeleteAllContactsActivity";

  private AlertDialog alertDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!accountAndKeyAvailableAndMigrationComplete())
      return;

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.delete_all_contacts);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_delete_all_contacts);

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
    findViewById(R.id.button_delete_all_contacts).setOnClickListener(new View.OnClickListener() {

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
          handleDeleteAllContacts();
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

  private void handleAllContactsDeleted() {
    Log.d(TAG, "handleAllContactsDeleted()");

    Toast.makeText(getBaseContext(),
                   R.string.all_contacts_have_been_deleted,
                   Toast.LENGTH_SHORT).show();

    finish();
  }

  private void handleDeleteAllContacts() {
    Log.d(TAG, "handleDeleteAllContacts()");

    AlertDialog.Builder builder = new AlertDialog.Builder(DeleteAllContactsActivity.this);
    builder.setTitle(R.string.title_delete_all_contacts);
    builder.setMessage(R.string.are_you_sure_you_want_to_delete_all_flock_contacts);
    builder.setNegativeButton(R.string.cancel, null);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        deleteAllContactsAsync();
      }

    });

    alertDialog = builder.show();
  }

  private void deleteAllContactsAsync() {
    new AsyncTask<String, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "deleteAllContactsAsync()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      private void handleDeleteAllLocalContacts(Bundle result) {
        LocalAddressbookStore localStore = new LocalAddressbookStore(getBaseContext(), account);

        for (LocalContactCollection localCollection : localStore.getCollections()) {
          try {

            Log.w(TAG, "removing all components from local contact collection " + localCollection.getPath());
            localCollection.removeAllComponents();
            localCollection.commitPendingOperations();

          } catch (RemoteException e) {
            ErrorToaster.handleBundleError(e, result);
          } catch (OperationApplicationException e) {
            ErrorToaster.handleBundleError(e, result);
          }
        }
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result = new Bundle();

        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
        handleDeleteAllLocalContacts(result);

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleAllContactsDeleted();
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);

        ((TextView)findViewById(R.id.cipher_passphrase)).setText("");
      }
    }.execute();
  }

}
