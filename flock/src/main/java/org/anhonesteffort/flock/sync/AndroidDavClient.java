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
import android.util.Log;

import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.webdav.DavClient;
import org.apache.commons.httpclient.protocol.Protocol;

import java.net.URL;

/**
 * Programmer: rhodey
 */
public class AndroidDavClient extends DavClient {

  private Context context;

  private void fixClientTrust() {
    int port = davHost.getPort();
    if (port < 1)
      port = davHost.getDefaultPort();

    boolean                useFlockTrustStore = davHost.toString().equals(OwsWebDav.HREF_WEBDAV_HOST);
    AppSecureSocketFactory appSocketFactory   = new AppSecureSocketFactory(context, useFlockTrustStore);
    Protocol               appHttps           = new Protocol("https", appSocketFactory, port);

    hostConfiguration.setHost(davHost.getHost(), port, appHttps);
    Protocol.registerProtocol("https", appHttps);
  }

  public AndroidDavClient(Context context,
                          URL     davHost,
                          String  username,
                          String  password)
  {
    super(davHost, username, password);
    this.context = context;

    if (davHost.getProtocol().equals("https"))
      fixClientTrust();
  }
}
