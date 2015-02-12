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
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.util.PasswordUtil;
import org.apache.commons.lang.StringUtils;

/**
 * Programmer: rhodey
 */
public class RegisterAccountFragment extends Fragment {

  private static final String TAG = "org.anhonesteffort.flock.RegisterAccountFragment";

  private static final String KEY_USERNAME = "KEY_USERNAME";

  protected static final int CODE_ACCOUNT_IMPORTED = 9001;

  private static MessageHandler   messageHandler;
  private        SetupActivity    setupActivity;
  private        TextWatcher      passwordWatcher;
  private        TextWatcher      passwordRepeatWatcher;
  private        Optional<String> username = Optional.absent();

  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    if (savedInstanceState != null)
      username = Optional.fromNullable(savedInstanceState.getString(KEY_USERNAME));
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    EditText usernameView = (EditText) getActivity().findViewById(R.id.account_username);
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
    View view = inflater.inflate(R.layout.fragment_register_ows_account, container, false);

    initButtons();
    initForm(view);

    return view;
  }

  private void initButtons() {
    getActivity().findViewById(R.id.button_next).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        registerAccountAsync();
      }

    });
  }

  private void initForm(View view) {
    if (messageHandler != null && messageHandler.serviceStarted) {
      getActivity().setProgressBarIndeterminateVisibility(true);
      getActivity().setProgressBarVisibility(true);
      setupActivity.setNavigationDisabled(true);
    }

    if (username.isPresent())
      ((EditText)view.findViewById(R.id.account_username)).setText(username.get());

    final EditText    passwordTextView           = (EditText) view.findViewById(R.id.cipher_passphrase);
    final EditText    passwordRepeatTextView     = (EditText) view.findViewById(R.id.cipher_passphrase_repeat);
    final ProgressBar passwordProgressView       = (ProgressBar) view.findViewById(R.id.progress_password_strength);
    final ProgressBar passwordRepeatProgressView = (ProgressBar) view.findViewById(R.id.progress_password_strength_repeat);

    if (passwordWatcher != null)
      passwordTextView.removeTextChangedListener(passwordWatcher);
    if (passwordRepeatWatcher != null)
      passwordRepeatTextView.removeTextChangedListener(passwordRepeatWatcher);

    passwordWatcher       = PasswordUtil.getPasswordStrengthTextWatcher(getActivity(), passwordProgressView);
    passwordRepeatWatcher = PasswordUtil.getPasswordStrengthTextWatcher(getActivity(), passwordRepeatProgressView);

    passwordTextView.addTextChangedListener(passwordWatcher);
    passwordRepeatTextView.addTextChangedListener(passwordRepeatWatcher);
  }

  private void handleRegisterComplete() {
    Log.d(TAG, "handleRegisterComplete()");
    setupActivity.updateFragmentUsingState(SetupActivity.STATE_IMPORT_CONTACTS);
  }

  private void registerAccountAsync() {
    if (messageHandler != null && messageHandler.serviceStarted)
      return;
    else if (messageHandler == null)
      messageHandler = new MessageHandler(setupActivity, this);

    Bundle result         = new Bundle();
    String username       = ((EditText) getActivity().findViewById(R.id.account_username)).getText().toString().trim();
    String password       = ((EditText) getActivity().findViewById(R.id.cipher_passphrase)).getText().toString().trim();
    String passwordRepeat = ((EditText) getActivity().findViewById(R.id.cipher_passphrase_repeat)).getText().toString().trim();

    if (StringUtils.isEmpty(username)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_EMPTY_ACCOUNT_ID);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      return;
    }

    if (username.contains(" ")) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SPACES_IN_USERNAME);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      return;
    }

    if (StringUtils.isEmpty(password)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SHORT_PASSWORD);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      ((TextView)getActivity().findViewById(R.id.cipher_passphrase)).setText("");
      ((TextView)getActivity().findViewById(R.id.cipher_passphrase_repeat)).setText("");
      return;
    }

    if (StringUtils.isEmpty(passwordRepeat) || !password.equals(passwordRepeat)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_PASSWORDS_DO_NOT_MATCH);
      ErrorToaster.handleDisplayToastBundledError(getActivity(), result);
      ((TextView)getActivity().findViewById(R.id.cipher_passphrase)).setText("");
      ((TextView)getActivity().findViewById(R.id.cipher_passphrase_repeat)).setText("");
      return;
    }

           username      = DavAccountHelper.correctUsername(getActivity(), username);
    Intent importService = new Intent(getActivity(), RegisterAccountService.class);

    importService.putExtra(RegisterAccountService.KEY_MESSENGER,         new Messenger(messageHandler));
    importService.putExtra(RegisterAccountService.KEY_ACCOUNT_ID,        username);
    importService.putExtra(RegisterAccountService.KEY_MASTER_PASSPHRASE, password);

    getActivity().startService(importService);
    messageHandler.serviceStarted = true;

    setupActivity.setNavigationDisabled(true);
    getActivity().setProgressBarIndeterminateVisibility(true);
    getActivity().setProgressBarVisibility(true);
  }

  public static class MessageHandler extends Handler {

    public SetupActivity           setupActivity;
    public RegisterAccountFragment importFragment;
    public boolean                 serviceStarted = false;

    public MessageHandler(SetupActivity setupActivity, RegisterAccountFragment importFragment) {
      this.setupActivity  = setupActivity;
      this.importFragment = importFragment;
    }

    @Override
    public void handleMessage(Message message) {
      messageHandler = null;
      serviceStarted = false;

      setupActivity.setNavigationDisabled(false);
      setupActivity.setProgressBarIndeterminateVisibility(false);
      setupActivity.setProgressBarVisibility(false);

      if (message.arg1 == CODE_ACCOUNT_IMPORTED)
        importFragment.handleRegisterComplete();

      else if (message.arg1 != ErrorToaster.CODE_SUCCESS) {
        Bundle errorBundler = new Bundle();

        errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE, message.arg1);
        ErrorToaster.handleDisplayToastBundledError(setupActivity, errorBundler);

        if (importFragment.getView().findViewById(R.id.account_username) != null) {
          ((TextView)importFragment.getView().findViewById(R.id.account_username)).setText("");
          ((TextView)importFragment.getView().findViewById(R.id.cipher_passphrase)).setText("");
          ((TextView)importFragment.getView().findViewById(R.id.cipher_passphrase_repeat)).setText("");
        }
      }
    }

  }
}
