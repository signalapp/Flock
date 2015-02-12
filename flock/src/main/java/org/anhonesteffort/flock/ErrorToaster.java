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

import android.content.Context;
import android.content.OperationApplicationException;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.stripe.exception.APIConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.registration.AuthorizationException;
import org.anhonesteffort.flock.registration.PaymentRequiredException;
import org.anhonesteffort.flock.registration.RegistrationApiParseException;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.sync.InvalidRemoteComponentException;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.WebDavConstants;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLException;

/**
 * Programmer: rhodey
 */
public class ErrorToaster {

  private static final String TAG = "org.anhonesteffort.flock.ErrorToaster";

  protected static final String KEY_STATUS_CODE = "ErrorToaster.KEY_STATUS_CODE";

  protected static final int CODE_SUCCESS = 0;

  protected static final int CODE_UNAUTHORIZED                  = 1;
  protected static final int CODE_REGISTRATION_API_SERVER_ERROR = 2;
  protected static final int CODE_REGISTRATION_API_CLIENT_ERROR = 3;

  protected static final int CODE_DAV_SERVER_ERROR = 4;
  protected static final int CODE_DAV_CLIENT_ERROR = 5;

  protected static final int CODE_CONNECTION_ERROR  = 6;
  protected static final int CODE_CERTIFICATE_ERROR = 7;
  protected static final int CODE_UNKNOWN_IO_ERROR  = 8;

  protected static final int CODE_INVALID_CIPHER_PASSPHRASE = 9;
  protected static final int CODE_INVALID_MAC_ERROR         = 10;
  protected static final int CODE_CRYPTO_ERROR              = 11;

  protected static final int CODE_EMPTY_DAV_URL          = 12;
  protected static final int CODE_EMPTY_ACCOUNT_ID       = 13;
  protected static final int CODE_SPACES_IN_USERNAME     = 14;
  protected static final int CODE_ILLEGAL_ACCOUNT_ID     = 15;
  protected static final int CODE_ACCOUNT_ID_TAKEN       = 16;
  protected static final int CODE_SHORT_PASSWORD         = 17;
  protected static final int CODE_PASSWORDS_DO_NOT_MATCH = 18;

  protected static final int CODE_CARD_NUMBER_INVALID     = 19;
  protected static final int CODE_CARD_EXPIRATION_INVALID = 20;
  protected static final int CODE_CARD_CVC_INVALID        = 21;
  protected static final int CODE_STRIPE_REJECTED_CARD    = 22;
  protected static final int CODE_STRIPE_CONNECTION_ERROR = 23;
  protected static final int CODE_STRIPE_API_ERROR        = 24;

  protected static final int CODE_SUBSCRIPTION_EXPIRED = 25;

  protected static final int CODE_ACCOUNT_MANAGER_ERROR = 26;

  protected static final int CODE_GOOGLE_PLAY_ERROR = 27;

  protected static void handleBundleError(Exception e, Bundle bundle) {
    Log.e(TAG, "handleBundleError() - ", e);

    if (e instanceof AuthorizationException)
      bundle.putInt(KEY_STATUS_CODE, CODE_UNAUTHORIZED);
    else if (e instanceof RegistrationApiParseException)
      bundle.putInt(KEY_STATUS_CODE, CODE_REGISTRATION_API_CLIENT_ERROR);
    else if (e instanceof PaymentRequiredException)
      bundle.putInt(KEY_STATUS_CODE, CODE_STRIPE_REJECTED_CARD);
    else if (e instanceof RegistrationApiException)
      bundle.putInt(KEY_STATUS_CODE, CODE_REGISTRATION_API_SERVER_ERROR);

    else if (e instanceof DavException) {
      DavException ex = (DavException) e;

      if (ex.getErrorCode() == WebDavConstants.SC_UNAUTHORIZED)
        bundle.putInt(KEY_STATUS_CODE, CODE_UNAUTHORIZED);
      else if (ex.getErrorCode() == OwsWebDav.STATUS_PAYMENT_REQUIRED)
        bundle.putInt(KEY_STATUS_CODE, CODE_SUBSCRIPTION_EXPIRED);
      else
        bundle.putInt(KEY_STATUS_CODE, CODE_DAV_SERVER_ERROR);
    }

    else if (e instanceof PropertyParseException) {
      PropertyParseException ex = (PropertyParseException) e;
      bundle.putInt(KEY_STATUS_CODE, CODE_DAV_SERVER_ERROR);
    }

    else if (e instanceof InvalidComponentException) {
      InvalidComponentException ex = (InvalidComponentException) e;
      if (ex instanceof InvalidRemoteComponentException)
        bundle.putInt(KEY_STATUS_CODE, CODE_DAV_SERVER_ERROR);
      else
        bundle.putInt(KEY_STATUS_CODE, CODE_DAV_CLIENT_ERROR);
    }

    else if (e instanceof RemoteException || e instanceof OperationApplicationException)
      bundle.putInt(KEY_STATUS_CODE, CODE_DAV_CLIENT_ERROR);

    else if (e instanceof SSLException)
      bundle.putInt(KEY_STATUS_CODE, CODE_CERTIFICATE_ERROR);

    else if (e instanceof IOException) {
      IOException ex = (IOException) e;
      if (ex instanceof SocketException ||
          ex instanceof UnknownHostException ||
          ex instanceof SocketTimeoutException)
        bundle.putInt(KEY_STATUS_CODE, CODE_CONNECTION_ERROR);
      else
        bundle.putInt(KEY_STATUS_CODE, CODE_UNKNOWN_IO_ERROR);
    }

    else if (e instanceof InvalidMacException)
      bundle.putInt(KEY_STATUS_CODE, CODE_INVALID_MAC_ERROR);
    else if (e instanceof GeneralSecurityException)
      bundle.putInt(KEY_STATUS_CODE, CODE_CRYPTO_ERROR);


    else if (e instanceof CardException) {
      final String CODE_INVALID_CARD_NUMBER      = "incorrect_number";
      final String CODE_INVALID_EXPIRATION_MONTH = "invalid_expiry_month";
      final String CODE_INVALID_EXPIRATION_YEAR  = "invalid_expiry_year";
      final String CODE_INVALID_CVC              = "invalid_cvc";

      if (((CardException) e).getCode().equals(CODE_INVALID_CARD_NUMBER))
        bundle.putInt(KEY_STATUS_CODE, CODE_CARD_NUMBER_INVALID);
      else if (((CardException) e).getCode().equals(CODE_INVALID_EXPIRATION_MONTH))
        bundle.putInt(KEY_STATUS_CODE, CODE_CARD_EXPIRATION_INVALID);
      else if (((CardException) e).getCode().equals(CODE_INVALID_EXPIRATION_YEAR))
        bundle.putInt(KEY_STATUS_CODE, CODE_CARD_EXPIRATION_INVALID);
      else if (((CardException) e).getCode().equals(CODE_INVALID_CVC))
        bundle.putInt(KEY_STATUS_CODE, CODE_CARD_CVC_INVALID);
      else
        bundle.putInt(KEY_STATUS_CODE, CODE_STRIPE_REJECTED_CARD);
    }
    else if (e instanceof APIConnectionException)
      bundle.putInt(KEY_STATUS_CODE, CODE_STRIPE_CONNECTION_ERROR);
    else if (e instanceof StripeException)
      bundle.putInt(KEY_STATUS_CODE, CODE_STRIPE_API_ERROR);

    else
      Log.e(TAG, "DID NOT HANDLE THIS EXCEPTION :(", e);
  }

  protected static void handleDisplayToastBundledError(Context context, Bundle bundle) {
    final int ERROR_CODE = bundle.getInt(KEY_STATUS_CODE, -1);

    switch (ERROR_CODE) {

      case CODE_UNAUTHORIZED:
        handleShowUnauthorizedError(context);
        break;

      case CODE_REGISTRATION_API_SERVER_ERROR:
        handleShowRegistrationApiServerError(context);
        break;
      case CODE_REGISTRATION_API_CLIENT_ERROR:
        handleShowRegistrationApiClientError(context);
        break;

      case CODE_DAV_SERVER_ERROR:
        handleShowDavServerError(context);
        break;
      case CODE_DAV_CLIENT_ERROR:
        handleShowDavClientError(context);
        break;

      case CODE_CONNECTION_ERROR:
        handleShowConnectionError(context);
        break;
      case CODE_CERTIFICATE_ERROR:
        handleShowCertificateError(context);
        break;
      case CODE_UNKNOWN_IO_ERROR:
        handleShowUnknownIoError(context);
        break;

      case CODE_INVALID_CIPHER_PASSPHRASE:
        handleShowInvalidCipherPassphraseError(context);
        break;
      case CODE_INVALID_MAC_ERROR:
        handleShowInvalidMacErrorError(context);
        break;
      case CODE_CRYPTO_ERROR:
        handleShowCryptoError(context);
        break;

      case CODE_EMPTY_DAV_URL:
        handleShowDavUrlEmpty(context);
        break;
      case CODE_EMPTY_ACCOUNT_ID:
        handleShowAccountIdEmpty(context);
        break;
      case CODE_SPACES_IN_USERNAME:
        handleShowSpacesInAccountId(context);
        break;
      case CODE_ILLEGAL_ACCOUNT_ID:
        handleShowIllegalAccountId(context);
        break;
      case CODE_ACCOUNT_ID_TAKEN:
        handleShowAccountIdTaken(context);
        break;
      case CODE_SHORT_PASSWORD:
        handleShowAccountPasswordTooShort(context);
        break;
      case CODE_PASSWORDS_DO_NOT_MATCH:
        handleShowPasswordsDoNotMatch(context);
        break;

      case CODE_CARD_NUMBER_INVALID:
        handleShowCardNumberInvalid(context);
        break;
      case CODE_CARD_EXPIRATION_INVALID:
        handleShowCardExpirationInvalid(context);
        break;
      case CODE_CARD_CVC_INVALID:
        handleShowCardCVCInvalid(context);
        break;
      case CODE_STRIPE_REJECTED_CARD:
        handleShowStripeRejectedCard(context);
        break;
      case CODE_STRIPE_CONNECTION_ERROR:
        handleShowStripeConnectionError(context);
        break;
      case CODE_STRIPE_API_ERROR:
        handleShowStripeApiError(context);
        break;

      case CODE_SUBSCRIPTION_EXPIRED:
        handleShowSubscriptionExpired(context);
        break;

      case CODE_ACCOUNT_MANAGER_ERROR:
        handleShowAccountManagerError(context);
        break;

      case CODE_GOOGLE_PLAY_ERROR:
        handleShowGooglePlayError(context);
        break;

    }
  }

  public static void handleShowError(Context context, Exception e) {
    Bundle bundle = new Bundle();

    handleBundleError(e, bundle);
    handleDisplayToastBundledError(context, bundle);
  }

  private static void handleShowError(Context context, int stringId) {
    Toast.makeText(context, stringId, Toast.LENGTH_LONG).show();
  }

  private static void handleShowErrorQuick(Context context, int stringId) {
    Toast.makeText(context, stringId, Toast.LENGTH_SHORT).show();
  }

  private static void handleShowUnauthorizedError(Context context) {
    handleShowError(context, R.string.error_login_unauthorized);
    DavAccountHelper.invalidateAccountPassword(context);
  }

  private static void handleShowRegistrationApiServerError(Context context) {
    handleShowError(context, R.string.error_registration_api_server_error);
  }
  private static void handleShowRegistrationApiClientError(Context context) {
    handleShowError(context, R.string.error_registration_api_client_error);
  }

  private static void handleShowDavServerError(Context context) {
    if (DavAccountHelper.isUsingOurServers(context))
      handleShowError(context, R.string.error_our_dav_server_error);
    else
      handleShowError(context, R.string.error_their_dav_server_error);
  }
  private static void handleShowDavClientError(Context context) {
    handleShowError(context, R.string.error_dav_client_error);
  }

  private static void handleShowConnectionError(Context context) {
    handleShowError(context, R.string.error_connection_error);
  }
  private static void handleShowCertificateError(Context context) {
    if (DavAccountHelper.isUsingOurServers(context))
      handleShowError(context, R.string.error_our_certificate_validation);
    else
      handleShowError(context, R.string.error_their_certificate_validation);
  }
  private static void handleShowUnknownIoError(Context context) {
    handleShowError(context, R.string.error_unknown_io_error);
  }

  private static void handleShowInvalidCipherPassphraseError(Context context) {
    handleShowErrorQuick(context, R.string.error_invalid_encryption_password);
  }
  private static void handleShowInvalidMacErrorError(Context context) {
    handleShowError(context, R.string.error_invalid_mac_error);
  }
  private static void handleShowCryptoError(Context context) {
    handleShowError(context, R.string.error_unknown_crypto_error);
  }

  private static void handleShowDavUrlEmpty(Context context) {
    handleShowErrorQuick(context, R.string.error_url_cannot_be_empty);
  }
  private static void handleShowAccountIdEmpty(Context context) {
    handleShowErrorQuick(context, R.string.error_username_empty);
  }
  private static void handleShowSpacesInAccountId(Context context) {
    handleShowErrorQuick(context, R.string.error_spaces_in_username);
  }
  private static void handleShowIllegalAccountId(Context context) {
    handleShowErrorQuick(context, R.string.error_username_illegal);
  }
  private static void handleShowAccountIdTaken(Context context) {
    handleShowErrorQuick(context, R.string.error_username_already_registered);
  }
  private static void handleShowAccountPasswordTooShort(Context context) {
    handleShowErrorQuick(context, R.string.error_password_too_short);
  }
  private static void handleShowPasswordsDoNotMatch(Context context) {
    handleShowErrorQuick(context, R.string.error_passwords_do_not_match);
  }

  private static void handleShowCardNumberInvalid(Context context) {
    handleShowErrorQuick(context, R.string.error_card_number_could_not_be_verified);
  }
  private static void handleShowCardExpirationInvalid(Context context) {
    handleShowErrorQuick(context, R.string.error_card_expiration_could_not_be_verified);
  }
  private static void handleShowCardCVCInvalid(Context context) {
    handleShowErrorQuick(context, R.string.error_card_security_code_could_not_be_verified);
  }
  private static void handleShowStripeRejectedCard(Context context) {
    handleShowErrorQuick(context, R.string.error_your_card_could_not_be_verified);
  }
  private static void handleShowStripeConnectionError(Context context) {
    handleShowError(context, R.string.error_connection_error);
  }
  private static void handleShowStripeApiError(Context context) {
    handleShowError(context, R.string.error_stripe_api_error);
  }

  private static void handleShowSubscriptionExpired(Context context) {
    handleShowErrorQuick(context, R.string.notification_flock_subscription_expired);
  }

  private static void handleShowAccountManagerError(Context context) {
    handleShowError(context, R.string.error_android_account_manager_error);
  }

  private static void handleShowGooglePlayError(Context context) {
    handleShowError(context, R.string.google_play_error_please_update_google_play_services);
  }

}
