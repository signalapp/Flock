/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.anhonesteffort.flock;

import android.app.Activity;
import android.content.Intent;
import android.content.SyncResult;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.sync.account.AccountStore;
import org.anhonesteffort.flock.sync.account.AccountSyncWorker;

import java.util.Timer;
import java.util.TimerTask;

import de.passsy.holocircularprogressbar.HoloCircularProgressBar;

/**
 * rhodey
 */
public class UnsubscribedFragment extends Fragment {

  private static final String TAG = "org.anhonesteffort.flock.UnsubscribedFragment";

  private final Handler uiHandler     = new Handler();
  private       Timer   intervalTimer = new Timer();

  private ManageSubscriptionActivity subscriptionActivity;
  private AsyncTask                  asyncTask;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof ManageSubscriptionActivity)
      this.subscriptionActivity = (ManageSubscriptionActivity) activity;
    else
      throw new ClassCastException(activity.toString() + " not what I expected D: !");
  }

  private void initButtons(View view) {
    view.findViewById(R.id.button_google_play).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_GOOGLE);
      }
    });
    view.findViewById(R.id.button_credit_card).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_STRIPE);
      }
    });
    view.findViewById(R.id.button_send_bitcoin).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent sendBitcoin = new Intent(subscriptionActivity, SendBitcoinActivity.class);
        sendBitcoin.putExtra(SendBitcoinActivity.KEY_DAV_ACCOUNT_BUNDLE,
                             subscriptionActivity.davAccount.toBundle());
        startActivity(sendBitcoin);
      }
    });
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    View view = inflater.inflate(R.layout.fragment_subscription_unsubscribed, container, false);

    initButtons(view);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();

    handleUpdateUi();
    handleStartPerpetualRefresh();
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);

    if (intervalTimer != null)
      intervalTimer.cancel();

    subscriptionActivity.setProgressBarIndeterminateVisibility(false);
    subscriptionActivity.setProgressBarVisibility(false);
  }

  private void handleUpdateDaysRemainingUi(Long daysRemaining) {
    TextView                daysRemainingView = (TextView)subscriptionActivity.findViewById(R.id.days_remaining);
    HoloCircularProgressBar progressBarView   = (HoloCircularProgressBar)subscriptionActivity.findViewById(R.id.days_remaining_progress);
    Long                    daysProgress      = daysRemaining;

    if (daysRemaining < 0) {
      daysRemaining = 0L;
      daysProgress  = 0L;
    }
    else if (daysRemaining > 365)
      daysProgress = 365L;

    daysRemainingView.setText(daysRemaining.toString());
    progressBarView.setProgress(1.0F - ((float) daysProgress / 365.0F));
  }

  private void handleUpdateUi() {
    int            subscriptionType = AccountStore.getSubscriptionPlanType(subscriptionActivity);
    Optional<Long> daysRemaining    = AccountStore.getDaysRemaining(subscriptionActivity);

    switch (subscriptionType) {
      case SubscriptionPlan.PLAN_TYPE_GOOGLE:
        subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_GOOGLE);
        break;

      case SubscriptionPlan.PLAN_TYPE_STRIPE:
        subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_STRIPE);
        break;

      default:
        if (daysRemaining.isPresent())
          handleUpdateDaysRemainingUi(daysRemaining.get());
        else
          Log.w(TAG, "days remaining not present in SubscriptionStore :|");
        break;
    }
  }

  private void handleRefreshSubscriptionStore() {
    asyncTask = new AsyncTask<String, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleRefreshSubscriptionStore()");
        subscriptionActivity.setProgressBarIndeterminateVisibility(true);
        subscriptionActivity.setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result = new Bundle();

        try {

          new AccountSyncWorker(
              subscriptionActivity,
              subscriptionActivity.davAccount,
              null,
              new SyncResult()
          ).run();

        } catch (RegistrationApiException e) {
          ErrorToaster.handleBundleError(e, result);
          return result;
        }

        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTask = null;
        subscriptionActivity.setProgressBarIndeterminateVisibility(false);
        subscriptionActivity.setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleUpdateUi();
        else
          ErrorToaster.handleDisplayToastBundledError(subscriptionActivity, result);
      }
    }.execute();
  }

  private final Runnable refreshUiRunnable = new Runnable() {

    @Override
    public void run() {
      if (asyncTask == null || asyncTask.isCancelled())
        handleRefreshSubscriptionStore();
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
