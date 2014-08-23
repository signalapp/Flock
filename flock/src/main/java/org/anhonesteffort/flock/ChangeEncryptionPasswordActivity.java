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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.anhonesteffort.flock.util.PasswordUtil;

/**
 * Programmer: rhodey
 */
public class ChangeEncryptionPasswordActivity extends AccountAndKeyRequiredActivity {

  private static final String TAG = "org.anhonesteffort.flock.ChangeEncryptionPasswordActivity";

  private TextWatcher passwordWatcher;
  private TextWatcher passwordRepeatWatcher;
  private boolean     serviceStarted = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!accountAndKeyAvailableAndMigrationComplete())
      return;

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.change_encryption_password);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_change_encryption_password);

    handleInitForm();
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

  private void handleInitForm() {
    findViewById(R.id.change_password_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleChangeCipherPassphrase();
      }
    });

    final EditText    passwordTextView           = (EditText)    findViewById(R.id.new_cipher_passphrase);
    final EditText    passwordRepeatTextView     = (EditText)    findViewById(R.id.new_cipher_passphrase_repeat);
    final ProgressBar passwordProgressView       = (ProgressBar) findViewById(R.id.progress_password_strength);
    final ProgressBar passwordRepeatProgressView = (ProgressBar) findViewById(R.id.progress_password_strength_repeat);

    if (passwordWatcher != null)
      passwordTextView.removeTextChangedListener(passwordWatcher);
    if (passwordRepeatWatcher != null)
      passwordRepeatTextView.removeTextChangedListener(passwordRepeatWatcher);

    passwordWatcher       = PasswordUtil.getPasswordStrengthTextWatcher(getBaseContext(), passwordProgressView);
    passwordRepeatWatcher = PasswordUtil.getPasswordStrengthTextWatcher(getBaseContext(), passwordRepeatProgressView);

    passwordTextView.addTextChangedListener(passwordWatcher);
    passwordRepeatTextView.addTextChangedListener(passwordRepeatWatcher);
  }

  private void handlePasswordChanged() {
    Log.d(TAG, "handlePasswordChanged()");

    Toast.makeText(getBaseContext(), R.string.encryption_password_saved, Toast.LENGTH_SHORT).show();
    new KeySyncScheduler(getBaseContext()).requestSync();
    finish();
  }

  private void handleChangeCipherPassphrase() {
    if (serviceStarted)
      return;

    Bundle result                    = new Bundle();
    String cipherPassphrase          = ((TextView)findViewById(R.id.cipher_passphrase)).getText().toString().trim();
    String newCipherPassphrase       = ((TextView)findViewById(R.id.new_cipher_passphrase)).getText().toString().trim();
    String newCipherPassphraseRepeat = ((TextView)findViewById(R.id.new_cipher_passphrase_repeat)).getText().toString().trim();

    if (newCipherPassphrase.length() == 0) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SHORT_PASSWORD);
      ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
      ((TextView)findViewById(R.id.new_cipher_passphrase)).setText("");
      return;
    }

    if (!newCipherPassphrase.equals(newCipherPassphraseRepeat)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_PASSWORDS_DO_NOT_MATCH);
      ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
      ((TextView)findViewById(R.id.new_cipher_passphrase)).setText("");
      ((TextView)findViewById(R.id.new_cipher_passphrase_repeat)).setText("");
      return;
    }

    Optional<String> savedPassphrase = KeyStore.getMasterPassphrase(getBaseContext());
    if (!savedPassphrase.isPresent() || !savedPassphrase.get().equals(cipherPassphrase)) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_INVALID_CIPHER_PASSPHRASE);
      ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);

      ((TextView)findViewById(R.id.cipher_passphrase)).setText("");
      ((TextView)findViewById(R.id.new_cipher_passphrase)).setText("");
      ((TextView)findViewById(R.id.new_cipher_passphrase_repeat)).setText("");
      return;
    }

    Intent changeService = new Intent(getBaseContext(), ChangeEncryptionPasswordService.class);

    changeService.putExtra(ChangeEncryptionPasswordService.KEY_MESSENGER,             new Messenger(new MessageHandler()));
    changeService.putExtra(ChangeEncryptionPasswordService.KEY_OLD_MASTER_PASSPHRASE, cipherPassphrase);
    changeService.putExtra(ChangeEncryptionPasswordService.KEY_NEW_MASTER_PASSPHRASE, newCipherPassphrase);
    changeService.putExtra(ChangeEncryptionPasswordService.KEY_ACCOUNT,               account.toBundle());

    startService(changeService);
    serviceStarted = true;

    setProgressBarIndeterminateVisibility(true);
    setProgressBarVisibility(true);
  }

  public class MessageHandler extends Handler {

    @Override
    public void handleMessage(Message message) {
      setProgressBarIndeterminateVisibility(false);
      setProgressBarVisibility(false);

      if (message.arg1 == ErrorToaster.CODE_SUCCESS)
        handlePasswordChanged();

      else {
               serviceStarted = false;
        Bundle errorBundler   = new Bundle();

        errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE, message.arg1);
        ErrorToaster.handleDisplayToastBundledError(getBaseContext(), errorBundler);

        if (findViewById(R.id.cipher_passphrase) != null) {
          ((TextView)findViewById(R.id.cipher_passphrase)).setText("");
          ((TextView)findViewById(R.id.new_cipher_passphrase)).setText("");
          ((TextView)findViewById(R.id.new_cipher_passphrase_repeat)).setText("");
        }
      }
    }

  }
}
