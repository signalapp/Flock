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
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.model.GooglePlan;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.sync.account.AccountStore;
import org.anhonesteffort.flock.sync.account.AccountSyncScheduler;
import org.anhonesteffort.flock.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * rhodey
 */
public class SubscriptionGoogleFragment extends Fragment {

  private static final String TAG = "org.anhonesteffort.flock.SubscriptionGoogleFragment";

  private static final int    REQUEST_CODE_GOOGLE_FRAGMENT = 1024;
  public  static final String SKU_YEARLY_SUBSCRIPTION      = "flock_subscription_yearly";
  public  static final String PURCHASE_TOKEN_HACK          = "flock_subscription_yearly_hack";
  public  static final String PRODUCT_TYPE_SUBSCRIPTION    = "subs";

  private ManageSubscriptionActivity subscriptionActivity;
  private AsyncTask                  asyncTask;
  private AsyncTask                  recurringTask;
  private AlertDialog                alertDialog;

  private final Handler uiHandler          = new Handler();
  private       Timer   intervalTimer      = new Timer();
  private       long    daysTillNextCharge = -1;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof ManageSubscriptionActivity)
      this.subscriptionActivity = (ManageSubscriptionActivity) activity;
    else
      throw new ClassCastException(activity.toString() + " not what I expected D: !");
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    View     view            = inflater.inflate(R.layout.fragment_subscription_google, container, false);
    TextView costPerYearView = (TextView) view.findViewById(R.id.cost_per_year);
    double   costPerYearUsd  = (double) getResources().getInteger(R.integer.cost_per_year_usd);

    costPerYearView.setText(getString(R.string.usd_per_year, costPerYearUsd));

    return view;
  }

  private void handleStartGoogleSubscription() {
    if (subscriptionActivity.activityRequestCode.isPresent() &&
        subscriptionActivity.activityRequestCode.get().equals(REQUEST_CODE_GOOGLE_FRAGMENT))
    {
      if (subscriptionActivity.activityResultCode.isPresent() &&
          subscriptionActivity.activityResultData.isPresent())
      {
        handlePurchaseIntentResult(subscriptionActivity.activityResultCode.get(),
                                   subscriptionActivity.activityResultData.get());
      }
      else {
        Log.e(TAG, "RESULT CODE OR DATA IS MISSING! D:");
        subscriptionActivity.handleClearActivityResult();
        subscriptionActivity.finish();
      }
    }
    else
      handleLoadSkuList(PRODUCT_TYPE_SUBSCRIPTION);
  }

  private void handleGoogleSubscriptionActive() {
    TextView statusView = (TextView) subscriptionActivity.findViewById(R.id.google_subscription_status);

    if (daysTillNextCharge >= 0)
      statusView.setText(getString(R.string.your_subscription_is_active_well_charge_you_again_in_days, daysTillNextCharge));
    else
      statusView.setText(getString(R.string.your_subscription_is_active_well_charge_you_again_in_days, -1));

    subscriptionActivity.findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handlePromptCancelSubscription();
      }
    });
  }

  private void handleUpdateUi() {
    if (subscriptionActivity.optionsMenu != null)
      subscriptionActivity.optionsMenu.findItem(R.id.button_send_bitcoin).setVisible(true);

    int planType = AccountStore.getSubscriptionPlanType(subscriptionActivity);

    switch (planType) {
      case SubscriptionPlan.PLAN_TYPE_NONE:
        handleStartGoogleSubscription();
        break;

      case SubscriptionPlan.PLAN_TYPE_GOOGLE:
        handleGoogleSubscriptionActive();
        break;

      default:
        Log.e(TAG, "active subscription is not google or none, how did we get here?!");
        subscriptionActivity.updateFragmentWithPlanType(planType);
        break;
    }

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

    if (recurringTask != null && !recurringTask.isCancelled())
      recurringTask.cancel(true);

    if (alertDialog != null)
      alertDialog.dismiss();

    if (intervalTimer != null)
      intervalTimer.cancel();
  }

  private void handleSkuListLoaded(List<String> skuList) {
    if (!skuList.contains(SKU_YEARLY_SUBSCRIPTION)) {
      Log.e(TAG, "flock yearly subscription sku not in returned sku list.");
      Toast.makeText(subscriptionActivity,
                     R.string.google_play_error_please_update_google_play_services,
                     Toast.LENGTH_LONG).show();
      return;
    }

    handleRequestPurchaseIntent(PRODUCT_TYPE_SUBSCRIPTION, SKU_YEARLY_SUBSCRIPTION);
  }

  private void handleStartPurchaseIntent(PendingIntent purchaseIntent) {
    try {

      subscriptionActivity.startIntentSenderForResult(
          purchaseIntent.getIntentSender(), REQUEST_CODE_GOOGLE_FRAGMENT, new Intent(), 0, 0, 0
      );

    } catch (IntentSender.SendIntentException e) {
      Log.e(TAG, "lol wut?", e);
      Toast.makeText(subscriptionActivity,
                     R.string.google_play_error_please_update_google_play_services,
                     Toast.LENGTH_LONG).show();
    }
  }

  protected void handlePurchaseIntentResult(int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      if (data.getIntExtra("RESPONSE_CODE", 0) == 0)
        handleItemPurchased(data);
      else
        handleItemPurchaseFailed(data);
    }
    else
      handleItemPurchaseCanceled();

    subscriptionActivity.handleClearActivityResult();
  }

  private void handleItemPurchased(Intent data) {
    try {

      String     purchaseData   = data.getStringExtra("INAPP_PURCHASE_DATA");
      JSONObject purchaseObject = new JSONObject(purchaseData);
      String     productSku     = purchaseObject.getString("productId");
      String     purchaseToken  = purchaseObject.getString("purchaseToken");

      if (productSku != null && purchaseToken != null) {
        GooglePlan newGooglePlan = new GooglePlan(
            subscriptionActivity.davAccount.getUserId(), SKU_YEARLY_SUBSCRIPTION,
            PURCHASE_TOKEN_HACK, Long.MAX_VALUE
        );

        AccountStore.setSubscriptionPlan(subscriptionActivity, newGooglePlan);
        new AccountSyncScheduler(subscriptionActivity).requestSync();

        daysTillNextCharge = 365;
        Toast.makeText(subscriptionActivity, R.string.thanks, Toast.LENGTH_SHORT).show();
        handleUpdateUi();
        return;
      }

    }
    catch (JSONException e) {
      Log.e(TAG, "no :(", e);
    } catch (JsonProcessingException e) {
      ErrorToaster.handleShowError(subscriptionActivity, e);
    }

    Toast.makeText(subscriptionActivity,
                   R.string.google_play_error_please_update_google_play_services,
                   Toast.LENGTH_LONG).show();
  }

  private void handleItemPurchaseFailed(Intent data) {
    Log.w(TAG, "handleItemPurchaseFailed :|");
    Toast.makeText(subscriptionActivity,
                   R.string.purchase_failed_check_your_google_wallet,
                   Toast.LENGTH_LONG).show();
    subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_NONE);
  }

  private void handleItemPurchaseCanceled() {
    subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_NONE);
  }

  private void handlePromptCancelSubscription() {
    AlertDialog.Builder builder = new AlertDialog.Builder(subscriptionActivity);

    builder.setTitle(R.string.cancel_subscription);
    builder.setMessage(R.string.are_you_sure_you_want_to_cancel_your_flock_subscription);
    builder.setNegativeButton(R.string.no, null);
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        handleCancelSubscription();
      }

    });

    alertDialog = builder.show();
  }

  private void handleRequestPurchaseIntent(final String productType,
                                           final String productSku)
  {
    if (asyncTask != null)
      return;

    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleRequestPurchaseIntent");
        subscriptionActivity.setProgressBarIndeterminateVisibility(true);
        subscriptionActivity.setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        if (subscriptionActivity.billingService == null) {
          Log.e(TAG, "billing service is null");
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
          return result;
        }

        String developerPayload = Base64.encodeBytes(
            subscriptionActivity.davAccount.getUserId().toUpperCase().getBytes()
        );

        try {

          Bundle intentBundle = subscriptionActivity.billingService
              .getBuyIntent(3, SubscriptionGoogleFragment.class.getPackage().getName(),
                            productSku, productType, developerPayload);

          if (intentBundle.getParcelable("BUY_INTENT") != null) {
            result.putParcelable("BUY_INTENT", intentBundle.getParcelable("BUY_INTENT"));
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

            return result;
          }
          Log.e(TAG, "buy intent is null");

        } catch (RemoteException e) {
          Log.e(TAG, "caught remote exception while getting buy intent", e);
        }

        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTask = null;
        subscriptionActivity.setProgressBarIndeterminateVisibility(false);
        subscriptionActivity.setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleStartPurchaseIntent((PendingIntent) result.getParcelable("BUY_INTENT"));
        else
          ErrorToaster.handleDisplayToastBundledError(subscriptionActivity, result);
      }
    }.execute();
  }

  private void handleLoadSkuList(final String productType) {
    if (asyncTask != null)
      return;

    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleLoadSkuList");
        subscriptionActivity.setProgressBarIndeterminateVisibility(true);
        subscriptionActivity.setProgressBarVisibility(true);
      }

      private ArrayList<String> getSkuDetails(ArrayList<String> skuList,
                                              Bundle            result)
          throws RemoteException
      {
        Bundle skuBundle = new Bundle();
        skuBundle.putStringArrayList("ITEM_ID_LIST", skuList);

        Bundle skuDetails = subscriptionActivity.billingService
            .getSkuDetails(3, SubscriptionGoogleFragment.class.getPackage().getName(),
                           productType, skuBundle);

        if (skuDetails.getInt("RESPONSE_CODE") == 0)
          return skuDetails.getStringArrayList("DETAILS_LIST");
        else {
          Log.e(TAG, "sku details response code is " + skuDetails.getInt("RESPONSE_CODE"));
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
          return new ArrayList<String>(0);
        }
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        if (subscriptionActivity.billingService == null) {
          Log.e(TAG, "billing service is null");
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
          return result;
        }

        ArrayList<String> skuQueryList = new ArrayList<String>(2);
        skuQueryList.add(SKU_YEARLY_SUBSCRIPTION);

        try {

          List<String>      skuDetails = getSkuDetails(skuQueryList, result);
          ArrayList<String> skuList    = new ArrayList<String>();

          if (result.getInt(ErrorToaster.KEY_STATUS_CODE, -1) != -1)
            return result;

          for (String thisResponse : skuDetails) {
            JSONObject productObject = new JSONObject(thisResponse);
            skuList.add(productObject.getString("productId"));
          }

          result.putStringArrayList("SKU_LIST", skuList);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (JSONException e) {
          Log.e(TAG, "error parsing sku details", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
        } catch (RemoteException e) {
          Log.e(TAG, "error parsing sku details", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTask = null;
        subscriptionActivity.setProgressBarIndeterminateVisibility(false);
        subscriptionActivity.setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleSkuListLoaded(result.getStringArrayList("SKU_LIST"));
        else
          ErrorToaster.handleDisplayToastBundledError(subscriptionActivity, result);
      }
    }.execute();
  }

  private void handleCancelSubscription() {
    if (asyncTask != null)
      return;

    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleCancelSubscription()");
        subscriptionActivity.setProgressBarIndeterminateVisibility(true);
        subscriptionActivity.setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        try {
          RegistrationApi registrationApi = new RegistrationApi(subscriptionActivity);

          registrationApi.cancelSubscription(subscriptionActivity.davAccount);
          AccountStore.setSubscriptionPlan(subscriptionActivity, SubscriptionPlan.PLAN_NONE);
          AccountStore.setAutoRenew(subscriptionActivity, false);

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (RegistrationApiException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (JsonProcessingException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        asyncTask = null;
        subscriptionActivity.setProgressBarIndeterminateVisibility(false);
        subscriptionActivity.setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          Toast.makeText(subscriptionActivity, R.string.subscription_canceled, Toast.LENGTH_SHORT).show();
          subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_NONE);
        }

        else {
          ErrorToaster.handleDisplayToastBundledError(subscriptionActivity, result);
          handleUpdateUi();
        }
      }
    }.execute();
  }

  private void handleRefreshDaysTillCharge() {
    if (recurringTask != null)
      return;

    recurringTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleRefreshDaysTillCharge");
        subscriptionActivity.setProgressBarIndeterminateVisibility(true);
        subscriptionActivity.setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result = new Bundle();

        if (subscriptionActivity.billingService == null) {
          Log.e(TAG, "billing service is null");
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
          return result;
        }

        try {

          Bundle ownedItems = subscriptionActivity.billingService
              .getPurchases(3, SubscriptionGoogleFragment.class.getPackage().getName(),
                               PRODUCT_TYPE_SUBSCRIPTION, null);

          if (ownedItems.getInt("RESPONSE_CODE") != 0) {
            Log.e(TAG, "owned items response code is " + ownedItems.getInt("RESPONSE_CODE"));
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
            return result;
          }

          ArrayList<String> purchaseDataList =
              ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

          for (int i = 0; i < purchaseDataList.size(); ++i) {
            JSONObject productObject = new JSONObject(purchaseDataList.get(i));
            if (productObject.getString("productId").equals(SKU_YEARLY_SUBSCRIPTION)) {
              long purchaseTime    = productObject.getLong("purchaseTime");
              long msSincePurchase = new Date().getTime() - purchaseTime;
              if (msSincePurchase < 0)
                msSincePurchase = 0;

              daysTillNextCharge = 365 - (msSincePurchase / 1000 / 60 / 60 / 24);
              if (daysTillNextCharge < 0)
                daysTillNextCharge = 0;
            }
          }

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (RemoteException e) {
          Log.e(TAG, "error while getting owned items", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
        } catch (JSONException e) {
          Log.e(TAG, "error while getting owned items", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_GOOGLE_PLAY_ERROR);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        recurringTask = null;
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
      if (recurringTask == null || recurringTask.isCancelled()) {
        if (isAdded())
          handleRefreshDaysTillCharge();
      }
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

    intervalTimer.schedule(uiTask, 0, 10000);
  }

}
