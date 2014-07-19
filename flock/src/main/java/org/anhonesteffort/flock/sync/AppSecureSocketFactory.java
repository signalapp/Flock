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

package org.anhonesteffort.flock.sync;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ControllerThreadSocketFactory;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Programmer: rhodey
 * Date: 3/18/14
 */
public class AppSecureSocketFactory implements SecureProtocolSocketFactory {

  private static final String TAG = "org.anhonesteffort.flock.sync.AppSecureSocketFactory";

  private Context    appContext;
  private boolean    useFlockTrustStore;
  private SSLContext sslContext;

  public AppSecureSocketFactory(Context context, boolean useFlockTrustStore) {
    this.appContext         = context;
    this.useFlockTrustStore = useFlockTrustStore;
  }

  private static SSLContext createAppStoreSSLContext(Context appContext, boolean useFlockTrustStore)
    throws HttpClientError
  {
    if (appContext == null)
      throw new HttpClientError("application context is null :(");

    KeyStore trustStore;

    try {

      if (useFlockTrustStore) {
        AssetManager assetManager        = appContext.getAssets();
        InputStream  keyStoreInputStream = assetManager.open("flock.store");
                     trustStore          = KeyStore.getInstance("BKS");

        trustStore.load(keyStoreInputStream, "owsflock".toCharArray());
      }
      else {
        trustStore = KeyStore.getInstance("AndroidCAStore");
        trustStore.load(null, null);
      }

      TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
      tmf.init(trustStore);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tmf.getTrustManagers(), null);

      return sslContext;

    } catch (Exception e) {
      Log.e(TAG, "createAppStoreSSLContext() - flock store? " + useFlockTrustStore, e);
      throw new HttpClientError(e.toString());
    }
  }
  
  private SSLContext getSSLContext() throws HttpClientError {
    if (sslContext == null)
      sslContext = createAppStoreSSLContext(appContext, useFlockTrustStore);

    return sslContext;
  }

  @Override
  public Socket createSocket(String host, int port)
      throws HttpClientError, IOException
  {
    return getSSLContext().getSocketFactory().createSocket(host, port);
  }

  @Override
  public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
      throws HttpClientError, IOException
  {
    return getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
      throws HttpClientError, IOException
  {
    return getSSLContext().getSocketFactory().createSocket(host, port, localAddress, localPort);
  }

  @Override
  public Socket createSocket(String               host,
                             int                  port,
                             InetAddress          localAddress,
                             int                  localPort,
                             HttpConnectionParams params)
      throws HttpClientError, IOException
  {
    if (params == null)
      return createSocket(host, port, localAddress, localPort);

    int timeout = params.getConnectionTimeout();

    if (timeout == 0)
      return createSocket(host, port, localAddress, localPort);

    return ControllerThreadSocketFactory.createSocket(this, host, port, localAddress, localPort, timeout);
  }

}
