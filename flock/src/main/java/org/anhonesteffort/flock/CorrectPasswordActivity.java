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
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;

/**
 * Programmer: rhodey
 */
public class CorrectPasswordActivity extends Activity {

  private static final String TAG = "org.anhonesteffort.flock.CorrectPasswordActivity";

  private String  accountUsername;
  private String  accountDavHREF;
  private boolean serviceStarted = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.correct_password);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_correct_sync_password);

    getUsernameAndHrefOrFinish();

    findViewById(R.id.login_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleStartCorrectPasswordService();
      }
    });
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

  private void getUsernameAndHrefOrFinish() {
    Optional<String> username = DavAccountHelper.getAccountUsername(getBaseContext());
    Optional<String> davHref  = DavAccountHelper.getAccountDavHREF(getBaseContext());

    if (!DavAccountHelper.isAccountRegistered(getBaseContext()) ||
        !username.isPresent() ||
        !davHref.isPresent())
    {
      Log.e(TAG, "not account is registered, not sure how we got here");
      Toast.makeText(this, R.string.error_no_account_is_registered, Toast.LENGTH_SHORT).show();
      startActivity(new Intent(getBaseContext(), SetupActivity.class));
      finish();
      return;
    }

    accountUsername = username.get();
    accountDavHREF  = davHref.get();
  }

  private void handleLoginSuccess() {
    Log.d(TAG, "handleLoginSuccess");
    Toast.makeText(getBaseContext(),
                   R.string.login_successful,
                   Toast.LENGTH_SHORT).show();

    new KeySyncScheduler(getBaseContext()).requestSync();
    new CalendarsSyncScheduler(getBaseContext()).requestSync();
    new AddressbookSyncScheduler(getBaseContext()).requestSync();

    finish();
  }

  private void handleStartCorrectPasswordService() {
    Log.d(TAG, "handleStartCorrectPasswordService()");

    if (serviceStarted)
      return;

    String     password = ((TextView)findViewById(R.id.account_password)).getText().toString().trim();
    DavAccount account  = new DavAccount(accountUsername, password, accountDavHREF);

    if (TextUtils.isEmpty(password)) {
      Toast.makeText(getBaseContext(),
                     R.string.error_password_too_short,
                     Toast.LENGTH_SHORT).show();
      return;
    }

    Intent correctService = new Intent(getBaseContext(), CorrectPasswordService.class);

    correctService.putExtra(CorrectPasswordService.KEY_MESSENGER, new Messenger(new MessageHandler()));
    correctService.putExtra(CorrectPasswordService.KEY_MASTER_PASSPHRASE, password);
    correctService.putExtra(CorrectPasswordService.KEY_ACCOUNT, account.toBundle());

    startService(correctService);
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
        handleLoginSuccess();

      else {
               serviceStarted = false;
        Bundle errorBundler   = new Bundle();

        errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE, message.arg1);
        ErrorToaster.handleDisplayToastBundledError(getBaseContext(), errorBundler);

        if (findViewById(R.id.account_password) != null)
          ((TextView)findViewById(R.id.account_password)).setText("");
      }
    }

  }

}
