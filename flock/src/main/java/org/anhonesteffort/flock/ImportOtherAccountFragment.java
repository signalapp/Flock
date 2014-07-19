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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.util.PasswordUtil;
import org.apache.commons.lang.StringUtils;

/**
 * Programmer: rhodey
 */
public class ImportOtherAccountFragment extends Fragment {

  private static final String TAG = "org.anhonesteffort.flock.ImportOtherAccountFragment";

  private static MessageHandler messageHandler;
  private        SetupActivity  setupActivity;
  private        TextWatcher    passwordWatcher;
  private        TextWatcher    passwordRepeatWatcher;

  private Optional<String>  davTestHost     = Optional.absent();
  private Optional<String>  davTestUsername = Optional.absent();

  protected void setDavTestOptions(String davTestHost, String davTestUsername) {
    this.davTestHost     = Optional.of(davTestHost);
    this.davTestUsername = Optional.of(davTestUsername);
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
    View fragmentView = inflater.inflate(R.layout.fragment_import_other_account, container, false);

    initFormWithTestParams(fragmentView);
    initButtons();

    return fragmentView;
  }

  private void initFormWithTestParams(View fragmentView) {
    if (messageHandler != null && messageHandler.serviceStarted) {
      getActivity().setProgressBarIndeterminateVisibility(true);
      getActivity().setProgressBarVisibility(true);
      setupActivity.setNavigationDisabled(true);
    }

    TextView webDAVHost     = (TextView)fragmentView.findViewById(R.id.href_webdav_host);
    TextView webDAVUsername = (TextView)fragmentView.findViewById(R.id.account_username);

    if (davTestHost.isPresent())
      webDAVHost.setText(davTestHost.get());
    if (davTestUsername.isPresent())
      webDAVUsername.setText(davTestUsername.get());

    final EditText    passwordTextView           = (EditText) fragmentView.findViewById(R.id.cipher_passphrase);
    final EditText    passwordRepeatTextView     = (EditText) fragmentView.findViewById(R.id.cipher_passphrase_repeat);
    final ProgressBar passwordProgressView       = (ProgressBar) fragmentView.findViewById(R.id.progress_password_strength);
    final ProgressBar passwordRepeatProgressView = (ProgressBar) fragmentView.findViewById(R.id.progress_password_strength_repeat);

    if (passwordWatcher != null)
      passwordTextView.removeTextChangedListener(passwordWatcher);
    if (passwordRepeatWatcher != null)
      passwordRepeatTextView.removeTextChangedListener(passwordRepeatWatcher);

    passwordWatcher       = PasswordUtil.getPasswordStrengthTextWatcher(getActivity(), passwordProgressView);
    passwordRepeatWatcher = PasswordUtil.getPasswordStrengthTextWatcher(getActivity(), passwordRepeatProgressView);

    passwordTextView.addTextChangedListener(passwordWatcher);
    passwordRepeatTextView.addTextChangedListener(passwordRepeatWatcher);
  }

  private void handleImportComplete() {
    Log.d(TAG, "handleImportComplete()");
    setupActivity.setIsNewAccount(true);
    setupActivity.updateFragmentUsingState(SetupActivity.STATE_IMPORT_CONTACTS);
  }

  private void initButtons() {
    getActivity().findViewById(R.id.button_next).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        ImportAccountAsync();
      }

    });
  }

  private void ImportAccountAsync() {
    if (messageHandler != null && messageHandler.serviceStarted)
      return;
    else if (messageHandler == null)
      messageHandler = new MessageHandler(setupActivity, this);

    Bundle result                 = new Bundle();
    String hrefWebDAVHost         = ((TextView)getView().findViewById(R.id.href_webdav_host)).getText().toString().trim();
    String accountUsername        = ((TextView)getView().findViewById(R.id.account_username)).getText().toString().trim();
    String accountPassword        = ((TextView)getView().findViewById(R.id.account_password)).getText().toString().trim();
    String cipherPassphrase       = ((TextView)getView().findViewById(R.id.cipher_passphrase)).getText().toString().trim();
    String cipherPassphraseRepeat = ((TextView)getView().findViewById(R.id.cipher_passphrase_repeat)).getText().toString().trim();

    if (StringUtils.isEmpty(hrefWebDAVHost)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_EMPTY_DAV_URL);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      return ;
    }

    if (!accountUsername.contains("@")) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_ILLEGAL_ACCOUNT_ID);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      return ;
    }

    if (StringUtils.isEmpty(cipherPassphrase) || StringUtils.isEmpty(accountPassword)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SHORT_PASSWORD);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      ((TextView)getView().findViewById(R.id.cipher_passphrase)).setText("");
      ((TextView)getView().findViewById(R.id.cipher_passphrase_repeat)).setText("");
      return ;
    }

    if (!cipherPassphrase.equals(cipherPassphraseRepeat)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_PASSWORDS_DO_NOT_MATCH);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      ((TextView)getView().findViewById(R.id.cipher_passphrase)).setText("");
      ((TextView)getView().findViewById(R.id.cipher_passphrase_repeat)).setText("");
      return;
    }

    DavAccount importAccount = new DavAccount(accountUsername, accountPassword, hrefWebDAVHost);
    Intent     importService = new Intent(getActivity(), ImportOtherAccountService.class);

    importService.putExtra(ImportOtherAccountService.KEY_MESSENGER,         new Messenger(messageHandler));
    importService.putExtra(ImportOtherAccountService.KEY_ACCOUNT,           importAccount.toBundle());
    importService.putExtra(ImportOtherAccountService.KEY_MASTER_PASSPHRASE, cipherPassphrase);

    getActivity().startService(importService);
    messageHandler.serviceStarted = true;

    setupActivity.setNavigationDisabled(true);
    getActivity().setProgressBarIndeterminateVisibility(true);
    getActivity().setProgressBarVisibility(true);
  }

  public static class MessageHandler extends Handler {

    public SetupActivity              setupActivity;
    public ImportOtherAccountFragment importFragment;
    public boolean                    serviceStarted = false;

    public MessageHandler(SetupActivity setupActivity, ImportOtherAccountFragment importFragment) {
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

      if (message.arg1 == ErrorToaster.CODE_SUCCESS)
        importFragment.handleImportComplete();

      else {
        Bundle errorBundler = new Bundle();

        errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE, message.arg1);
        ErrorToaster.handleDisplayToastBundledError(setupActivity, errorBundler);

        if (importFragment.getView().findViewById(R.id.account_password) != null) {
          ((TextView)importFragment.getView().findViewById(R.id.account_password)).setText("");
          ((TextView)importFragment.getView().findViewById(R.id.cipher_passphrase)).setText("");
          ((TextView)importFragment.getView().findViewById(R.id.cipher_passphrase_repeat)).setText("");
        }
      }
    }

  }
}
