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

package org.anhonesteffort.flock.webdav;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class DavClient {

  private static final int KEEP_ALIVE_TIMEOUT_SECONDS = 15;

  protected URL    davHost;
  protected String davUsername;
  protected String davPassword;

  protected HttpClient            client;
  protected HostConfiguration     hostConfiguration;
  private   HttpConnectionManager connectionManager;

  protected void initClient() {
    HttpClientParams params    = new HttpClientParams();
    List<String>     authPrefs = new ArrayList<String>(2);

    authPrefs.add(AuthPolicy.DIGEST);
    authPrefs.add(AuthPolicy.BASIC);
    params.setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
    params.setAuthenticationPreemptive(true);

    client            = new HttpClient(params);
    hostConfiguration = client.getHostConfiguration();
    connectionManager = client.getHttpConnectionManager();

    hostConfiguration.setHost(davHost.getHost(),
                              davHost.getPort(),
                              Protocol.getProtocol(davHost.getProtocol()));

    Credentials credentials = new UsernamePasswordCredentials(davUsername, davPassword);
    client.getState().setCredentials(AuthScope.ANY, credentials);
  }

  public DavClient(URL davHost, String username, String password) {
    this.davHost     = davHost;
    this.davUsername = username;
    this.davPassword = password;

    initClient();
  }

  public URL getDavHost() {
    return davHost;
  }

  public String getUsername() {
    return davUsername;
  }

  public String getPassword() {
    return davPassword;
  }

  public List<String> getDavOptions() throws IOException, DavException {
    OptionsMethod optionsMethod = new OptionsMethod(davHost.toString());

    try {

      execute(optionsMethod);

      if (optionsMethod.getStatusCode() >= 300)
        throw new DavException(optionsMethod.getStatusCode(),
            "Options method really shouldn't give us grief here... (" + optionsMethod.getStatusCode() + ")");

      return Arrays.asList(optionsMethod.getAllowedMethods());

    } finally {
      optionsMethod.releaseConnection();
    }
  }

  public int execute(HttpMethodBase method) throws IOException {
    method.addRequestHeader("Connection", "Keep-Alive");
    method.addRequestHeader("Keep-Alive", "timeout=" + KEEP_ALIVE_TIMEOUT_SECONDS);
    return client.executeMethod(hostConfiguration, method);
  }

  protected void closeHttpConnection() {
    connectionManager.closeIdleConnections(0);
  }

}
