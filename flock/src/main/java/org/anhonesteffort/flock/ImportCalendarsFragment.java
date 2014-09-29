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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.chiralcode.colorpicker.ColorPicker;
import com.google.common.base.Optional;

import org.anhonesteffort.flock.sync.calendar.LocalCalendarStore;
import org.anhonesteffort.flock.sync.calendar.LocalEventCollection;

import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class ImportCalendarsFragment extends AccountAndKeyRequiredFragment
    implements CompoundButton.OnCheckedChangeListener
{
  private static final String TAG = "org.anhonesteffort.flock.ImportCalendarsFragment";

  private AsyncTask     asyncTask;
  private ListView      localCalendarListView;
  private SetupActivity setupActivity;
  private AlertDialog   alertDialog;

  private List<LocalEventCollection> selectedCalendars;
  private List<CalendarForCopy>      calendarsForCopyService = new LinkedList<CalendarForCopy>();
  private boolean                    list_is_initializing    = false;
  private int                        indexOfCalendarToPrompt = -1;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof SetupActivity)
      this.setupActivity = (SetupActivity) activity;
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    View fragmentView = inflater.inflate(R.layout.fragment_simple_list, container, false);

    if (!accountAndKeyAvailableAndMigrationComplete())
      return fragmentView;

    initButtons();

    return fragmentView;
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!accountAndKeyAvailableAndMigrationComplete())
      return ;

    initializeList();
  }

  @Override
  public void onPause() {
    super.onPause();

    if (alertDialog != null)
      alertDialog.dismiss();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);
  }

  private void handleBackgroundImportStarted() {
    Log.d(TAG, "handleBackgroundImportStarted()");

    if (selectedCalendars.size() > 0) {
      String toastMessage = getString(R.string.started_background_import_of_calendars, selectedCalendars.size());
      Toast.makeText(getActivity(), toastMessage, Toast.LENGTH_SHORT).show();
    }

    if (setupActivity != null)
      setupActivity.updateFragmentUsingState(SetupActivity.STATE_SELECT_REMOTE_ADDRESSBOOK);
    else
      getActivity().finish();
  }

  private void handlePromptComplete(LocalEventCollection importCalendar,
                                    String               calendarName,
                                    int                  calendarColor)
  {
    try {

      calendarsForCopyService.add(
          new CalendarForCopy(
              importCalendar.getAccount(),
              importCalendar.getLocalId(),
              calendarName,
              calendarColor,
              importCalendar.getComponentIds().size()
          )
      );

      indexOfCalendarToPrompt++;
      handlePromptForNextCalendarNameAndColors();

    } catch (RemoteException e) {
      indexOfCalendarToPrompt = -1;
      selectedCalendars.clear();
      initializeList();
    }
  }

  private void handlePromptForCalendar(final LocalEventCollection importCalendar) throws RemoteException {
          LayoutInflater      inflater        = getActivity().getLayoutInflater();
          View                view            = inflater.inflate(R.layout.dialog_calendar_edit, null);
    final EditText            displayNameEdit = (EditText   ) view.findViewById(R.id.dialog_display_name);
    final ColorPicker         colorPicker     = (ColorPicker) view.findViewById(R.id.dialog_calendar_color);
          AlertDialog.Builder builder         = new AlertDialog.Builder(getActivity());

    Optional<String> displayName = importCalendar.getDisplayName();
    if (displayName.isPresent())
      displayNameEdit.setText(displayName.get());

    Optional<Integer> color = importCalendar.getColor();
    if (color.isPresent())
      colorPicker.setColor(color.get());

    builder.setView(view).setTitle(R.string.title_calendar_properties);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        if (TextUtils.isEmpty(displayNameEdit.getText().toString())) {
          Toast.makeText(getActivity(),
                         R.string.display_name_cannot_be_empty,
                         Toast.LENGTH_LONG).show();
        }
        else {
          handlePromptComplete(importCalendar,
                               displayNameEdit.getText().toString(),
                               colorPicker.getColor());
        }
      }

    });

    builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {
        indexOfCalendarToPrompt = -1;
        selectedCalendars.clear();
        initializeList();
      }

    });

    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        indexOfCalendarToPrompt = -1;
        selectedCalendars.clear();
        initializeList();
      }
    });

    alertDialog = builder.show();
  }

  private void handlePromptForNextCalendarNameAndColors() {
    Log.d(TAG, "handlePromptForNextCalendarNameAndColors()");

    if (indexOfCalendarToPrompt == -1)
      indexOfCalendarToPrompt = 0;

    try {

      if (indexOfCalendarToPrompt < selectedCalendars.size())
        handlePromptForCalendar(selectedCalendars.get(indexOfCalendarToPrompt));
      else if (calendarsForCopyService.size() > 0) {
        handleStartBackgroundCopyService(calendarsForCopyService);
        handleBackgroundImportStarted();
      }

    } catch (RemoteException e) {
      indexOfCalendarToPrompt = -1;
      selectedCalendars.clear();
      initializeList();
    }
  }

  private void initButtons() {
    Button actionButton;

    if (setupActivity != null) {
      actionButton = (Button) getActivity().findViewById(R.id.button_next);
      actionButton.setText(R.string.skip);
    }
    else {
      actionButton = (Button) getActivity().findViewById(R.id.button_action);
      actionButton.setText(R.string.cancel);
    }

    actionButton.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View view) {
        if (selectedCalendars.size() > 0)
          handlePromptForNextCalendarNameAndColors();
        else
          handleBackgroundImportStarted();
      }

    });
  }

  private void initializeList() {
    Log.d(TAG, "initializeList()");

    if (list_is_initializing)
      return;

    selectedCalendars       = new LinkedList<LocalEventCollection>();
    list_is_initializing    = true;
    calendarsForCopyService = new LinkedList<CalendarForCopy>();
    indexOfCalendarToPrompt = -1;

    asyncTask = new RetrieveCalendarsTask().execute();
  }

  @Override
  public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
    Button actionButton;

    if (setupActivity != null)
      actionButton = (Button) getActivity().findViewById(R.id.button_next);
    else
      actionButton = (Button) getActivity().findViewById(R.id.button_action);

    if (selectedCalendars.size() > 0 && setupActivity != null)
      actionButton.setText(R.string.next);
    else if (selectedCalendars.size() > 0 && setupActivity == null)
      actionButton.setText(R.string.button_import);
    else if (setupActivity != null)
      actionButton.setText(R.string.skip);
    else
      actionButton.setText(R.string.cancel);
  }

  private void handleLocalCalendarsRetrieved(List<LocalEventCollection> localCalendars) {
    Log.d(TAG, " handleLocalCalendarsRetrieved()");
    LocalEventCollection[] localCalendarArray = new  LocalEventCollection[localCalendars.size()];
    for (int i = 0; i < localCalendars.size(); i++)
      localCalendarArray[i] = localCalendars.get(i);

    LocalCalendarListAdapter listAdapter =
        new LocalCalendarListAdapter(getActivity().getBaseContext(), localCalendarArray, selectedCalendars, this);

    localCalendarListView = (ListView)getView().findViewById(R.id.list);
    localCalendarListView.setAdapter(listAdapter);
    list_is_initializing = false;
  }

  private void handleStartBackgroundCopyService(final List<CalendarForCopy> copyCalendars) {

    final Context hackContext = getActivity().getApplicationContext();

    new AsyncTask<String, Void, Bundle>() {

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result      = new Bundle();
        Intent copyService = new Intent(hackContext, CalendarCopyService.class);

        for (CalendarForCopy copyCalendar : copyCalendars) {
          copyService.setAction(CalendarCopyService.ACTION_QUEUE_ACCOUNT_FOR_COPY);
          copyService.putExtra(CalendarCopyService.KEY_FROM_ACCOUNT,   copyCalendar.fromAccount);
          copyService.putExtra(CalendarCopyService.KEY_TO_ACCOUNT,     account.getOsAccount());
          copyService.putExtra(CalendarCopyService.KEY_CALENDAR_ID,    copyCalendar.calendarId);
          copyService.putExtra(CalendarCopyService.KEY_CALENDAR_NAME,  copyCalendar.calendarName);
          copyService.putExtra(CalendarCopyService.KEY_CALENDAR_COLOR, copyCalendar.calendarColor);
          copyService.putExtra(CalendarCopyService.KEY_EVENT_COUNT,    copyCalendar.eventCount);

          hackContext.startService(copyService);
        }

        copyService.setAction(CalendarCopyService.ACTION_START_COPY);
        hackContext.startService(copyService);
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));
        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS)
          ErrorToaster.handleDisplayToastBundledError(hackContext, result);
      }

    }.execute();
  }

  private class RetrieveCalendarsTask extends AsyncTask<Void, Void, Bundle> {

    private List<LocalEventCollection> localCalendars;

    @Override
    protected void onPreExecute() {
      Log.d(TAG, "RetrieveCalendarsTask()");
      getActivity().setProgressBarIndeterminateVisibility(true);
      getActivity().setProgressBarVisibility(true);
    }

    protected List<LocalEventCollection> getLocalCalendars(List<Account> localAccounts)
      throws RemoteException
    {
      List<LocalEventCollection> collections = new LinkedList<LocalEventCollection>();

      for (Account localAccount : localAccounts) {
        LocalCalendarStore localStore = new LocalCalendarStore(getActivity(), localAccount);
        collections.addAll(localStore.getCollectionsIgnoreSync());
      }

      return collections;
    }

    protected List<Account> getOtherAccounts() {
      List<Account> accounts = new LinkedList<Account>();

      for (Account osAccount : AccountManager.get(getActivity()).getAccounts()) {
        if (!osAccount.name.equals(account.getOsAccount().name) &&
            !osAccount.type.equals(account.getOsAccount().type))
        {
          accounts.add(osAccount);
        }
      }

      return accounts;
    }

    @Override
    protected Bundle doInBackground(Void... params) {
      Bundle        result        = new Bundle();
      List<Account> otherAccounts = getOtherAccounts();

      try {

        localCalendars = getLocalCalendars(otherAccounts);

        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

      } catch (RemoteException e) {
        ErrorToaster.handleBundleError(e, result);
      }

      return result;
    }

    @Override
    protected void onPostExecute(Bundle result) {
      Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));
      getActivity().setProgressBarIndeterminateVisibility(false);
      getActivity().setProgressBarVisibility(false);

      if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
        handleLocalCalendarsRetrieved(localCalendars);
      else
        ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
    }
  }

  private static class CalendarForCopy {

    public Account fromAccount;
    public long    calendarId;
    public String  calendarName;
    public int     calendarColor;
    public int     eventCount;

    public CalendarForCopy(Account fromAccount,
                           long    calendarId,
                           String  calendarName,
                           int     calendarColor,
                           int     eventCount)
    {
      this.fromAccount   = fromAccount;
      this.calendarId    = calendarId;
      this.calendarName  = calendarName;
      this.calendarColor = calendarColor;
      this.eventCount    = eventCount;
    }

  }

}
