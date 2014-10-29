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

package org.anhonesteffort.flock.sync.account;

import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Optional;
import com.stripe.exception.CardException;

import org.anhonesteffort.flock.SubscriptionGoogleFragment;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.OwsRegistration;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiClientException;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.ResourceAlreadyExistsException;
import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.GooglePlan;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.sync.SyncWorker;
import org.anhonesteffort.flock.sync.SyncWorkerUtil;
import org.anhonesteffort.flock.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * rhodey
 */
public class AccountSyncWorker implements SyncWorker {

  private static final String TAG = "org.anhonesteffort.flock.sync.account.AccountSyncWorker";

  private final Context              context;
  private final DavAccount           account;
  private final IInAppBillingService billingService;
  private final SyncResult           result;
  private final RegistrationApi      registration;

  public AccountSyncWorker(Context              context,
                           DavAccount           account,
                           IInAppBillingService billingService,
                           SyncResult           syncResult)
  {
    this.context        = context;
    this.account        = account;
    this.result         = syncResult;
    this.billingService = billingService;

    registration = new RegistrationApi(context);
  }

  private List<JSONObject> getPurchasedGoogleSubscriptions() {
    List<JSONObject> subscriptions = new LinkedList<>();

    if (billingService == null) {
      Log.e(TAG, "billing service is null");
      return subscriptions;
    }

    try {

      Bundle ownedItems = billingService
          .getPurchases(3, SubscriptionGoogleFragment.class.getPackage().getName(),
              SubscriptionGoogleFragment.PRODUCT_TYPE_SUBSCRIPTION, null);

      ArrayList<String> purchaseDataList =
          ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

      for (int i = 0; i < purchaseDataList.size(); ++i) {
        JSONObject productObject = new JSONObject(purchaseDataList.get(i));
        if (productObject.getString("productId") != null)
          subscriptions.add(productObject);
      }

    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (JSONException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }

    return subscriptions;
  }

  private void handleCancelSubscriptionWithServerIfNotRenewing() {
    Log.d(TAG, "handleCancelSubscriptionWithServerIfNotRenewing");

    try {

      if (!registration.getIsPlanAutoRenewing(account)) {
        Log.d(TAG, "server says our plan is not auto renewing, must have been canceled through" +
                   " google play store, will now cancel with registration server.");

        registration.cancelSubscription(account);
        AccountStore.setSubscriptionPlan(context, SubscriptionPlan.PLAN_NONE);
        AccountStore.setAutoRenew(context, false);
      }
      else
        Log.w(TAG, "active subscription is GOOGLE but google play returned no matching purchases, why?");

    } catch (RegistrationApiException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (JsonProcessingException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  private void handleLocallyCanceledGoogleSubscriptions() {
    Log.d(TAG, "handleLocallyCanceledGoogleSubscriptions");

    if (AccountStore.getSubscriptionPlanType(context) != SubscriptionPlan.PLAN_TYPE_GOOGLE)
      return;

    boolean havePlanWithPlayServices = false;
    for (JSONObject googleSubscription : getPurchasedGoogleSubscriptions()) {
      try {

        String subscriptionSku = googleSubscription.getString("productId");
        if (subscriptionSku.equals(SubscriptionGoogleFragment.SKU_YEARLY_SUBSCRIPTION)) {
          havePlanWithPlayServices = true;
          break;
        }

      } catch (JSONException e) {
        SyncWorkerUtil.handleException(context, e, result);
        return;
      }
    }

    if (!havePlanWithPlayServices && billingService != null)
      handleCancelSubscriptionWithServerIfNotRenewing();
  }

  private void handlePutGoogleSubscriptionToServer(JSONObject subscription) {
    Log.d(TAG, "handlePutGoogleSubscriptionToServer");

    try {

      String     productSku    = subscription.getString("productId");
      String     purchaseToken = subscription.getString("purchaseToken");
      GooglePlan plan          = new GooglePlan(
          account.getUserId(), productSku, purchaseToken, Long.MAX_VALUE
      );

      try {

        registration.putNewGooglePlan(account, productSku, purchaseToken);
        AccountStore.setSubscriptionPlan(context, plan);

      } catch (ResourceAlreadyExistsException e) {
        Log.w(TAG, "thought we were putting new google plan but one already exists.");
      }

      AccountStore.setAutoRenew(context, true);

    } catch (RegistrationApiException e) {

      if (e instanceof RegistrationApiClientException) {
        RegistrationApiClientException ex = (RegistrationApiClientException) e;

        if (ex.getStatus() == OwsRegistration.STATUS_PAYMENT_REQUIRED)
          Log.w(TAG, "thought we were putting new google plan but server says it is bad", e);
        else
          SyncWorkerUtil.handleException(context, e, result);
      }
      else
        SyncWorkerUtil.handleException(context, e, result);

    } catch (JSONException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (JsonProcessingException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  private void handleNewGoogleSubscriptions() {
    Log.d(TAG, "handleNewGoogleSubscriptions");

    try {

      JSONObject       newGooglePlan = null;
      SubscriptionPlan currentPlan   = AccountStore.getSubscriptionPlan(context);

      if (currentPlan.getPlanType() != SubscriptionPlan.PLAN_TYPE_GOOGLE &&
          currentPlan.getPlanType() != SubscriptionPlan.PLAN_TYPE_NONE)
      {
        return;
      }

      for (JSONObject purchasedGoogleSubscription : getPurchasedGoogleSubscriptions()) {
        try {

          String productSku              = purchasedGoogleSubscription.getString("productId");
          String encodedDeveloperPayload = purchasedGoogleSubscription.getString("developerPayload");
          Log.d(TAG, "GOOGLE THINKS WE PURCHASED THIS >> " + " - " + productSku);

          if (productSku.equals(SubscriptionGoogleFragment.SKU_YEARLY_SUBSCRIPTION)) {
            if (encodedDeveloperPayload != null) {

              String developerPayload = new String(Base64.decode(encodedDeveloperPayload));
              if (developerPayload.toUpperCase().equals(account.getUserId().toUpperCase())) {

                switch (currentPlan.getPlanType()) {
                  case SubscriptionPlan.PLAN_TYPE_NONE:
                    newGooglePlan = purchasedGoogleSubscription;
                    break;

                  case SubscriptionPlan.PLAN_TYPE_GOOGLE:
                    GooglePlan currentGooglePlan = (GooglePlan) currentPlan;
                    if (currentGooglePlan.getPurchaseToken().equals(SubscriptionGoogleFragment.PURCHASE_TOKEN_HACK))
                      newGooglePlan = purchasedGoogleSubscription;
                    break;
                }

              }
              else
                Log.w(TAG, "found google play subscription belonging to account other than this, won't put to server.");
            }
            else
              Log.w(TAG, "found google play subscription with null developer payload, won't put to server.");
          }

        } catch (JSONException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (IOException e) {
          SyncWorkerUtil.handleException(context, e, result);
        }
      }

      if (newGooglePlan != null)
        handlePutGoogleSubscriptionToServer(newGooglePlan);

    } catch (JsonParseException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  private void handleMigrateToStripePlanIfAutoRenewAndNoPlan(AugmentedFlockAccount flockAccount) {
    if (!flockAccount.getAutoRenewEnabled() ||
        !flockAccount.getSubscriptionPlan().getPlanType().equals(SubscriptionPlan.PLAN_TYPE_NONE))
    {
      return;
    }

    try {

      registration.migrateBillingToStripeSubscriptionModel(account);

    } catch (CardException e) {
      Log.e(TAG, "tried to migrate account to stripe plan and got CardException", e);
      SyncWorkerUtil.handleException(context, e, result);
    } catch (RegistrationApiException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  private Optional<AugmentedFlockAccount> handleUpdateFlockAccountCache() {
    Log.d(TAG, "handleUpdateFlockAccountCache");

    AugmentedFlockAccount flockAccount = null;

    try {

      flockAccount = registration.getAccount(account);
      AccountStore.updateStore(context, flockAccount);

    } catch (RegistrationApiException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (JsonProcessingException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }

    return Optional.fromNullable(flockAccount);
  }

  private void handleUpdateCardInformationCache() {
    Log.d(TAG, "handleUpdateCardInformationCache");

    try {

      AccountStore.setCardInformation(context, registration.getCard(account));

    } catch (RegistrationApiException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  @Override
  public void run() {
    handleLocallyCanceledGoogleSubscriptions();
    handleNewGoogleSubscriptions();

    Optional<AugmentedFlockAccount> flockAccount = handleUpdateFlockAccountCache();

    if (flockAccount.isPresent())
      handleMigrateToStripePlanIfAutoRenewAndNoPlan(flockAccount.get());

    handleUpdateCardInformationCache();
  }

  @Override
  public void cleanup() {

  }

}
