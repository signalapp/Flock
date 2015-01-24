/*
 * Copyright (C) 2015 Open Whisper Systems
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

package org.anhonesteffort.flock.registration;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.InputStream;
import java.security.KeyStore;

/**
 * rhodey
 */
public class HttpClientFactory {

  private final Context context;

  public HttpClientFactory(Context context) {
    this.context = context;
  }

  public DefaultHttpClient buildClient() throws RegistrationApiException {
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
      Log.e(getClass().getName(), "caught exception while constructing HttpClient client", e);
      throw new RegistrationApiException("caught exception while constructing HttpClient client: " + e.toString());
    }
  }

}
