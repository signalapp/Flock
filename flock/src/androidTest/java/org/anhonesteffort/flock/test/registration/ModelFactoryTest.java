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

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;

import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;

import static org.anhonesteffort.flock.test.util.JsonHelpers.fromJson;
import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;

/**
 * rhodey
 */
public class ModelFactoryTest extends InstrumentationTestCase {

  private static final String MOCK_ACCOUNT_ID = "ACCOUNT00";

  public void testBuildAccount() throws Exception {
    final AssetManager          assets  = getInstrumentation().getContext().getAssets();
    final AugmentedFlockAccount account = fromJson(
        jsonFixture(assets, "fixtures/AugmentedFlockAccount.json"),
        AugmentedFlockAccount.class
    );

    assertTrue(account.getId().equals(MOCK_ACCOUNT_ID.toLowerCase()));
  }

  public void testBuildAccountWithUnknownProperties() throws Exception {
    final AssetManager          assets  = getInstrumentation().getContext().getAssets();
    final AugmentedFlockAccount account = fromJson(
        jsonFixture(assets, "fixtures/AugmentedFlockAccountWithUnknown.json"),
        AugmentedFlockAccount.class
    );

    assertTrue(account.getId().equals(MOCK_ACCOUNT_ID.toLowerCase()));
  }

}
