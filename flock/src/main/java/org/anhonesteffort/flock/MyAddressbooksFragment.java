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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.HidingCardDavCollection;
import org.anhonesteffort.flock.sync.addressbook.HidingCardDavStore;
import org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.SSLException;

/**
 * Programmer: rhodey
 * Date: 3/14/14
 */
public class MyAddressbooksFragment extends AbstractMyCollectionsFragment {
  
  private static final String TAG = "org.anhonesteffort.flock.MyAddressbooksFragment";

  protected void handleButtonNext() {
    setupActivity.get().updateFragmentUsingState(SetupActivity.STATE_SELECT_REMOTE_CALENDARS);
  }

  @Override
  protected void handleHideOptionsMenuItems(Menu menu) { }

  @Override
  protected void handleRestoreOptionsMenuItems(Menu menu) { }

  @Override
  protected String getStringCollectionsSelected() {
    return getString(R.string.addressbooks_selected);
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = mode.getMenuInflater();
    inflater.inflate(R.menu.collection_list_edit, menu);
    mode.setTitle(getString(R.string.title_edit_selected_addressbooks));
    mode.setSubtitle(batchSelections.size() + " " + getString(R.string.addressbooks_selected));

    return true;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    switch (item.getItemId()) {

      case R.id.edit_collection_button:
        handleEditSelectedAddressbook();
        break;

      case R.id.delete_collection_button:
        handleDeletebatchSelections();
        break;

    }
    return false;
  }

  private void handleRemoteAddressbooksRetrieved(List<HidingCardDavCollection> remoteAddressbooks) {
    Log.d(TAG, "handleRemoteAddressbooksRetrieved()");

    HidingCardDavCollection[] addressbookArray = new HidingCardDavCollection[remoteAddressbooks.size()];
    for (int i = 0; i < addressbookArray.length; i++)
      addressbookArray[i] = remoteAddressbooks.get(i);

    LocalAddressbookStore localStore = new LocalAddressbookStore(getActivity(), account);
    RemoteAddressbookListAdapter addressbookListAdapter =
        new RemoteAddressbookListAdapter(getActivity(), addressbookArray, localStore, batchSelections);

    collectionsListView = (ListView) getView().findViewById(R.id.list);
    collectionsListView.setAdapter(addressbookListAdapter);
    collectionsListView.setOnItemClickListener(this);

    if (!setupActivity.isPresent())
      collectionsListView.setOnItemLongClickListener(this);

    list_is_initializing = false;
    updateActionMode();
  }

  private void handleEditSelectedAddressbook() {
    Log.d(TAG, "handleEditSelectedAddressbook()");

    if (!getIsFlockCollectionForSelectedCollection()) {
      Toast.makeText(getActivity(),
                     R.string.you_cannot_edit_addressbooks_that_have_not_been_synced,
                     Toast.LENGTH_SHORT).show();
      initializeList();
      return;
    }

          LayoutInflater      inflater        = getActivity().getLayoutInflater();
          View                dialogView      = inflater.inflate(R.layout.dialog_addressbook_edit, null);
    final EditText            displayNameEdit = (EditText) dialogView.findViewById(R.id.dialog_display_name);
          Optional<String>    displayName     = getDisplayNameForSelectedCollection();
          AlertDialog.Builder builder         = new AlertDialog.Builder(getActivity());

    if (displayName.isPresent())
      displayNameEdit.setText(displayName.get());

    builder.setView(dialogView).setTitle(R.string.title_addressbook_properties);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        if (TextUtils.isEmpty(displayNameEdit.getText())) {
          Toast.makeText(getActivity(),
                         R.string.display_name_cannot_be_empty,
                         Toast.LENGTH_LONG).show();
        }
        else {
          editAddressbookAsync(batchSelections.get(0),
                               displayNameEdit.getText().toString());
        }
      }

    });

    alertDialog = builder.show();
  }

  private void handleDeletebatchSelections() {
    Log.d(TAG, "handleDeleteSelectedAddressbook()");

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

    builder.setTitle(R.string.title_delete_selected_calendars_dialog);
    builder.setMessage(R.string.are_you_sure_you_want_to_delete_selected_addressbooks);
    builder.setNegativeButton(R.string.cancel, null);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        List<String> batchSelectionsCopy = new LinkedList<String>();
        for (String path : batchSelections)
          batchSelectionsCopy.add(path);

        deleteAddressbooksAsync(batchSelectionsCopy);
      }

    });

    alertDialog = builder.show();
  }

  private void editAddressbookAsync(final String remotePath,
                                    final String newDisplayName)
  {
    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "editAddressbookAsync()");

        handleStartIndeterminateProgress();
        if (actionMode != null)
          actionMode.finish();
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        try {

          HidingCardDavStore remoteStore =
              DavAccountHelper.getHidingCardDavStore(getActivity(), account, masterCipher);
          Optional<HidingCardDavCollection> remoteAddressbook = remoteStore.getCollection(remotePath);

          if (remoteAddressbook.isPresent()) {
            remoteAddressbook.get().setHiddenDisplayName(newDisplayName);
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          }
          else {
            Log.e(TAG, "remote addressbook at " + remotePath + " is missing");
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_DAV_SERVER_ERROR);
          }

          remoteStore.releaseConnections();

        } catch (DavException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (GeneralSecurityException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));

        batchSelections.clear();
        handleStopIndeterminateProgress();

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          initializeList();
          new AddressbookSyncScheduler(getActivity()).requestSync();
        }
        else
          ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      }
    }.execute();
  }

  private void deleteAddressbooksAsync(final List<String> batchSelectionsCopy) {
    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "deleteAddressbooksAsync()");

        handleStartIndeterminateProgress();
        if (actionMode != null)
          actionMode.finish();
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        try {

          HidingCardDavStore remoteStore =
              DavAccountHelper.getHidingCardDavStore(getActivity(), account, masterCipher);

          LocalAddressbookStore localStore =
              new LocalAddressbookStore(getActivity(), account);

          for (String remotePath : batchSelectionsCopy) {
            Log.w(TAG, "deleting remote addressbook at " + remotePath);
            remoteStore.removeCollection(remotePath);

            Log.w(TAG, "deleting local addressbook at " + remotePath);
            localStore.removeCollection(remotePath);
          }
          remoteStore.releaseConnections();

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (DavException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));

        batchSelections.clear();
        handleStopIndeterminateProgress();

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          initializeList();
        else
          ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      }
    }.execute();
  }

  @Override
  protected void retrieveRemoteCollectionsAsync() {
    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      private List<HidingCardDavCollection> remoteAddressbooks =
          new LinkedList<HidingCardDavCollection>();

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "RetrieveAddressbooksTask - onPreExecute()");
        handleStartIndeterminateProgress();
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        try {

          HidingCardDavStore remoteStore =
              DavAccountHelper.getHidingCardDavStore(getActivity(), account, masterCipher);
          remoteAddressbooks = remoteStore.getCollections();
          remoteStore.releaseConnections();

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (PropertyParseException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (DavException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));
        handleStopIndeterminateProgress();

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleRemoteAddressbooksRetrieved(remoteAddressbooks);
        else
          ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      }
    }.execute();
  }
}
