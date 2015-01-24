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

package org.anhonesteffort.flock.test.registration;

import android.test.InstrumentationTestCase;

import org.anhonesteffort.flock.registration.HttpClientFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * rhodey
 */
public class HttpClientFactoryTest extends InstrumentationTestCase {

  public void testScheme() throws Exception {
    final HttpClientFactory httpFactory = new HttpClientFactory(getInstrumentation().getContext());
    final DefaultHttpClient httpClient  = httpFactory.buildClient();
    final SchemeRegistry    schemes     = httpClient.getConnectionManager().getSchemeRegistry();

    final Scheme httpScheme  = schemes.getScheme("http");
    final Scheme httpsScheme = schemes.getScheme("https");

    assertTrue(httpScheme != null && httpsScheme != null);
    assertTrue(schemes.getSchemeNames().size() == 2);

    assertTrue(httpsScheme.getDefaultPort() == 443);
    assertTrue(httpsScheme.getSocketFactory() instanceof SSLSocketFactory);
  }

}
