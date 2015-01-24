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
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Optional;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Token;

import org.anhonesteffort.flock.registration.OwsRegistration;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.model.FlockCardInformation;
import org.anhonesteffort.flock.registration.model.StripePlan;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.sync.account.AccountStore;
import org.anhonesteffort.flock.sync.account.AccountSyncScheduler;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.HashMap;

/**
 * rhodey
 */
public class SubscriptionStripeFragment extends Fragment {

  private static final String TAG = "org.anhonesteffort.flock.UnsubscribedFragment";

  private ManageSubscriptionActivity subscriptionActivity;
  private AsyncTask                  asyncTask;
  private AlertDialog                alertDialog;

  private Optional<FlockCardInformation> cardInformation;

  private TextWatcher cardNumberTextWatcher;
  private TextWatcher cardExpirationTextWatcher;
  private int         lastCardExpirationLength = 0;

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
    View     view            = inflater.inflate(R.layout.fragment_subscription_card, container, false);
    TextView costPerYearView = (TextView) view.findViewById(R.id.cost_per_year);
    double   costPerYearUsd  = (double) getResources().getInteger(R.integer.cost_per_year_usd);

    costPerYearView.setText(getString(R.string.usd_per_year, costPerYearUsd));

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();

    handleUpdateUi();
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);

    if (alertDialog != null)
      alertDialog.dismiss();

    ((EditText) subscriptionActivity.findViewById(R.id.card_number)).setText("");
    ((EditText) subscriptionActivity.findViewById(R.id.card_expiration)).setText("");
    ((EditText) subscriptionActivity.findViewById(R.id.card_cvc)).setText("");
  }

  private void handleUpdateUiForCreatingSubscription() {
    TextView statusView = (TextView) subscriptionActivity.findViewById(R.id.card_subscription_status);
    statusView.setVisibility(View.GONE);

    Button buttonCancel            = (Button) subscriptionActivity.findViewById(R.id.button_card_cancel);
    Button buttonStartSubscription = (Button) subscriptionActivity.findViewById(R.id.button_card_action);

    buttonCancel.setVisibility(View.GONE);

    buttonStartSubscription.setText(R.string.start_subscription);
    buttonStartSubscription.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleVerifyCardAndPutToServer();
      }
    });

    initCardNumberHelper();
    initCardExpirationHelper();
  }

  private void handleSetupFormForEditingCard() {
    TextView statusView = (TextView) subscriptionActivity.findViewById(R.id.card_subscription_status);
    statusView.setVisibility(View.VISIBLE);

    if (AccountStore.getLastChargeFailed(subscriptionActivity)) {
      statusView.setText(R.string.payment_failed);
      statusView.setTextColor(getResources().getColor(R.color.error_red));
    }
    else {
      statusView.setText(R.string.subscription_is_active);
      statusView.setTextColor(getResources().getColor(R.color.success_green));
    }

    EditText cardNumberView     = (EditText) subscriptionActivity.findViewById(R.id.card_number);
    EditText cardExpirationView = (EditText) subscriptionActivity.findViewById(R.id.card_expiration);

    if (cardInformation.isPresent()) {
      if (StringUtils.isEmpty(cardNumberView.getText().toString()))
        cardNumberView.setText("**** **** **** " + cardInformation.get().getCardLastFour());

      if (StringUtils.isEmpty(cardExpirationView.getText().toString()))
        cardExpirationView.setText(cardInformation.get().getCardExpiration());
    }

    Button buttonCancel            = (Button) subscriptionActivity.findViewById(R.id.button_card_cancel);
    Button buttonStartSubscription = (Button) subscriptionActivity.findViewById(R.id.button_card_action);

    buttonCancel.setVisibility(View.VISIBLE);
    buttonCancel.setText(R.string.cancel_subscription);
    buttonCancel.setBackgroundColor(getResources().getColor(R.color.error_red));
    buttonCancel.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handlePromptCancelSubscription();
      }
    });

    buttonStartSubscription.setText(R.string.save_card);
    buttonStartSubscription.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleVerifyCardAndPutToServer();
      }
    });

    initCardNumberHelper();
    initCardExpirationHelper();
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

  private void handleUpdateUi() {
    int planType        = AccountStore.getSubscriptionPlanType(subscriptionActivity);
        cardInformation = AccountStore.getCardInformation(subscriptionActivity);

    switch (planType) {
      case SubscriptionPlan.PLAN_TYPE_GOOGLE:
        Log.e(TAG, "is subscribed using google, how did we get here?");
        subscriptionActivity.updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_GOOGLE);
        break;

      case SubscriptionPlan.PLAN_TYPE_STRIPE:
        if (subscriptionActivity.optionsMenu != null)
          subscriptionActivity.optionsMenu.findItem(R.id.button_send_bitcoin).setVisible(true);

        handleSetupFormForEditingCard();
        break;

      default:
        if (subscriptionActivity.optionsMenu != null)
          subscriptionActivity.optionsMenu.findItem(R.id.button_send_bitcoin).setVisible(false);

        handleUpdateUiForCreatingSubscription();
        break;
    }
  }

  private void initCardNumberHelper() {
    final EditText cardNumberView      = (EditText) subscriptionActivity.findViewById(R.id.card_number);
    final EditText cardExpirationView  = (EditText) subscriptionActivity.findViewById(R.id.card_expiration);

    cardNumberView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (cardNumberView.getText() != null &&
            cardNumberView.getText().toString().contains("*"))
        {
          cardNumberView.setText("");
        }

        return false;
      }
    });

    if (cardNumberTextWatcher != null)
      cardNumberView.removeTextChangedListener(cardNumberTextWatcher);

    cardNumberTextWatcher = new TextWatcher() {

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        String cardNumber          = s.toString().replace(" ", "");
        String formattedCardNumber = "";

        for (int i = 0; i < cardNumber.length(); i++) {
          if (i > 0 && i % 4 == 0)
            formattedCardNumber += " ";

          formattedCardNumber += cardNumber.charAt(i);
        }

        cardNumberView.removeTextChangedListener(this);
        cardNumberView.setText(formattedCardNumber);
        cardNumberView.setSelection(formattedCardNumber.length());
        cardNumberView.addTextChangedListener(this);

        if (!cardNumber.contains("*") && cardNumber.length() == 16)
          cardExpirationView.requestFocus();
      }
    };

    cardNumberView.addTextChangedListener(cardNumberTextWatcher);
  }

  private void initCardExpirationHelper() {
    final EditText cardExpirationView = (EditText) subscriptionActivity.findViewById(R.id.card_expiration);
    final EditText cardCvcView        = (EditText) subscriptionActivity.findViewById(R.id.card_cvc);

    if (cardExpirationTextWatcher != null)
      cardExpirationView.removeTextChangedListener(cardExpirationTextWatcher);

    cardExpirationTextWatcher = new TextWatcher() {

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        String formattedCardExpiration = s.toString();

        if (lastCardExpirationLength         <= formattedCardExpiration.length() &&
            formattedCardExpiration.length() == 2)
        {
          formattedCardExpiration = formattedCardExpiration + "/";
        }

        lastCardExpirationLength = formattedCardExpiration.length();

        cardExpirationView.removeTextChangedListener(this);
        cardExpirationView.setText(formattedCardExpiration);
        cardExpirationView.setSelection(formattedCardExpiration.length());
        cardExpirationView.addTextChangedListener(this);

        if (formattedCardExpiration.length() == 5)
          cardCvcView.requestFocus();
      }
    };

    cardExpirationView.addTextChangedListener(cardExpirationTextWatcher);
  }

  private void handleVerifyCardAndPutToServer() {
    if (asyncTask != null)
      return;

    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleVerifyCardAndPutToServer()");
        subscriptionActivity.setProgressBarIndeterminateVisibility(true);
        subscriptionActivity.setProgressBarVisibility(true);
      }

      private String handleGetStripeCardTokenId(String cardNumber,
                                                String cardExpiration,
                                                String cardCVC)
          throws StripeException
      {
        String[] expiration      = cardExpiration.split("/");
        Integer  expirationMonth = Integer.valueOf(expiration[0]);
        Integer  expirationYear;

        if (expiration[1].length() == 4)
          expirationYear = Integer.valueOf(expiration[1]);
        else
          expirationYear = Integer.valueOf(expiration[1]) + 2000;

        java.util.Map<String, Object> cardParams  = new HashMap<String, Object>();
        java.util.Map<String, Object> tokenParams = new HashMap<String, Object>();

        cardParams.put("number",    cardNumber.replace(" ", ""));
        cardParams.put("exp_month", expirationMonth);
        cardParams.put("exp_year",  expirationYear);
        cardParams.put("cvc",       cardCVC);

        tokenParams.put("card", cardParams);

        return Token.create(tokenParams, OwsRegistration.STRIPE_PUBLIC_KEY).getId();
      }

      private void handlePutStripeTokenToServer(String stripeTokenId)
          throws IOException, RegistrationApiException
      {
        RegistrationApi registrationApi = new RegistrationApi(subscriptionActivity);
        registrationApi.setStripeCard(subscriptionActivity.davAccount, stripeTokenId);
      }

      private void handleUpdateSubscriptionStore(String cardNumber, String cardExpiration)
          throws JsonProcessingException
      {
        String cardNoSpaces = cardNumber.replace(" ", "");
        String lastFour     = cardNoSpaces.substring(cardNoSpaces.length() - 4);

        FlockCardInformation cardInformation =
            new FlockCardInformation(subscriptionActivity.davAccount.getUserId(), lastFour, cardExpiration);

        StripePlan newStripePlan =
            new StripePlan(subscriptionActivity.davAccount.getUserId(), "nope");

        AccountStore.setLastChargeFailed(subscriptionActivity, false);
        AccountStore.setSubscriptionPlan(subscriptionActivity, newStripePlan);
        AccountStore.setAutoRenew(subscriptionActivity, true);
        AccountStore.setCardInformation(subscriptionActivity, Optional.of(cardInformation));
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result         = new Bundle();
        String cardNumber     = ((TextView) subscriptionActivity.findViewById(R.id.card_number)).getText().toString();
        String cardExpiration = ((TextView) subscriptionActivity.findViewById(R.id.card_expiration)).getText().toString();
        String cardCVC        = ((TextView) subscriptionActivity.findViewById(R.id.card_cvc)).getText().toString();

        if (StringUtils.isEmpty(cardNumber) || cardNumber.contains("*")) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CARD_NUMBER_INVALID);
          return result;
        }

        if (StringUtils.isEmpty(cardExpiration) || cardExpiration.split("/").length != 2) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CARD_EXPIRATION_INVALID);
          return result;
        }

        if (StringUtils.isEmpty(cardCVC) || cardCVC.length() < 1) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CARD_CVC_INVALID);
          return result;
        }

        try {

          String stripeTokenId = handleGetStripeCardTokenId(cardNumber, cardExpiration, cardCVC);
          handlePutStripeTokenToServer(stripeTokenId);
          handleUpdateSubscriptionStore(cardNumber, cardExpiration);

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (CardException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (StripeException e) {
          ErrorToaster.handleBundleError(e, result);
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
          Toast.makeText(subscriptionActivity, R.string.card_verified_and_saved, Toast.LENGTH_LONG).show();
          handleUpdateUi();
        }

        else {
          ErrorToaster.handleDisplayToastBundledError(subscriptionActivity, result);
          handleUpdateUi();
        }
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

          AccountStore.setLastChargeFailed(subscriptionActivity, false);
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
          new AccountSyncScheduler(subscriptionActivity).requestSync();
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
}
