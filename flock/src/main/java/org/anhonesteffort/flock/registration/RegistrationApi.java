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

package org.anhonesteffort.flock.registration;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.base.Optional;
import com.google.common.io.CharStreams;
import com.stripe.exception.CardException;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.FlockCardInformation;
import org.anhonesteffort.flock.util.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class RegistrationApi {

  private static final String TAG = "org.anhonesteffort.flock.registration.RegistrationApi";

  private static final String KEY_CACHED_DAYS_REMAINING     = "KEY_CACHED_DAYS_REMAINING";
  private static final String KEY_CACHED_AUTO_RENEW_ENABLED = "KEY_CACHED_AUTO_RENEW_ENABLED";
  private static final String KEY_CACHED_LAST_CHARGE_FAILED = "KEY_CACHED_LAST_CHARGE_FAILED";

  private       Context      context;
  private final ObjectMapper mapper;

  public RegistrationApi(Context context) {
    this.context = context;
    this.mapper  = new ObjectMapper();

    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
  }

  private DefaultHttpClient getClient(Context context)
      throws IOException, RegistrationApiClientException
  {
    try {

      AssetManager assetManager        = context.getAssets();
      InputStream  keyStoreInputStream = assetManager.open("flock.store");
      KeyStore     trustStore          = KeyStore.getInstance("BKS");

      trustStore.load(keyStoreInputStream, "owsflock".toCharArray());

      SSLSocketFactory  appSSLSocketFactory = new SSLSocketFactory(trustStore);
      DefaultHttpClient client              = new DefaultHttpClient();
      SchemeRegistry    schemeRegistry      = client.getConnectionManager().getSchemeRegistry();
      Scheme            httpsScheme         = new Scheme("https", appSSLSocketFactory, 443);

      schemeRegistry.register(httpsScheme);

      return client;

    } catch (Exception e) {
      Log.e(TAG, "caught exception while constructing HttpClient client", e);
      throw new RegistrationApiClientException("caught exception while constructing HttpClient client: " + e.toString());
    }
  }

  private void authorizeRequest(HttpRequestBase httpRequest, DavAccount account) {
    String encodedAuth = account.getUserId() + ":" + account.getAuthToken();
    httpRequest.addHeader("Authorization", "Basic " + Base64.encodeBytes(encodedAuth.getBytes()));
  }

  private void throwExceptionIfNotOK(HttpResponse response) throws RegistrationApiException {
    Log.d(TAG, "response status code: " + response.getStatusLine().getStatusCode());

    switch (response.getStatusLine().getStatusCode()) {
      case OwsRegistration.STATUS_MALFORMED_REQUEST:
        throw new RegistrationApiClientException("Registration API returned status malformed request.");

      case OwsRegistration.STATUS_UNAUTHORIZED:
        throw new AuthorizationException("Registration API returned status unauthorized.");

      case OwsRegistration.STATUS_RESOURCE_NOT_FOUND:
        throw new ResourceNotFoundException("Registration API returned status resource not found.");

      case OwsRegistration.STATUS_RESOURCE_ALREADY_EXISTS:
        throw new ResourceAlreadyExistsException("Registration API returned status 403, resource already exists.");

      case OwsRegistration.STATUS_PAYMENT_REQUIRED:
        throw new RegistrationApiClientException("Registration API didn't like the card token we gave it",
                                                 OwsRegistration.STATUS_PAYMENT_REQUIRED);

      case OwsRegistration.STATUS_SERVICE_UNAVAILABLE:
        throw new RegistrationApiException("Registration API service is unavailable");

      case OwsRegistration.STATUS_SERVER_ERROR:
        throw new RegistrationApiException("Registration API returned status 500! 0.o");
    }
  }

  private AugmentedFlockAccount buildFlockAccount(HttpResponse response)
      throws RegistrationApiClientException
  {
    try {

      return mapper.readValue(response.getEntity().getContent(), AugmentedFlockAccount.class);

    } catch (IOException e) {
      Log.e(TAG, "unable to build account from HTTP response", e);
      throw new RegistrationApiClientException("unable to build account from HTTP response.");
    }
  }

  private FlockCardInformation buildFlockCardInformation(HttpResponse response)
      throws RegistrationApiClientException
  {
    try {

      return mapper.readValue(response.getEntity().getContent(), FlockCardInformation.class);

    } catch (IOException e) {
      Log.e(TAG, "unable to build card information from HTTP response", e);
      throw new RegistrationApiClientException("unable to build card information from HTTP response.");
    }
  }

  public Double getCostPerYearUsd()
      throws IOException, RegistrationApiException
  {
    HttpGet           httpGet    = new HttpGet(OwsRegistration.HREF_PRICING);
    DefaultHttpClient httpClient = getClient(context);
    HttpResponse      response   = httpClient.execute(httpGet);
    InputStreamReader reader     = new InputStreamReader(response.getEntity().getContent());

    throwExceptionIfNotOK(response);

    return Double.valueOf(CharStreams.toString(reader));
  }

  public boolean isAuthenticated(DavAccount account)
    throws IOException, RegistrationApiException
  {
    try {

      HttpGet           httpGet    = new HttpGet(OwsRegistration.getHrefForAccount(account.getUserId()));
      DefaultHttpClient httpClient = getClient(context);
      authorizeRequest(httpGet, account);

      HttpResponse response = httpClient.execute(httpGet);
      throwExceptionIfNotOK(response);

      return true;

    } catch (AuthorizationException e) {
      return false;
    }
  }

  public AugmentedFlockAccount createAccount(DavAccount account)
    throws RegistrationApiException, IOException
  {
    Log.d(TAG, "createAccount()");

    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_ID,       account.getUserId()));
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_VERSION,  Integer.toString(2)));
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_PASSWORD, account.getAuthToken()));

    String            HREF       = OwsRegistration.getHrefWithParameters(OwsRegistration.HREF_ACCOUNT_COLLECTION,
                                                                         nameValuePairs);
    HttpPost          httpPost   = new HttpPost(HREF);
    DefaultHttpClient httpClient = getClient(context);

    HttpResponse response = httpClient.execute(httpPost);
    throwExceptionIfNotOK(response);

    AugmentedFlockAccount flockAccount = buildFlockAccount(response);

    cacheSubscriptionDetails(context,
                             flockAccount.getDaysRemaining(),
                             flockAccount.getAutoRenewEnabled(),
                             flockAccount.getLastStripeChargeFailed());
    return flockAccount;
  }

  public AugmentedFlockAccount getAccount(DavAccount account)
      throws RegistrationApiException, IOException
  {
    String            HREF       = OwsRegistration.getHrefForAccount(account.getUserId());
    HttpGet           httpGet    = new HttpGet(HREF);
    DefaultHttpClient httpClient = getClient(context);
    authorizeRequest(httpGet, account);

    HttpResponse response = httpClient.execute(httpGet);
    throwExceptionIfNotOK(response);

    AugmentedFlockAccount flockAccount = buildFlockAccount(response);

    cacheSubscriptionDetails(context,
                             flockAccount.getDaysRemaining(),
                             flockAccount.getAutoRenewEnabled(),
                             flockAccount.getLastStripeChargeFailed());
    return flockAccount;
  }

  private static void cacheSubscriptionDetails(Context context,
                                               Long    daysRemaining,
                                               Boolean autoRenewEnabled,
                                               Boolean lastChargeFailed)
  {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    preferences.edit()
        .putLong(KEY_CACHED_DAYS_REMAINING,        daysRemaining)
        .putBoolean(KEY_CACHED_AUTO_RENEW_ENABLED, autoRenewEnabled)
        .putBoolean(KEY_CACHED_LAST_CHARGE_FAILED, lastChargeFailed)
        .apply();
  }

  public static Optional<Pair<Long, Boolean[]>> getCachedSubscriptionDetails(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (preferences.getLong(KEY_CACHED_DAYS_REMAINING, -1) == -1)
      return Optional.absent();

    return Optional.of(new Pair<Long, Boolean[]>(
        preferences.getLong(KEY_CACHED_DAYS_REMAINING, 0),
        new Boolean[] {
            preferences.getBoolean(KEY_CACHED_AUTO_RENEW_ENABLED, false),
            preferences.getBoolean(KEY_CACHED_LAST_CHARGE_FAILED, true)
        }
    ));
  }

  public Optional<FlockCardInformation> getCard(DavAccount account)
      throws RegistrationApiException, IOException
  {
    String            HREF       = OwsRegistration.getHrefForCard(account.getUserId());
    HttpGet           httpGet    = new HttpGet(HREF);
    DefaultHttpClient httpClient = getClient(context);
    authorizeRequest(httpGet, account);

    HttpResponse response = httpClient.execute(httpGet);

    try {

      throwExceptionIfNotOK(response);

    } catch (ResourceNotFoundException e) {
      return Optional.absent();
    }

    return Optional.of(buildFlockCardInformation(response));
  }

  public void setAccountVersion(DavAccount account, Integer version)
      throws RegistrationApiException, IOException
  {
    Log.d(TAG, "setAccountVersion()");

    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_VERSION, version.toString()));

    String            HREF       = OwsRegistration.getHrefWithParameters(OwsRegistration.getHrefForAccount(account.getUserId()), nameValuePairs);
    HttpPut           httpPut    = new HttpPut(HREF);
    DefaultHttpClient httpClient = getClient(context);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);
  }

  public void setAccountPassword(DavAccount account, String newPassword)
    throws RegistrationApiException, IOException
  {
    Log.d(TAG, "setAccountPassword()");

    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_PASSWORD, newPassword));

    String            HREF       = OwsRegistration.getHrefWithParameters(OwsRegistration.getHrefForAccount(account.getUserId()), nameValuePairs);
    HttpPut           httpPut    = new HttpPut(HREF);
    DefaultHttpClient httpClient = getClient(context);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);
  }

  public void updateAccountStripeCard(DavAccount account, String stripeCardToken)
      throws CardException, RegistrationApiException, IOException
  {
    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_STRIPE_CARD_TOKEN, stripeCardToken));

    String            HREF       = OwsRegistration.getHrefWithParameters(OwsRegistration.getHrefForAccount(account.getUserId()), nameValuePairs);
    HttpPut           httpPut    = new HttpPut(HREF);
    DefaultHttpClient httpClient = getClient(context);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);

    try {

      throwExceptionIfNotOK(response);

    } catch (RegistrationApiClientException e) {
      if (e.getStatus() == OwsRegistration.STATUS_PAYMENT_REQUIRED)
        throw new CardException("server rejected card", "hack", "hack", null);
      else
        throw e;
    }

    Optional<Pair<Long, Boolean[]>> subscriptionDetails = getCachedSubscriptionDetails(context);
    if (!subscriptionDetails.isPresent())
      return;

    cacheSubscriptionDetails(context,
                             subscriptionDetails.get().first,
                             subscriptionDetails.get().second[0],
                             false);
  }

  public void setAccountAutoRenew(DavAccount account, Boolean autoRenewEnabled)
      throws RegistrationApiException, IOException
  {
    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_AUTO_RENEW, autoRenewEnabled.toString()));

    String            HREF       = OwsRegistration.getHrefWithParameters(OwsRegistration.getHrefForAccount(account.getUserId()), nameValuePairs);
    HttpPut           httpPut    = new HttpPut(HREF);
    DefaultHttpClient httpClient = getClient(context);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);

    Optional<Pair<Long, Boolean[]>> subscriptionDetails = getCachedSubscriptionDetails(context);
    if (!subscriptionDetails.isPresent())
      return;

    cacheSubscriptionDetails(context,
                             subscriptionDetails.get().first,
                             autoRenewEnabled,
                             subscriptionDetails.get().second[1]);
  }

  public void deleteAccount(DavAccount account)
    throws RegistrationApiException, IOException
  {
    Log.d(TAG, "deleteAccount()");

    HttpDelete        httpDelete = new HttpDelete(OwsRegistration.getHrefForAccount(account.getUserId()));
    DefaultHttpClient httpClient = getClient(context);
    authorizeRequest(httpDelete, account);

    HttpResponse response = httpClient.execute(httpDelete);
    throwExceptionIfNotOK(response);

    cacheSubscriptionDetails(context, 0L, false, true);
  }
}
