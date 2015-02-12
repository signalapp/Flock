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

package org.anhonesteffort.flock.test.auth;

import android.test.AndroidTestCase;

import org.anhonesteffort.flock.util.guava.Optional;

import org.anhonesteffort.flock.auth.DavAccount;

/**
 * rhodey
 */
public class DavAccountTest extends AndroidTestCase {

  private static final String MOCK_USER_ID    = "rhodey";
  private static final String MOCK_AUTH_TOKEN = "token-token-token";
  private static final String MOCK_DAV_HOST   = "https://flock.sync/";

  public void testToFromBundle() {
    final DavAccount account = new DavAccount(MOCK_USER_ID, MOCK_AUTH_TOKEN, MOCK_DAV_HOST);
    final Optional<DavAccount> accountFromBundle = DavAccount.build(account.toBundle());

    assertTrue(accountFromBundle.isPresent());
    assertTrue(accountFromBundle.get().getUserId().equals(MOCK_USER_ID));
    assertTrue(accountFromBundle.get().getAuthToken().equals(MOCK_AUTH_TOKEN));
    assertTrue(accountFromBundle.get().getDavHostHREF().equals(MOCK_DAV_HOST));
  }

}
