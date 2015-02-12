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
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.apache.commons.lang.StringUtils;


/**
 * Programmer: rhodey
 */
public class ImportOwsAccountFragment extends Fragment {

  private static final String TAG = "org.anhonesteffort.flock.ImportOwsAccountFragment";

  private static final String KEY_USERNAME = "KEY_USERNAME";

  protected static final int CODE_ACCOUNT_IMPORTED = 9001;

  private static MessageHandler messageHandler;
  private        SetupActivity  setupActivity;

  private Optional<String> username = Optional.absent();

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    if (savedInstanceState != null)
      username = Optional.fromNullable(savedInstanceState.getString(KEY_USERNAME));
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    TextView usernameView = (TextView)getView().findViewById(R.id.account_username);
    if (usernameView.getText() != null)
      savedInstanceState.putString(KEY_USERNAME, usernameView.getText().toString());
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof SetupActivity)
      this.setupActivity = (SetupActivity) activity;
    else
      throw new ClassCastException(activity.toString() + " not what I expected D: !");

    if (messageHandler == null)
      messageHandler = new MessageHandler(setupActivity, this);
    else {
      messageHandler.setupActivity  = setupActivity;
      messageHandler.importFragment = this;
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    View view = inflater.inflate(R.layout.fragment_import_ows_account, container, false);

    initButtons();
    initForm();

    return view;
  }

  private void initButtons() {
    getActivity().findViewById(R.id.button_next).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        handleImportAccountAsync();
      }

    });
  }

  private void initForm() {
    TextView usernameView = (TextView)getActivity().findViewById(R.id.account_username);
    if (username.isPresent())
      usernameView.setText(username.get());

    if (messageHandler != null && messageHandler.serviceStarted) {
      getActivity().setProgressBarIndeterminateVisibility(true);
      getActivity().setProgressBarVisibility(true);
      setupActivity.setNavigationDisabled(true);
    }
  }

  private void handleImportComplete() {
    Log.d(TAG, "handleImportComplete()");
    setupActivity.updateFragmentUsingState(SetupActivity.STATE_IMPORT_CONTACTS);
  }

  private void handleSubscriptionExpired(DavAccount account) {
    Log.d(TAG, "handleSubscriptionExpired()");
    Toast.makeText(getActivity(),
                   R.string.notification_flock_subscription_expired,
                   Toast.LENGTH_LONG).show();

    Intent nextIntent = new Intent(getActivity(), ManageSubscriptionActivity.class);
    nextIntent.putExtra(ManageSubscriptionActivity.KEY_DAV_ACCOUNT_BUNDLE, account.toBundle());
    startActivity(nextIntent);
  }

  private void handleImportAccountAsync() {
    if (messageHandler != null && messageHandler.serviceStarted)
      return;
    else if (messageHandler == null)
      messageHandler = new MessageHandler(setupActivity, this);

    Bundle result           = new Bundle();
    String accountId        = ((TextView)getView().findViewById(R.id.account_username)).getText().toString().trim();
    String cipherPassphrase = ((TextView)getView().findViewById(R.id.cipher_passphrase)).getText().toString().trim();

    if (StringUtils.isEmpty(accountId)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_EMPTY_ACCOUNT_ID);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      ((TextView)getView().findViewById(R.id.account_username)).setText("");
      ((TextView)getView().findViewById(R.id.cipher_passphrase)).setText("");
      return;
    }

    if (cipherPassphrase.length() == 0) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SHORT_PASSWORD);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      ((TextView)getView().findViewById(R.id.account_username)).setText("");
      ((TextView)getView().findViewById(R.id.cipher_passphrase)).setText("");
      return;
    }

    Intent importService = new Intent(getActivity(), ImportOwsAccountService.class);
           accountId     = DavAccountHelper.correctUsername(getActivity(), accountId);

    importService.putExtra(ImportOwsAccountService.KEY_MESSENGER,         new Messenger(messageHandler));
    importService.putExtra(ImportOwsAccountService.KEY_ACCOUNT_ID,        accountId);
    importService.putExtra(ImportOwsAccountService.KEY_MASTER_PASSPHRASE, cipherPassphrase);

    getActivity().startService(importService);
    messageHandler.serviceStarted = true;
    setupActivity.setNavigationDisabled(true);

    getActivity().setProgressBarIndeterminateVisibility(true);
    getActivity().setProgressBarVisibility(true);
  }

  public static class MessageHandler extends Handler {

    public SetupActivity            setupActivity;
    public ImportOwsAccountFragment importFragment;
    public boolean                  serviceStarted = false;

    public MessageHandler(SetupActivity setupActivity, ImportOwsAccountFragment importFragment) {
      this.setupActivity  = setupActivity;
      this.importFragment = importFragment;
    }

    @Override
    public void handleMessage(Message message) {
      messageHandler = null;
      serviceStarted = false;

      setupActivity.setProgressBarIndeterminateVisibility(false);
      setupActivity.setProgressBarVisibility(false);
      setupActivity.setNavigationDisabled(false);

      if (message.arg1 == CODE_ACCOUNT_IMPORTED)
        importFragment.handleImportComplete();

      else if (message.arg1 == ErrorToaster.CODE_SUBSCRIPTION_EXPIRED) {
        Optional<DavAccount> account = DavAccount.build(message.getData());
        if (account.isPresent())
          importFragment.handleSubscriptionExpired(account.get());
        else
          Log.e(TAG, "unable to build account for subscription expired message!!! :(");
      }

      else if (message.arg1 != ErrorToaster.CODE_SUCCESS) {
        Bundle errorBundler = new Bundle();

        errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE, message.arg1);
        ErrorToaster.handleDisplayToastBundledError(setupActivity, errorBundler);

        if (importFragment.getView().findViewById(R.id.account_username) != null) {
          ((TextView)importFragment.getView().findViewById(R.id.account_username)).setText("");
          ((TextView)importFragment.getView().findViewById(R.id.cipher_passphrase)).setText("");
        }
      }
    }
  }
}
