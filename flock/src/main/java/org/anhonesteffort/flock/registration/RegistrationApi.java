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
import android.util.Log;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.FlockCardInformation;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.util.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class RegistrationApi {

  private static final String TAG = "org.anhonesteffort.flock.registration.RegistrationApi";

  private final DefaultHttpClient httpClient;

  public RegistrationApi(Context context) throws RegistrationApiException {
    this.httpClient = new HttpClientFactory(context).buildClient();
  }

  private static void authorizeRequest(HttpRequestBase httpRequest, DavAccount account) {
    String encodedAuth = account.getUserId() + ":" + account.getAuthToken();
    httpRequest.addHeader("Authorization", "Basic " + Base64.encodeBytes(encodedAuth.getBytes()));
  }

  private static void throwExceptionIfNotOK(HttpResponse response)
      throws RegistrationApiException
  {
    Log.d(TAG, "response status code: " + response.getStatusLine().getStatusCode());

    Integer status = response.getStatusLine().getStatusCode();

    if (status >= 200 && status < 300)
      return;

    switch (status) {
      case OwsRegistration.STATUS_UNAUTHORIZED:
        throw new AuthorizationException("Registration API returned status unauthorized.");

      case OwsRegistration.STATUS_RESOURCE_NOT_FOUND:
        throw new ResourceNotFoundException("Registration API returned status resource not found.");

      case OwsRegistration.STATUS_RESOURCE_ALREADY_EXISTS:
        throw new ResourceAlreadyExistsException("Registration API returned status 403, resource already exists.");

      case OwsRegistration.STATUS_PAYMENT_REQUIRED:
        throw new PaymentRequiredException("Registration API didn't like the card or purchase token we gave it");

      case OwsRegistration.STATUS_RATE_LIMITED:
        throw new RegistrationApiException("we are being rate limited for some reason, bummer.");
    }

    throw new RegistrationApiException(
        "got bad status: " + response.getStatusLine().getReasonPhrase() + ", " + status, status
    );
  }

  public AugmentedFlockAccount createAccount(DavAccount account)
    throws RegistrationApiException, IOException
  {
    Log.d(TAG, "createAccount()");

    List<NameValuePair> nameValuePairs = new LinkedList<>();
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_ID,       account.getUserId()));
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_VERSION,  Integer.toString(2)));
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_PASSWORD, account.getAuthToken()));

    String accountsControllerHref = OwsRegistration.getHrefWithParameters(
        OwsRegistration.HREF_ACCOUNT_COLLECTION,
        nameValuePairs
    );

    HttpPost     httpPost = new HttpPost(accountsControllerHref);
    HttpResponse response = httpClient.execute(httpPost);

    throwExceptionIfNotOK(response);

    return ModelFactory.buildAccount(response);
  }


  public AugmentedFlockAccount getAccount(DavAccount account)
      throws RegistrationApiException, IOException
  {
    String  HREF    = OwsRegistration.getHrefForAccount(account.getUserId());
    HttpGet httpGet = new HttpGet(HREF);
    authorizeRequest(httpGet, account);

    HttpResponse response = httpClient.execute(httpGet);
    throwExceptionIfNotOK(response);

    return ModelFactory.buildAccount(response);
  }

  public void setAccountVersion(DavAccount account, Integer version)
      throws RegistrationApiException, IOException
  {
    Log.d(TAG, "setAccountVersion()");

    List<NameValuePair> nameValuePairs = new LinkedList<>();
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_VERSION, version.toString()));

    String accountControllerHref = OwsRegistration.getHrefWithParameters(
        OwsRegistration.getHrefForAccount(account.getUserId()),
        nameValuePairs
    );

    HttpPut httpPut = new HttpPut(accountControllerHref);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);
  }

  public void setAccountPassword(DavAccount account, String newPassword)
      throws RegistrationApiException, IOException
  {
    Log.d(TAG, "setAccountPassword()");

    List<NameValuePair> nameValuePairs = new LinkedList<>();
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_ACCOUNT_PASSWORD, newPassword));

    String accountControllerHref = OwsRegistration.getHrefWithParameters(
        OwsRegistration.getHrefForAccount(account.getUserId()),
        nameValuePairs
    );

    HttpPut httpPut = new HttpPut(accountControllerHref);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);
  }

  public Optional<FlockCardInformation> getCard(DavAccount account)
      throws RegistrationApiException, IOException
  {
    String  cardControllerHref = OwsRegistration.getHrefForCard(account.getUserId());
    HttpGet httpGet            = new HttpGet(cardControllerHref);
    authorizeRequest(httpGet, account);

    HttpResponse response = httpClient.execute(httpGet);

    try {

      throwExceptionIfNotOK(response);

    } catch (ResourceNotFoundException e) {
      return Optional.absent();
    }

    return Optional.of(ModelFactory.buildCard(response));
  }

  public void setStripeCard(DavAccount account, String stripeCardToken)
      throws RegistrationApiException, IOException
  {
    List<NameValuePair> nameValuePairs = new LinkedList<>();
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_STRIPE_CARD_TOKEN, stripeCardToken));

    String cardControllerHref = OwsRegistration.getHrefWithParameters(
        OwsRegistration.getHrefForCard(account.getUserId()),
        nameValuePairs
    );

    HttpPut httpPut = new HttpPut(cardControllerHref);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);
  }

  public boolean getIsPlanAutoRenewing(DavAccount account)
      throws RegistrationApiException, IOException
  {
    String  planControllerHref = OwsRegistration.getHrefForPlan(account.getUserId());
    HttpGet httpGet            = new HttpGet(planControllerHref);
    authorizeRequest(httpGet, account);

    HttpResponse response = httpClient.execute(httpGet);
    throwExceptionIfNotOK(response);

    return ModelFactory.buildBoolean(response);
  }

  public void putNewGooglePlan(DavAccount account, String planId, String purchaseToken)
      throws RegistrationApiException, IOException
  {
    Log.d(TAG, "putNewGooglePlan()");
    List<NameValuePair> nameValuePairs = new LinkedList<>();
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_PLAN_TYPE,      Integer.toString(SubscriptionPlan.PLAN_TYPE_GOOGLE)));
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_PLAN_ID,        planId));
    nameValuePairs.add(new BasicNameValuePair(OwsRegistration.PARAM_PURCHASE_TOKEN, purchaseToken));

    String planControllerHref = OwsRegistration.getHrefWithParameters(
        OwsRegistration.getHrefForPlan(account.getUserId()),
        nameValuePairs
    );

    HttpPut httpPut = new HttpPut(planControllerHref);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);
  }

  public void migrateBillingToStripeSubscriptionModel(DavAccount account)
      throws RegistrationApiException, IOException
  {
    Log.d(TAG, "migrateBillingToStripeSubscriptionModel()");

    List<NameValuePair> nameValuePairs = new LinkedList<>();
    nameValuePairs.add(new BasicNameValuePair(
        OwsRegistration.PARAM_PLAN_TYPE,
        Integer.toString(SubscriptionPlan.PLAN_TYPE_STRIPE)
    ));

    String planControllerHref = OwsRegistration.getHrefWithParameters(
        OwsRegistration.getHrefForPlan(account.getUserId()),
        nameValuePairs
    );

    HttpPut httpPut = new HttpPut(planControllerHref);
    authorizeRequest(httpPut, account);

    HttpResponse response = httpClient.execute(httpPut);
    throwExceptionIfNotOK(response);
  }

  public void cancelSubscription(DavAccount account)
      throws RegistrationApiException, IOException
  {
    Log.d(TAG, "cancelSubscription()");

    String     planController = OwsRegistration.getHrefForPlan(account.getUserId());
    HttpDelete httpDelete     = new HttpDelete(planController);
    authorizeRequest(httpDelete, account);

    HttpResponse response = httpClient.execute(httpDelete);
    throwExceptionIfNotOK(response);
  }

  public void deleteAccount(DavAccount account)
    throws RegistrationApiException, IOException
  {
    Log.d(TAG, "deleteAccount()");

    String     accountControllerHref = OwsRegistration.getHrefForAccount(account.getUserId());
    HttpDelete httpDelete            = new HttpDelete(accountControllerHref);
    authorizeRequest(httpDelete, account);

    HttpResponse response = httpClient.execute(httpDelete);
    throwExceptionIfNotOK(response);
  }

}
