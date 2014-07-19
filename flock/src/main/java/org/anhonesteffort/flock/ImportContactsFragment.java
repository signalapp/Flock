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
import android.app.ProgressDialog;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Toast;

import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.LocalContactCollection;

import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class ImportContactsFragment extends AccountAndKeyRequiredFragment
    implements CompoundButton.OnCheckedChangeListener
{
  private static final String TAG = "org.anhonesteffort.flock.ImportContactsFragment";

  private AsyncTask     asyncTask;
  private ListView      accountDetailsListView;
  private SetupActivity setupActivity;

  private List<ImportContactsFragment.AccountContactDetails> selectedAccounts;
  private boolean                                            list_is_initializing = false;

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

    if (!accountAndKeyAvailable())
      return fragmentView;

    initButtons();

    return fragmentView;
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!accountAndKeyAvailable())
      return ;

    initializeList();
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);
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
        Intent                       copyService = new Intent(getActivity(), ContactCopyService.class);

        if (selectedAccounts.size() == 0) {
          handleBackgroundImportStarted();
          return;
        }

        for (ImportContactsFragment.AccountContactDetails copyAccount : selectedAccounts) {
          copyService.setAction(ContactCopyService.ACTION_QUEUE_ACCOUNT_FOR_COPY);
          copyService.putExtra(ContactCopyService.KEY_FROM_ACCOUNT,  copyAccount.account);
          copyService.putExtra(ContactCopyService.KEY_TO_ACCOUNT,    account.getOsAccount());
          copyService.putExtra(ContactCopyService.KEY_CONTACT_COUNT, copyAccount.contact_count);

          getActivity().startService(copyService);
        }

        copyService.setAction(ContactCopyService.ACTION_START_COPY);
        getActivity().startService(copyService);
        handleBackgroundImportStarted();
      }

    });
  }

  private void initializeList() {
    Log.d(TAG, "initializeList()");

    if (list_is_initializing)
      return;

    selectedAccounts     = new LinkedList<AccountContactDetails>();
    list_is_initializing = true;
    asyncTask            = new RetrieveAccountContactDetailsTask().execute();
  }

  @Override
  public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
    Button actionButton;

    if (setupActivity != null)
      actionButton = (Button) getActivity().findViewById(R.id.button_next);
    else
      actionButton = (Button) getActivity().findViewById(R.id.button_action);

    if (selectedAccounts.size() > 0 && setupActivity != null)
      actionButton.setText(R.string.next);
    else if (selectedAccounts.size() > 0 && setupActivity == null)
      actionButton.setText(R.string.button_import);
    else if (setupActivity != null)
      actionButton.setText(R.string.skip);
    else
      actionButton.setText(R.string.cancel);
  }

  private void handleBackgroundImportStarted() {
    Log.d(TAG, "handleBackgroundImportStarted()");
    Integer contactCount = 0;
    for (ImportContactsFragment.AccountContactDetails copyAccount : selectedAccounts)
      contactCount += copyAccount.contact_count;

    if (selectedAccounts.size() > 0) {
      String toastMessage = getString(R.string.started_background_import_of) + " " +
                            contactCount + " " + getString(R.string.contacts);
      Toast.makeText(getActivity(), toastMessage, Toast.LENGTH_SHORT).show();
    }

    if (setupActivity != null)
      setupActivity.updateFragmentUsingState(SetupActivity.STATE_IMPORT_CALENDARS);
    else
      getActivity().finish();
  }

  private void handleAccountDetailsRetrieved(List<AccountContactDetails> accountDetails) {
    Log.d(TAG, "handleAccountDetailsRetrieved()");
    AccountContactDetails[] accountDetailsArray = new AccountContactDetails[accountDetails.size()];
    for (int i = 0; i < accountDetails.size(); i++)
      accountDetailsArray[i] = accountDetails.get(i);

    AccountContactDetailsListAdapter listAdapter =
        new AccountContactDetailsListAdapter(getActivity().getBaseContext(), accountDetailsArray, selectedAccounts, this);

    accountDetailsListView = (ListView)getView().findViewById(R.id.list);
    accountDetailsListView.setAdapter(listAdapter);
    list_is_initializing = false;
  }

  private class RetrieveAccountContactDetailsTask extends AsyncTask<Void, Void, Bundle> {

    List<AccountContactDetails> accountDetails = new LinkedList<AccountContactDetails>();

    @Override
    protected void onPreExecute() {
      Log.d(TAG, "RetrieveAccountContactDetailsTask()");
      getActivity().setProgressBarIndeterminateVisibility(true);
      getActivity().setProgressBarVisibility(true);
    }

    protected void populateAccountContactCounts(List<AccountContactDetails> accounts)
        throws RemoteException
    {
      ContentProviderClient client = getActivity().getContentResolver()
          .acquireContentProviderClient(AddressbookSyncScheduler.CONTENT_AUTHORITY);

      for (AccountContactDetails accountDetails : accounts) {
        Uri rawContactsUri = LocalContactCollection
            .getSyncAdapterUri(ContactsContract.RawContacts.CONTENT_URI, accountDetails.account);

        Cursor cursor = client.query(rawContactsUri, null, null, null, null);
        accountDetails.contact_count = cursor.getCount();

        cursor.close();
      }
    }

    protected List<AccountContactDetails> getOtherAccounts() {
      List<AccountContactDetails> accounts = new LinkedList<AccountContactDetails>();

      for (Account osAccount : AccountManager.get(getActivity()).getAccounts()) {
        if (!osAccount.name.equals(account.getOsAccount().name) &&
            !osAccount.type.equals(account.getOsAccount().type))
        {
          accounts.add(new AccountContactDetails(osAccount, 0));
        }
      }

      return accounts;
    }

    @Override
    protected Bundle doInBackground(Void... params) {
      Bundle result = new Bundle();

      try {

        accountDetails = getOtherAccounts();
        populateAccountContactCounts(accountDetails);

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
        handleAccountDetailsRetrieved(accountDetails);
      else
        ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
    }
  }

  protected class AccountContactDetails {
    public Account account;
    public int     contact_count;

    public AccountContactDetails(Account account, int contact_count) {
      this.account        = account;
      this.contact_count  = contact_count;
    }
  }

}
