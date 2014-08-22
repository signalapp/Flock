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

import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class OwsRegistration {

  protected static final int STATUS_OK                      = 200;
  protected static final int STATUS_REDIRECT                = 300;
  protected static final int STATUS_MALFORMED_REQUEST       = 400;
  protected static final int STATUS_UNAUTHORIZED            = 401;
  protected static final int STATUS_PAYMENT_REQUIRED        = 402;
  protected static final int STATUS_RESOURCE_ALREADY_EXISTS = 403;
  protected static final int STATUS_RESOURCE_NOT_FOUND      = 404;
  protected static final int STATUS_SERVER_ERROR            = 500;
  protected static final int STATUS_SERVICE_UNAVAILABLE     = 503;

  protected static final String ACCOUNT_COLLECTION      = "accounts";
  protected static final String ACCOUNT_CARD_CONTROLLER = "card";
  protected static final String PRICING_CONTROLLER      = "pricing";

  protected static final String PARAM_ACCOUNT_ID        = "id";
  protected static final String PARAM_ACCOUNT_VERSION   = "version";
  protected static final String PARAM_ACCOUNT_PASSWORD  = "password";
  protected static final String PARAM_STRIPE_CARD_TOKEN = "stripe_card_token";
  protected static final String PARAM_AUTO_RENEW        = "auto_renew";

  protected static final String REGISTRATION_API_HOST   = "flock-accounts.whispersystems.org";
  protected static final int    REGISTRATION_API_PORT   = 443;
  protected static final String HREF_REGISTRATION_API   = "https://" + REGISTRATION_API_HOST + ":" + REGISTRATION_API_PORT;
  protected static final String HREF_ACCOUNT_COLLECTION = HREF_REGISTRATION_API + "/" + ACCOUNT_COLLECTION + "/";
  protected static final String HREF_PRICING            = HREF_REGISTRATION_API + "/" + PRICING_CONTROLLER + "/";

  public static final String STRIPE_PUBLIC_KEY = "pk_live_EiIuIaXaPPMgjllTlweiDYgJ";

  protected static String getHrefForAccount(String accountId) {
    return HREF_ACCOUNT_COLLECTION + accountId;
  }

  protected static String getHrefForCard(String accountId) {
    return HREF_ACCOUNT_COLLECTION + accountId + "/" + ACCOUNT_CARD_CONTROLLER;
  }

  protected static String getHrefWithParameters(String href, List<NameValuePair> params) {
    String result = href + "?";
    try {

      for (NameValuePair param : params)
        result += param.getName() + "=" + URLEncoder.encode(param.getValue(), HTTP.UTF_8) + "&";

    } catch (UnsupportedEncodingException e) {
      Log.e("OwsRegistrtaion", e.toString());
    }

    return result;
  }

}
