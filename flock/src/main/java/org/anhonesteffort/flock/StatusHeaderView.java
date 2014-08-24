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

import android.content.ContentResolver;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.InvalidCipherVersionException;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.DavKeyCollection;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class StatusHeaderView extends LinearLayout {

  private final static String TAG = StatusHeaderView.class.getSimpleName();

  private final Handler uiHandler     = new Handler();
  private       Timer   intervalTimer = new Timer();

  private final TextView timeLastSyncView;
  private final TextView syncStatusView;

  private Optional<DavAccount> account;
  private AsyncTask            asyncTaskSubscription;
  private AsyncTask            asyncTaskCard;
  private AsyncTask            asyncTaskMasterPassphrase;
  private AsyncTask            asyncTaskMigration;

  private long    timeLastSync                  = -1;
  private boolean syncInProgress                = false;
  private boolean subscriptionIsValid           = true;
  private boolean cardIsValid                   = true;
  private boolean cipherPassphraseIsValid       = true;
  private boolean authNotificationShown         = false;
  private boolean subscriptionNotificationShown = false;

  private boolean syncServerHasError                       = false;
  private boolean registrationServerHasError               = false;
  private boolean syncServerErrorNotificationShown         = false;
  private boolean registrationServerErrorNotificationShown = false;

  public StatusHeaderView(Context context) {
    super(context);

    LayoutInflater.from(context).inflate(R.layout.status_header_view, this);

    account          = DavAccountHelper.getAccount(context);
    timeLastSyncView = (TextView) getRootView().findViewById(R.id.last_sync_time);
    syncStatusView   = (TextView) getRootView().findViewById(R.id.sync_status);
  }

  public void hackOnPause() {
    if (asyncTaskSubscription != null && !asyncTaskSubscription.isCancelled())
      asyncTaskSubscription.cancel(true);

    if (asyncTaskCard != null && !asyncTaskCard.isCancelled())
      asyncTaskCard.cancel(true);

    if (asyncTaskMasterPassphrase != null && !asyncTaskMasterPassphrase.isCancelled())
      asyncTaskMasterPassphrase.cancel(true);

    if (intervalTimer != null)
      intervalTimer.cancel();
  }

  private void handleUpdateTimeLastSync() {
    AddressbookSyncScheduler addressbookSync = new AddressbookSyncScheduler(getContext());
    CalendarsSyncScheduler   calendarSync    = new CalendarsSyncScheduler(getContext());

    Optional<Long> lastContactSync  = addressbookSync.getTimeLastSync();
    Optional<Long> lastCalendarSync = calendarSync.getTimeLastSync();

    if (lastCalendarSync.isPresent() && !lastContactSync.isPresent())
      timeLastSync = lastCalendarSync.get();
    else  if (!lastCalendarSync.isPresent() && lastContactSync.isPresent())
      timeLastSync = lastContactSync.get();
    else if (!lastCalendarSync.isPresent() && !lastContactSync.isPresent())
      timeLastSync = -1;
    else
      timeLastSync = Math.min(lastContactSync.get(), lastCalendarSync.get());

    if (!account.isPresent())
      return;

    syncInProgress = addressbookSync.syncInProgress(account.get().getOsAccount()) ||
                     calendarSync.syncInProgress(account.get().getOsAccount());
  }

  private void handleUpdateLayout() {
    final String timeLastSyncText;
    final String syncStatusText;
    final int    syncStatusDrawable;

    if (!ContentResolver.getMasterSyncAutomatically()) {
      syncStatusView.setText(getContext().getString(R.string.status_header_status_sync_disabled));
      timeLastSyncView.setVisibility(GONE);
      syncStatusView.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.sad_cloud, 0, 0);

      invalidate();
      return;
    }

    if (timeLastSync == -1) {
      syncStatusView.setText(getContext().getString(R.string.status_header_sync_in_progress));
      syncStatusView.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.sync_in_progress, 0, 0);
      timeLastSyncView.setVisibility(GONE);

      invalidate();
      return;
    }
    else {
      DateFormat formatter = DateFormat.getDateTimeInstance();
      timeLastSyncText     = formatter.format(new Date(timeLastSync));
      timeLastSyncView.setText(getContext().getString(R.string.status_header_sync_time, timeLastSyncText));
      timeLastSyncView.setVisibility(VISIBLE);
    }

    if (syncServerHasError) {
      if (account.isPresent() && DavAccountHelper.isUsingOurServers(account.get()))
        syncStatusText = getContext().getString(R.string.status_header_status_our_sync_service_error);
      else
        syncStatusText = getContext().getString(R.string.status_header_status_their_sync_service_error);

      syncStatusDrawable = R.drawable.sad_cloud;
    }
    else if (registrationServerHasError) {
      syncStatusText     = getContext().getString(R.string.status_header_status_registration_service_error);
      syncStatusDrawable = R.drawable.sad_cloud;
    }
    else if (!DavAccountHelper.getAccountPassword(getContext()).isPresent()) {
      syncStatusText     = getContext().getString(R.string.status_header_status_account_login_failed);
      syncStatusDrawable = R.drawable.sad_cloud;
      if (!authNotificationShown) {
        AbstractDavSyncAdapter.showAuthNotificationAndInvalidatePassword(getContext());
        authNotificationShown = true;
      }
    }
    else if (!subscriptionIsValid) {
      syncStatusText     = getContext().getString(R.string.notification_flock_subscription_expired);
      syncStatusDrawable = R.drawable.sad_cloud;
      if (!subscriptionNotificationShown) {
        AbstractDavSyncAdapter.showSubscriptionExpiredNotification(getContext());
        subscriptionNotificationShown = true;
      }
    }
    else if (!cardIsValid) {
      syncStatusText     = getContext().getString(R.string.status_header_status_auto_renew_error);
      syncStatusDrawable = R.drawable.sad_cloud;
    }
    else if (MigrationHelperBroadcastReceiver.getUiDisabledForMigration(getContext())) {
      syncStatusText     = getContext().getString(R.string.status_header_status_migration_in_progress);
      syncStatusDrawable = R.drawable.migration_in_progress;

      timeLastSyncView.setText(R.string.please_wait_this_will_take_a_few_minutes);
      timeLastSyncView.setVisibility(VISIBLE);

      new KeySyncScheduler(getContext()).requestSync();
    }
    else if (!cipherPassphraseIsValid) {
      syncStatusText     = getContext().getString(R.string.status_header_status_encryption_password_incorrect);
      syncStatusDrawable = R.drawable.sad_cloud;
    }
    else if (syncInProgress) {
      syncStatusText     = getContext().getString(R.string.status_header_sync_in_progress);
      syncStatusDrawable = R.drawable.sync_in_progress;
    }
    else {
      syncStatusText     = getContext().getString(R.string.status_header_status_good);
      syncStatusDrawable = R.drawable.happy_cloud;
    }

    syncStatusView.setText(syncStatusText);
    syncStatusView.setCompoundDrawablesWithIntrinsicBounds(0, syncStatusDrawable, 0, 0);
    invalidate();
  }

  private void handleUpdateSubscriptionIsValid() {
    if (!account.isPresent() || (asyncTaskSubscription != null && !asyncTaskSubscription.isCancelled()))
      return;

    asyncTaskSubscription = new AsyncTask<String, Void, Bundle>() {

      boolean subscriptionExpired = false;

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result = new Bundle();

        try {

          subscriptionExpired = DavAccountHelper.isExpired(getContext(), account.get());
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (PropertyParseException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (DavException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTaskSubscription = null;
        subscriptionIsValid   = !subscriptionExpired;

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_DAV_SERVER_ERROR) {
          if (!syncServerErrorNotificationShown || !syncServerHasError) {
            ErrorToaster.handleDisplayToastBundledError(getContext(), result);
            syncServerHasError               = true;
            syncServerErrorNotificationShown = true;
          }
        }
        else if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS)
          ErrorToaster.handleDisplayToastBundledError(getContext(), result);
      }

    }.execute();
  }

  private void handleUpdateCardIsValid() {
    if (!account.isPresent() || (asyncTaskCard != null && !asyncTaskCard.isCancelled()))
      return;

    asyncTaskCard = new AsyncTask<String, Void, Bundle>() {

      boolean lastChargeFailed = false;

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle          result          = new Bundle();
        RegistrationApi registrationApi = new RegistrationApi(getContext());

        try {

          if (registrationApi.getAccount(account.get()).getLastStripeChargeFailed())
            lastChargeFailed = registrationApi.getCard(account.get()).isPresent();

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (RegistrationApiException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTaskCard = null;
        cardIsValid   = !lastChargeFailed;

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_REGISTRATION_API_SERVER_ERROR) {
          if (!registrationServerErrorNotificationShown || !registrationServerHasError) {
            ErrorToaster.handleDisplayToastBundledError(getContext(), result);
            registrationServerHasError               = true;
            registrationServerErrorNotificationShown = true;
          }
        }
        else if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS)
          ErrorToaster.handleDisplayToastBundledError(getContext(), result);
      }

    }.execute();
  }

  private void handleUpdateCipherPassphraseIsValid() {
    if (asyncTaskMasterPassphrase != null && !asyncTaskMasterPassphrase.isCancelled())
      return;

    asyncTaskMasterPassphrase = new AsyncTask<String, Void, Bundle>() {

      boolean passphraseIsValid = false;

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result = new Bundle();

        try {

          passphraseIsValid = KeyHelper.masterPassphraseIsValid(getContext());
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (InvalidCipherVersionException e) {
          Log.d(TAG, "caught invalid cipher version exception, likely due to migration.", e);
        } catch (GeneralSecurityException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTaskMasterPassphrase = null;
        cipherPassphraseIsValid   = passphraseIsValid;

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS)
          ErrorToaster.handleDisplayToastBundledError(getContext(), result);
      }

    }.execute();
  }

  private void handleUpdateMigrationInProgress() {
    if ((asyncTaskMigration != null && !asyncTaskMigration.isCancelled()) ||
        !MigrationHelperBroadcastReceiver.getUiDisabledForMigration(getContext()))
      return;

    Log.d(TAG, "handleUpdateMigrationInProgress()");

    asyncTaskMigration = new AsyncTask<String, Void, Bundle>() {

      @Override
      protected Bundle doInBackground(String... params) {
        boolean migrationInProgress = true;
        Bundle  result              = new Bundle();

        try {

          DavKeyStore                davKeyStore   = DavAccountHelper.getDavKeyStore(getContext(), account.get());
          Optional<DavKeyCollection> keyCollection = davKeyStore.getCollection();

          if (keyCollection.isPresent())
            migrationInProgress = !keyCollection.get().isMigrationComplete();

          MigrationHelperBroadcastReceiver.setUiDisabledForMigration(getContext(), migrationInProgress);

          if (!migrationInProgress)
            MigrationHelperBroadcastReceiver.setMigrationUpdateHandled(getContext());

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (InvalidComponentException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (PropertyParseException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (DavException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTaskMigration = null;

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS)
          ErrorToaster.handleDisplayToastBundledError(getContext(), result);
      }

    }.execute();
  }

  private final Runnable refreshUiRunnable = new Runnable() {
    @Override
    public void run() {
      handleUpdateTimeLastSync();
      handleUpdateLayout();
    }
  };
  private final Runnable refreshSubscriptionRunnable = new Runnable() {
    @Override
    public void run() {
      handleUpdateSubscriptionIsValid();
    }
  };
  private final Runnable refreshCardRunnable = new Runnable() {
    @Override
    public void run() {
      handleUpdateCardIsValid();
    }
  };
  private final Runnable refreshCipherPassphraseRunnable = new Runnable() {
    @Override
    public void run() {
      handleUpdateCipherPassphraseIsValid();
    }
  };
  private final Runnable refreshMigrationRunnable = new Runnable() {
    @Override
    public void run() {
      handleUpdateMigrationInProgress();
    }
  };

  public void handleStartPerpetualRefresh() {
    account       = DavAccountHelper.getAccount(getContext());
    intervalTimer = new Timer();

    TimerTask uiTask = new TimerTask() {
      @Override
      public void run() {
        uiHandler.post(refreshUiRunnable);
      }
    };
    TimerTask subscriptionTask = new TimerTask() {
      @Override
      public void run() {
        uiHandler.post(refreshSubscriptionRunnable);
      }
    };
    TimerTask cardTask = new TimerTask() {
      @Override
      public void run() {
        uiHandler.post(refreshCardRunnable);
      }
    };
    TimerTask passphraseTask = new TimerTask() {
      @Override
      public void run() {
        uiHandler.post(refreshCipherPassphraseRunnable);
      }
    };
    TimerTask migrationTask = new TimerTask() {
      @Override
      public void run() {
        uiHandler.post(refreshMigrationRunnable);
      }
    };

    intervalTimer.schedule(uiTask, 0, 2000);

    if (account.isPresent()) {
      if (DavAccountHelper.isUsingOurServers(account.get())) {
        intervalTimer.schedule(subscriptionTask, 0, 20000);
        intervalTimer.schedule(cardTask,         0, 20000);
      }
      else
        intervalTimer.schedule(passphraseTask, 0, 10000);

      intervalTimer.schedule(migrationTask, 0, 10000);
    }
  }
}