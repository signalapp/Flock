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
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.FlockAccount;
import org.anhonesteffort.flock.registration.model.FlockCardInformation;
import org.anhonesteffort.flock.registration.model.FlockSubscription;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLException;

import de.passsy.holocircularprogressbar.HoloCircularProgressBar;

/**
 * Programmer: rhodey
 */
public class ManageSubscriptionActivity extends Activity {

  private static final String TAG = "org.anhonesteffort.flock.ManageSubscriptionActivity";

  public static final String KEY_DAV_ACCOUNT_BUNDLE      = "KEY_DAV_ACCOUNT_BUNDLE";
  public static final String KEY_CARD_INFORMATION_BUNDLE = "KEY_CARD_INFORMATION_BUNDLE";

  private Optional<AugmentedFlockAccount> flockAccount    = Optional.absent();
  private Optional<FlockCardInformation>  cardInformation = Optional.absent();

  private final Handler   uiHandler     = new Handler();
  private       Timer     intervalTimer = new Timer();

  private DavAccount davAccount;
  private AsyncTask  asyncTask;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.activity_manage_subscription);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_manage_subscription);

    if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
      if (!DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        finish();
        return;
      }

      davAccount      = DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
      cardInformation = FlockCardInformation.build(savedInstanceState.getBundle(KEY_CARD_INFORMATION_BUNDLE));
    }
    else if (getIntent().getExtras() != null) {
      if (!DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        finish();
        return;
      }

      davAccount      = DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
      cardInformation = FlockCardInformation.build(getIntent().getExtras().getBundle(KEY_CARD_INFORMATION_BUNDLE));
    }

    initButtons();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.manage_subscription, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {

      case android.R.id.home:
        finish();
        break;


      case R.id.button_send_bitcoin:
        Intent nextIntent = new Intent(getBaseContext(), SendBitcoinActivity.class);
        nextIntent.putExtra(SendBitcoinActivity.KEY_DAV_ACCOUNT_BUNDLE, davAccount.toBundle());
        startActivity(nextIntent);
        break;

    }

    return false;
  }

  @Override
  public void onResume() {
    super.onResume();

    Optional<Pair<Long, Boolean[]>> cachedSubscriptionDetails =
        RegistrationApi.getCachedSubscriptionDetails(getBaseContext());

    if (cachedSubscriptionDetails.isPresent()) {
      handleUpdateUi(cachedSubscriptionDetails.get().first,
                     cachedSubscriptionDetails.get().second[0],
                     cachedSubscriptionDetails.get().second[1],
                     Optional.<List<FlockSubscription>>absent());
      handleStartPerpetualRefresh();
    }
    else
      handleInitSubscriptionDetailsCache();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putBundle(KEY_DAV_ACCOUNT_BUNDLE, davAccount.toBundle());

    if (cardInformation.isPresent())
      savedInstanceState.putBundle(KEY_CARD_INFORMATION_BUNDLE, cardInformation.get().toBundle());

    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);

    if (intervalTimer != null)
      intervalTimer.cancel();
  }

  private void initButtons() {
    findViewById(R.id.button_edit_auto_renew).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        Intent nextIntent = new Intent(getBaseContext(), EditAutoRenewActivity.class);
        nextIntent.putExtra(EditAutoRenewActivity.KEY_DAV_ACCOUNT_BUNDLE, davAccount.toBundle());

        if (flockAccount.isPresent()) {
          nextIntent.putExtra(EditAutoRenewActivity.KEY_FLOCK_ACCOUNT_BUNDLE,
                              flockAccount.get().toBundle());
        }

        if (cardInformation.isPresent()) {
          nextIntent.putExtra(EditAutoRenewActivity.KEY_CARD_INFORMATION_BUNDLE,
                              cardInformation.get().toBundle());
        }

        startActivity(nextIntent);
      }

    });
  }

  private void handleUpdateSubscriptionHistory(List<FlockSubscription> subscriptions) {
    LinearLayout subscriptionHistory = (LinearLayout)findViewById(R.id.subscription_history);

    subscriptionHistory.removeAllViews();
    for (FlockSubscription subscription : subscriptions) {
      TextView subscriptionDetails = new TextView(getBaseContext());
      View     spacerView          = new View(getBaseContext());
      String   createDate          = new SimpleDateFormat("dd/MM/yy").format(subscription.getCreateDate());

      subscriptionDetails.setText(createDate + " - " + subscription.getDaysCredit() +
                                  " " + getString(R.string.days));

      LinearLayout.LayoutParams subscriptionParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
      );
      subscriptionParams.setMargins(0, 8, 0, 0);
      subscriptionDetails.setLayoutParams(subscriptionParams);
      subscriptionDetails.setTextAppearance(getBaseContext(), android.R.style.TextAppearance_Medium);
      subscriptionDetails.setTextColor(Color.BLACK);

      LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, 1
      );
      subscriptionParams.setMargins(0, 0, 0, 0);
      spacerView.setLayoutParams(spacerParams);
      spacerView.setBackground(getResources().getDrawable(android.R.drawable.divider_horizontal_bright));

      subscriptionHistory.addView(subscriptionDetails);
      subscriptionHistory.addView(spacerView);
    }
  }

  private void handleUpdateUi(Long                              daysRemaining,
                              Boolean                           autoRenewEnabled,
                              Boolean                           lastChargeFailed,
                              Optional<List<FlockSubscription>> subscriptions)
  {
    TextView                daysRemainingView     = (TextView)findViewById(R.id.days_remaining);
    TextView                autoRenewView         = (TextView)findViewById(R.id.auto_renew_status);
    Button                  editAutoRenewButton   = (Button)findViewById(R.id.button_edit_auto_renew);
    HoloCircularProgressBar progressBarView       = (HoloCircularProgressBar)findViewById(R.id.days_remaining_progress);
    int                     daysRemainingTrigger  = getResources().getInteger(R.integer.auto_renew_trigger_days_remaining);
    Long                    daysRemainingProgress = daysRemaining;

    if (daysRemaining < 0) {
      daysRemaining         = 0L;
      daysRemainingProgress = 0L;
    }
    else if (daysRemaining > 365)
      daysRemainingProgress = 365L;

    daysRemainingView.setText(daysRemaining.toString());
    progressBarView.setProgress(1.0F - ((float) daysRemainingProgress / 365.0F));
    editAutoRenewButton.setText(R.string.button_edit_payment_details);

    if (!autoRenewEnabled) {
      autoRenewView.setTextColor(getResources().getColor(R.color.disaled_grey));
      autoRenewView.setText(R.string.auto_renew_disabled);
    }
    else if (!lastChargeFailed && daysRemaining < daysRemainingTrigger) {
      autoRenewView.setTextColor(getResources().getColor(R.color.success_green));
      autoRenewView.setText(getString(R.string.processing_payment));
    }
    else if (!lastChargeFailed) {
      autoRenewView.setTextColor(getResources().getColor(R.color.success_green));
      autoRenewView.setText(getString(R.string.auto_renew_enabled));
    }
    else {
      autoRenewView.setTextColor(getResources().getColor(R.color.error_red));
      autoRenewView.setText(getString(R.string.auto_renew_error));
    }

    if (subscriptions.isPresent())
      handleUpdateSubscriptionHistory(subscriptions.get());
  }

  private void handleRefreshSubscriptionDetails() {
    asyncTask = new AsyncTask<String, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleRefreshSubscriptionDetails()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result = new Bundle();

        try {

          RegistrationApi registrationApi = new RegistrationApi(getBaseContext());
                          flockAccount    =  Optional.of(registrationApi.getAccount(davAccount));
                          cardInformation = registrationApi.getCard(davAccount);

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (RegistrationApiException e) {
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
        asyncTask = null;
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          handleUpdateUi(flockAccount.get().getDaysRemaining(),
                         flockAccount.get().getAutoRenewEnabled(),
                         flockAccount.get().getLastStripeChargeFailed(),
                         Optional.of(flockAccount.get().getSubscriptions()));
        }
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
      }
    }.execute();
  }

  private void handleInitSubscriptionDetailsCache() {
    asyncTask = new AsyncTask<String, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleInitSubscriptionDetailsCache()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result = new Bundle();

        try {

          flockAccount = Optional.of(new RegistrationApi(getBaseContext()).getAccount(davAccount));
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (RegistrationApiException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          handleUpdateUi(flockAccount.get().getDaysRemaining(),
              flockAccount.get().getAutoRenewEnabled(),
              flockAccount.get().getLastStripeChargeFailed(),
              Optional.of(flockAccount.get().getSubscriptions()));
          handleStartPerpetualRefresh();
        }
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
      }
    }.execute();
  }

  private final Runnable refreshUiRunnable = new Runnable() {

    @Override
    public void run() {
      if (asyncTask == null || asyncTask.isCancelled())
        handleRefreshSubscriptionDetails();
    }

  };

  private void handleStartPerpetualRefresh() {
              intervalTimer = new Timer();
    TimerTask uiTask        = new TimerTask() {

      @Override
      public void run() {
        uiHandler.post(refreshUiRunnable);
      }

    };

    intervalTimer.schedule(uiTask, 0, 15000);
  }

}
