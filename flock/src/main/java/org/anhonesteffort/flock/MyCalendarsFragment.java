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
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.chiralcode.colorpicker.ColorPicker;
import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavStore;
import org.anhonesteffort.flock.sync.calendar.LocalCalendarStore;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.SSLException;

/**
 * Programmer: rhodey
 */
public class MyCalendarsFragment extends AbstractMyCollectionsFragment
    implements View.OnClickListener
{

  private static final String TAG = "org.anhonesteffort.flock.MyCalendarsFragment";

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    View fragmentView = super.onCreateView(inflater, container, savedInstanceState);

    if (account != null && DavAccountHelper.isUsingOurServers(account))
      fragmentView.findViewById(R.id.list_heading_sync).setVisibility(View.GONE);

    return fragmentView;
  }

  protected String getStringCollectionsSelected() {
    return getString(R.string.calendars_selected);
  }

  protected void handleButtonNext() {
    setupActivity.get().handleSetupComplete();
  }

  @Override
  protected void handleHideOptionsMenuItems(Menu menu) {
    if (menu.findItem(R.id.create_collection_button) == null)
      return;

    menu.findItem(R.id.create_collection_button).setVisible(false);
  }

  @Override
  protected void handleRestoreOptionsMenuItems(Menu menu) {
    if (menu.findItem(R.id.create_collection_button) == null)
      activity.getMenuInflater().inflate(R.menu.calendar_list_browse, menu);

    menu.findItem(R.id.create_collection_button).setVisible(true);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {

      case R.id.create_collection_button:
        handleCreateNewCalendar();
        break;

    }

    return true;
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = mode.getMenuInflater();
    inflater.inflate(R.menu.collection_list_edit, menu);
    mode.setTitle(getString(R.string.title_edit_selected_calendars));
    mode.setSubtitle(batchSelections.size() + " " + getString(R.string.calendars_selected));

    return true;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    switch (item.getItemId()) {

      case R.id.edit_collection_button:
        handleEditSelectedCalendar();
        break;

      case R.id.delete_collection_button:
        handleDeleteSelectedCalendars();
        break;

    }
    return false;
  }

  @Override
  public void onClick(View calendarColorView) {
    if (batchSelections.size() == 0) {
      String collectionPath = (String) calendarColorView.getTag(R.integer.tag_collection_path);

      for(int i = 0; i < collectionsListView.getChildCount(); i++) {
        View rowView = collectionsListView.getChildAt(i);

        if (rowView.getTag(R.integer.tag_collection_path).equals(collectionPath)) {
          handleSelectRow(rowView);
          handleEditSelectedCalendar();
          break;
        }
      }
    }
  }

  private void handleRemoteCalendarsRetrieved(List<HidingCalDavCollection> remoteCalendars) {
    Log.d(TAG, "handleRemoteCalendarsRetrieved()");

    HidingCalDavCollection[] remoteCalendarArray = new HidingCalDavCollection[remoteCalendars.size()];
    for (int i = 0; i < remoteCalendarArray.length; i++)
      remoteCalendarArray[i] = remoteCalendars.get(i);

    LocalCalendarStore localCalendarStore  = new LocalCalendarStore(activity, account.getOsAccount());
    RemoteCalendarListAdapter calendarListAdapter =
        new RemoteCalendarListAdapter(activity,
                                      !DavAccountHelper.isUsingOurServers(account),
                                      remoteCalendarArray,
                                      localCalendarStore,
                                      batchSelections,
                                      this);

    collectionsListView = (ListView) activity.findViewById(R.id.list);
    collectionsListView.setAdapter(calendarListAdapter);
    collectionsListView.setOnItemClickListener(this);

    if (!setupActivity.isPresent())
      collectionsListView.setOnItemLongClickListener(this);

    list_is_initializing = false;
    updateActionMode();
  }

  private void handleCreateNewCalendar() {
          LayoutInflater      inflater    = activity.getLayoutInflater();
    final View                view        = inflater.inflate(R.layout.dialog_calendar_edit, null);
    final ColorPicker         colorPicker = (ColorPicker) view.findViewById(R.id.dialog_calendar_color);
          AlertDialog.Builder builder     = new AlertDialog.Builder(activity);
          SharedPreferences   settings    = PreferenceManager.getDefaultSharedPreferences(activity);

    colorPicker.setColor(settings.getInt(PreferencesActivity.KEY_PREF_DEFAULT_CALENDAR_COLOR,
                                         getResources().getColor(R.color.flocktheme_color)));
    builder.setView(view).setTitle(R.string.title_calendar_properties);

    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        EditText displayNameEdit = (EditText) view.findViewById(R.id.dialog_display_name);
        createCalendarAsync(displayNameEdit.getText().toString(), colorPicker.getColor());
      }

    });

    alertDialog = builder.show();
  }

  private int getColorForSelectedCalendar() {
    if (batchSelections.size() == 0)
      return getResources().getColor(R.color.flocktheme_color);

    for(int i = 0; i < collectionsListView.getChildCount(); i++) {
      View    rowView   = collectionsListView.getChildAt(i);
      View    colorView = rowView.findViewById(R.id.calendar_color);
      Boolean selected  = (Boolean) rowView.getTag(R.integer.tag_collection_selected);

      if (selected && colorView.getTag(R.integer.tag_calendar_color) != null)
        return (Integer) colorView.getTag(R.integer.tag_calendar_color);
    }

    return getResources().getColor(R.color.flocktheme_color);
  }

  private void handleEditSelectedCalendar() {
    Log.d(TAG, "handleEditSelectedCalendar()");

    if (!getIsFlockCollectionForSelectedCollection()) {
      Toast.makeText(getActivity(),
                     R.string.you_cannot_edit_calendars_that_have_not_been_synced,
                     Toast.LENGTH_SHORT).show();
      initializeList();
      return;
    }

          LayoutInflater      inflater        = activity.getLayoutInflater();
          View                view            = inflater.inflate(R.layout.dialog_calendar_edit, null);
    final EditText            displayNameEdit = (EditText   ) view.findViewById(R.id.dialog_display_name);
    final ColorPicker         colorPicker     = (ColorPicker) view.findViewById(R.id.dialog_calendar_color);
          AlertDialog.Builder builder         = new AlertDialog.Builder(activity);
          Optional<String>    displayName     = getDisplayNameForSelectedCollection();

    if (displayName.isPresent())
      displayNameEdit.setText(displayName.get());

    colorPicker.setColor(getColorForSelectedCalendar());
    builder.setView(view).setTitle(R.string.title_calendar_properties);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        if (TextUtils.isEmpty(displayNameEdit.getText().toString())) {
          Toast.makeText(activity,
              R.string.display_name_cannot_be_empty,
              Toast.LENGTH_LONG).show();
        }
        else {
          editCalendarAsync(batchSelections.get(0),
                            displayNameEdit.getText().toString(),
                            colorPicker.getColor());
        }
      }

    });

    alertDialog = builder.show();
  }

  private void handleDeleteSelectedCalendars() {
    Log.d(TAG, "handleDeleteSelectedCalendars()");

    AlertDialog.Builder builder = new AlertDialog.Builder(activity);

    builder.setTitle(R.string.title_delete_selected_calendars_dialog);
    builder.setMessage(R.string.are_you_sure_you_want_to_delete_selected_calendars);
    builder.setNegativeButton(R.string.cancel, null);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        List<String> batchSelectionsCopy = new LinkedList<String>();
        for (String path : batchSelections)
          batchSelectionsCopy.add(path);

        deleteCalendarsAsync(batchSelectionsCopy);
      }

    });

    alertDialog = builder.show();
  }

  private void createCalendarAsync(final String displayName, final int color) {
    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "createCalendarAsync()");

        handleStartIndeterminateProgress();
        if (actionMode != null)
          actionMode.finish();
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result       = new Bundle();
        String calendarUUID = UUID.randomUUID().toString();

        try {

          HidingCalDavStore remoteStore =
              DavAccountHelper.getHidingCalDavStore(activity, account, masterCipher);
          Optional<String> calendarHomeSet = remoteStore.getCalendarHomeSet();

          if (!calendarHomeSet.isPresent()) {
            Log.e(TAG, "remote calendar store is missing calendar home set");
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_DAV_SERVER_ERROR);
          }

          String calendarRemotePath = calendarHomeSet.get().concat(calendarUUID + "/");
          remoteStore.addCollection(calendarRemotePath, displayName, color);
          remoteStore.releaseConnections();

          LocalCalendarStore localStore = new LocalCalendarStore(activity, account.getOsAccount());
          localStore.addCollection(calendarRemotePath, displayName, color);

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (PropertyParseException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (DavException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (RemoteException e) {
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
        batchSelections.clear();
        handleStopIndeterminateProgress();

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          initializeList();
        else
          ErrorToaster.handleDisplayToastBundledError(activity, result);
      }
    }.execute();
  }

  private void editCalendarAsync(final String remotePath,
                                 final String newDisplayName,
                                 final int    newColor)
  {
    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "editCalendarAsync()");

        handleStartIndeterminateProgress();
        if (actionMode != null)
          actionMode.finish();
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        try {

          HidingCalDavStore remoteStore =
              DavAccountHelper.getHidingCalDavStore(activity, account, masterCipher);
          Optional<HidingCalDavCollection> remoteCalendar = remoteStore.getCollection(remotePath);

          if (remoteCalendar.isPresent()) {
            remoteCalendar.get().setHiddenDisplayName(newDisplayName);
            remoteCalendar.get().setHiddenColor(newColor);
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          }
          else {
            Log.e(TAG, "remote calendar at " + remotePath + " is missing");
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
          new CalendarsSyncScheduler(activity).requestSync();
        }
        else
          ErrorToaster.handleDisplayToastBundledError(activity, result);
      }
    }.execute();
  }

  private void deleteCalendarsAsync(final List<String> batchSelectionsCopy) {
    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "deleteCalendarsAsync()");

        handleStartIndeterminateProgress();
        if (actionMode != null)
          actionMode.finish();
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        try {

          HidingCalDavStore remoteStore =
              DavAccountHelper.getHidingCalDavStore(activity, account, masterCipher);
          LocalCalendarStore localCalendarStore =
              new LocalCalendarStore(activity, account.getOsAccount());

          for (String remotePath : batchSelectionsCopy) {
            Log.w(TAG, "deleting remote calendar at " + remotePath);
            remoteStore.removeCollection(remotePath);

            Log.w(TAG, "deleting local calendar at " + remotePath);
            localCalendarStore.removeCollection(remotePath);
          }

          remoteStore.releaseConnections();
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (DavException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (RemoteException e) {
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
          ErrorToaster.handleDisplayToastBundledError(activity, result);
      }
    }.execute();
  }

  @Override
  protected void retrieveRemoteCollectionsAsync() {
    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      private List<HidingCalDavCollection> remoteCalendars =
          new LinkedList<HidingCalDavCollection>();

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "RetrieveCalendarsTask - onPreExecute()");
        handleStartIndeterminateProgress();
      }

      private List<HidingCalDavCollection> handleRemoveKeyCollection(List<HidingCalDavCollection> collections) {
        Optional<HidingCalDavCollection> keyCollection = Optional.absent();
        for (HidingCalDavCollection collection : collections) {
          if (collection.getPath().contains(DavKeyStore.PATH_KEY_COLLECTION))
            keyCollection = Optional.of(collection);
        }
        if (keyCollection.isPresent())
          collections.remove(keyCollection.get());

        return collections;
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        try {

          HidingCalDavStore remoteCalDavStore =
              DavAccountHelper.getHidingCalDavStore(activity, account, masterCipher);
          remoteCalendars = handleRemoveKeyCollection(remoteCalDavStore.getCollections());
          remoteCalDavStore.releaseConnections();

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
          handleRemoteCalendarsRetrieved(remoteCalendars);
        else
          ErrorToaster.handleDisplayToastBundledError(activity, result);
      }
    }.execute();
  }
}
