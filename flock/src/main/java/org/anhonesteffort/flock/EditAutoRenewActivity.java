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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Optional;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Token;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.OwsRegistration;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.FlockAccount;
import org.anhonesteffort.flock.registration.model.FlockCardInformation;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.HashMap;

/**
 * Programmer: rhodey
 */
public class EditAutoRenewActivity extends Activity {

  private static final String TAG = "org.anhonesteffort.flock.EditAutoRenewActivity";

  public static final String KEY_DAV_ACCOUNT_BUNDLE      = "KEY_DAV_ACCOUNT_BUNDLE";
  public static final String KEY_FLOCK_ACCOUNT_BUNDLE    = "KEY_FLOCK_ACCOUNT_BUNDLE";
  public static final String KEY_CARD_INFORMATION_BUNDLE = "KEY_CARD_INFORMATION_BUNDLE";

  private DavAccount  davAccount;
  private AsyncTask   asyncTask;
  private TextWatcher cardNumberTextWatcher;
  private TextWatcher cardExpirationTextWatcher;

  private Optional<FlockAccount>         flockAccount    = Optional.absent();
  private Optional<FlockCardInformation> cardInformation = Optional.absent();

  private int lastCardExpirationLength = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.activity_edit_auto_renew);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.button_edit_payment_details);

    if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
      if (!DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        finish();
        return;
      }

      davAccount      = DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
      flockAccount    = FlockAccount.build(savedInstanceState.getBundle(KEY_FLOCK_ACCOUNT_BUNDLE));
      cardInformation = FlockCardInformation.build(savedInstanceState.getBundle(KEY_CARD_INFORMATION_BUNDLE));
    }
    else if (getIntent().getExtras() != null) {
      if (!DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        finish();
        return;
      }

      davAccount      = DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
      flockAccount    = FlockAccount.build(getIntent().getExtras().getBundle(KEY_FLOCK_ACCOUNT_BUNDLE));
      cardInformation = FlockCardInformation.build(getIntent().getExtras().getBundle(KEY_CARD_INFORMATION_BUNDLE));
    }

    initCostPerYear();
  }

  private void initCostPerYear() {
    TextView costPerYearView = (TextView) findViewById(R.id.cost_per_year);
    double   costPerYearUsd  = (double) getResources().getInteger(R.integer.cost_per_year_usd);

    costPerYearView.setText(getString(R.string.usd_per_year, costPerYearUsd));
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

  @Override
  public void onResume() {
    super.onResume();

    ((CheckBox) findViewById(R.id.checkbox_enable_auto_renew)).setChecked(false);
    handleDisableForm();

    handleGetAccountAndCardAsync();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putBundle(KEY_DAV_ACCOUNT_BUNDLE, davAccount.toBundle());

    if (flockAccount.isPresent())
      savedInstanceState.putBundle(KEY_FLOCK_ACCOUNT_BUNDLE, flockAccount.get().toBundle());

    if (cardInformation.isPresent())
      savedInstanceState.putBundle(KEY_CARD_INFORMATION_BUNDLE, cardInformation.get().toBundle());

    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);

    ((EditText) findViewById(R.id.card_number)).setText("");
    ((EditText) findViewById(R.id.card_expiration)).setText("");
  }

  private void handleDisableForm() {
    Log.d(TAG, "handleDisableForm()");

    Button   verifyAndSaveButton = (Button)   findViewById(R.id.button_verify_and_save);
    EditText cardExpirationView  = (EditText) findViewById(R.id.card_expiration);
    EditText cardNumberView      = (EditText) findViewById(R.id.card_number);
    TextView cardCVCView         = (TextView) findViewById(R.id.card_cvc);

    cardNumberView.setFocusable(false);
    cardNumberView.setFocusableInTouchMode(false);
    cardExpirationView.setFocusable(false);
    cardExpirationView.setFocusableInTouchMode(false);
    cardCVCView.setFocusable(false);
    cardCVCView.setFocusableInTouchMode(false);

    cardNumberView.setText("");
    cardExpirationView.setText("");
    cardCVCView.setText("");

    cardNumberView.setOnTouchListener(null);
    cardExpirationView.setOnTouchListener(null);
    cardCVCView.setOnTouchListener(null);

    verifyAndSaveButton.setText(R.string.button_save);
    verifyAndSaveButton.setOnClickListener(null);
  }

  private void handleInitFormAsAutoRenewDisabled() {
    Log.d(TAG, "handleInitFormFormAsAutoRenewDisabled()");

    Button   verifyAndSaveButton = (Button)   findViewById(R.id.button_verify_and_save);
    EditText cardExpirationView  = (EditText) findViewById(R.id.card_expiration);
    EditText cardNumberView      = (EditText) findViewById(R.id.card_number);
    TextView cardCVCView         = (TextView) findViewById(R.id.card_cvc);

    cardNumberView.setFocusable(false);
    cardNumberView.setFocusableInTouchMode(false);
    cardExpirationView.setFocusable(false);
    cardExpirationView.setFocusableInTouchMode(false);
    cardCVCView.setFocusable(false);
    cardCVCView.setFocusableInTouchMode(false);

    cardNumberView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          ((CheckBox) findViewById(R.id.checkbox_enable_auto_renew)).setChecked(true);

        return false;
      }
    });
    cardExpirationView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          ((CheckBox) findViewById(R.id.checkbox_enable_auto_renew)).setChecked(true);

        return false;
      }
    });
    cardCVCView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          ((CheckBox) findViewById(R.id.checkbox_enable_auto_renew)).setChecked(true);

        return false;
      }
    });

    cardNumberView.setText("");
    cardExpirationView.setText("");
    cardCVCView.setText("");

    verifyAndSaveButton.setText(R.string.button_save);
    verifyAndSaveButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleSaveAutoRenewAndFinish(false);
      }
    });
  }

  private void handleEnableForm() {
    Log.d(TAG, "handleEnableForm()");

    EditText cardExpirationView = (EditText) findViewById(R.id.card_expiration);
    EditText cardNumberView     = (EditText) findViewById(R.id.card_number);
    TextView cardCVCView        = (TextView) findViewById(R.id.card_cvc);

    cardNumberView.setFocusable(true);
    cardNumberView.setFocusableInTouchMode(true);
    cardExpirationView.setFocusable(true);
    cardExpirationView.setFocusableInTouchMode(true);
    cardCVCView.setFocusable(true);
    cardCVCView.setFocusableInTouchMode(true);

    cardNumberView.setOnTouchListener(null);
    cardExpirationView.setOnTouchListener(null);
    cardCVCView.setOnTouchListener(null);
  }

  private void handleInitFormForEditing() {
    Log.d(TAG, "handleInitFormForEditing()");

    Button verifyAndSaveButton = (Button) findViewById(R.id.button_verify_and_save);

    handleEnableForm();
    initCardNumberHelper();
    initCardExpirationHelper();

    verifyAndSaveButton.setText(R.string.button_verify_and_save);
    verifyAndSaveButton.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        handleVerifyCardAndFinish();
      }

    });
  }

  private void handleInitFormForFixingError() {
    Log.d(TAG, "handleInitFormForFixingError()");

    EditText cardNumberView      = (EditText) findViewById(R.id.card_number);
    EditText cardExpirationView  = (EditText) findViewById(R.id.card_expiration);
    TextView cardCVCView         = (TextView) findViewById(R.id.card_cvc);
    Button   verifyAndSaveButton = (Button) findViewById(R.id.button_verify_and_save);

    if (StringUtils.isEmpty(cardNumberView.getText().toString()))
      cardNumberView.setText("**** **** **** " + cardInformation.get().getCardLastFour());

    cardNumberView.setError(getString(R.string.error_your_card_could_not_be_verified));

    if (StringUtils.isEmpty(cardExpirationView.getText().toString()))
      cardExpirationView.setText(cardInformation.get().getCardExpiration());

    cardCVCView.setText("");

    handleEnableForm();
    initCardNumberHelper();
    initCardExpirationHelper();

    verifyAndSaveButton.setText(R.string.button_verify_and_save);
    verifyAndSaveButton.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        handleVerifyCardAndFinish();
      }

    });
  }

  private void handleInitFormForViewingSuccess() {
    Log.d(TAG, "handleInitFormForViewingSuccess()");

    Button   verifyAndSaveButton = (Button)   findViewById(R.id.button_verify_and_save);
    EditText cardNumberView      = (EditText) findViewById(R.id.card_number);
    EditText cardExpirationView  = (EditText) findViewById(R.id.card_expiration);
    TextView cardCVCView         = (TextView) findViewById(R.id.card_cvc);

    handleEnableForm();

    cardNumberView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          handleInitFormForEditing();

        return false;
      }
    });
    cardExpirationView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          handleInitFormForEditing();

        return false;
      }
    });
    cardCVCView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          handleInitFormForEditing();

        return false;
      }
    });

    if (StringUtils.isEmpty(cardNumberView.getText().toString()))
      cardNumberView.setText("**** **** **** " + cardInformation.get().getCardLastFour());

    if (StringUtils.isEmpty(cardExpirationView.getText().toString()))
      cardExpirationView.setText(cardInformation.get().getCardExpiration());

    cardCVCView.setText("");

    verifyAndSaveButton.setText(R.string.button_save);
    verifyAndSaveButton.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        handleSaveAutoRenewAndFinish(true);
      }

    });
  }

  private void initCardNumberHelper() {
    Log.d(TAG, "initCardNumberHelper()");

    final EditText cardNumberView      = (EditText) findViewById(R.id.card_number);
    final EditText cardExpirationView  = (EditText) findViewById(R.id.card_expiration);
    final CheckBox autoRenewIsEnabled = (CheckBox) findViewById(R.id.checkbox_enable_auto_renew);

    cardNumberView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (cardNumberView.getText() != null &&
            cardNumberView.getText().toString().contains("*"))
        {
          cardNumberView.setText("");
        }

        if (autoRenewIsEnabled.isChecked())
          handleInitFormForEditing();

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
    Log.d(TAG, "initCardExpirationHelper()");

    final EditText cardExpirationView = (EditText) findViewById(R.id.card_expiration);
    final EditText cardCvcView        = (EditText) findViewById(R.id.card_cvc);
    final CheckBox autoRenewIsEnabled = (CheckBox) findViewById(R.id.checkbox_enable_auto_renew);

    cardExpirationView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (autoRenewIsEnabled.isChecked())
          handleInitFormForEditing();

        return false;
      }
    });

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

  private void handleRefreshForm(boolean isCallback) {
    Log.d(TAG, "handleRefreshForm() is callback >> " + isCallback);

    CheckBox autoRenewIsEnabled = (CheckBox) findViewById(R.id.checkbox_enable_auto_renew);

    if (!isCallback)
      autoRenewIsEnabled.setChecked(flockAccount.get().getAutoRenewEnabled());

    autoRenewIsEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        handleRefreshForm(true);
      }
    });

    if (!autoRenewIsEnabled.isChecked())
      handleInitFormAsAutoRenewDisabled();
    else {
      if (!flockAccount.get().getLastStripeChargeFailed() && cardInformation.isPresent())
        handleInitFormForViewingSuccess();
      else if (flockAccount.get().getLastStripeChargeFailed() && cardInformation.isPresent())
        handleInitFormForFixingError();
      else
        handleInitFormForEditing();
    }
  }

  private void handleSaveAutoRenewAndFinish(final Boolean autoRenewIsEnabled) {
    if (asyncTask != null)
      return;

    asyncTask = new AsyncTask<Void, Void, Bundle>() {
      boolean autoRenewChanged = false;

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleSaveAutoRenewAndFinish()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle          result          = new Bundle();
        RegistrationApi registrationApi = new RegistrationApi(getBaseContext());

        if (flockAccount.get().getAutoRenewEnabled() == autoRenewIsEnabled) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          return result;
        }

        try {

          registrationApi.setAccountAutoRenew(davAccount, autoRenewIsEnabled);
          flockAccount     = Optional.of((FlockAccount) registrationApi.getAccount(davAccount));
          autoRenewChanged = true;

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
        asyncTask = null;
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          if (autoRenewChanged)
            Toast.makeText(getBaseContext(),
                           R.string.autorenew_saved,
                           Toast.LENGTH_LONG).show();

          finish();
        }
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
      }
    }.execute();
  }

  private void handleVerifyCardAndFinish() {
    if (asyncTask != null)
      return;

    asyncTask = new AsyncTask<Void, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleVerifyCardAndFinish()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
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
          throws IOException, RegistrationApiException, CardException
      {
        RegistrationApi registrationApi = new RegistrationApi(getBaseContext());
        registrationApi.updateAccountStripeCard(davAccount, stripeTokenId);
      }

      @Override
      protected Bundle doInBackground(Void... params) {
        Bundle result         = new Bundle();
        String cardNumber     = ((TextView)findViewById(R.id.card_number)).getText().toString();
        String cardExpiration = ((TextView)findViewById(R.id.card_expiration)).getText().toString();
        String cardCVC        = ((TextView)findViewById(R.id.card_cvc)).getText().toString();

        if (StringUtils.isEmpty(cardNumber)) {
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

          if (!flockAccount.get().getAutoRenewEnabled())
            new RegistrationApi(getBaseContext()).setAccountAutoRenew(davAccount, true);

          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (CardException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (StripeException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (RegistrationApiException e) {
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
          Toast.makeText(getBaseContext(), R.string.card_verified_and_saved, Toast.LENGTH_LONG).show();
          finish();
        }

        else {
          handleInitFormForEditing();
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
        }
      }
    }.execute();
  }

  private void handleGetAccountAndCardAsync() {
    if (flockAccount.isPresent() && cardInformation.isPresent()) {
      handleRefreshForm(false);
      return;
    }

    asyncTask = new AsyncTask<String, Void, Bundle>() {

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleGetAccountAndCardAsync()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle          result           = new Bundle();
        RegistrationApi registrationApi  = new RegistrationApi(getBaseContext());

        try {

          if (!flockAccount.isPresent()) {

            AugmentedFlockAccount augmentedAccount = registrationApi.getAccount(davAccount);
            flockAccount = Optional.of((FlockAccount) augmentedAccount);
          }

          if (!cardInformation.isPresent())
            cardInformation = registrationApi.getCard(davAccount);

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
        asyncTask = null;
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
          handleRefreshForm(false);
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
      }
    }.execute();
  }
}
