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

package org.anhonesteffort.flock.test.registration.model;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;

import org.anhonesteffort.flock.registration.model.FlockAccount;
import org.anhonesteffort.flock.test.util.DateHelper;

import static org.anhonesteffort.flock.test.util.JsonHelpers.fromJson;
import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;

/**
 * rhodey
 */
public class FlockAccountTest extends InstrumentationTestCase {

  public static final String  TEST_ACCOUNT_ID         = "ACCOUNT00";
  public static final Integer TEST_VERSION            = 1;
  public static final String  TEST_SALT               = "salt";
  public static final String  TEST_PLAINTEXT_PASSWORD = "0000";
  public static final String  TEST_STRIPE_ID          = "stripe00";
  public static final Boolean TEST_LAST_CHARGE_FAILED = false;
  public static final Boolean TEST_AUTO_RENEW         = false;

  private AssetManager assets;

  public static FlockAccount accountNoPlan() {
    final FlockAccount account = new FlockAccount(
        TEST_ACCOUNT_ID,         TEST_VERSION,    TEST_SALT,
        null,                    TEST_STRIPE_ID,  DateHelper.getMockDate(),
        TEST_LAST_CHARGE_FAILED, TEST_AUTO_RENEW, SubscriptionPlanTest.nonePlan()
    );
    account.setPassword(TEST_PLAINTEXT_PASSWORD);

    return account;
  }

  public static FlockAccount accountStripePlan() {
    final FlockAccount account = new FlockAccount(
        TEST_ACCOUNT_ID,         TEST_VERSION,    TEST_SALT,
        null,                    TEST_STRIPE_ID,  DateHelper.getMockDate(),
        TEST_LAST_CHARGE_FAILED, TEST_AUTO_RENEW, SubscriptionPlanTest.stripePlan()
    );
    account.setPassword(TEST_PLAINTEXT_PASSWORD);

    return account;
  }

  public static FlockAccount accountGooglePlan() {
    final FlockAccount account = new FlockAccount(
        TEST_ACCOUNT_ID,         TEST_VERSION,    TEST_SALT,
        null,                    TEST_STRIPE_ID,  DateHelper.getMockDate(),
        TEST_LAST_CHARGE_FAILED, TEST_AUTO_RENEW, SubscriptionPlanTest.googlePlan()
    );
    account.setPassword(TEST_PLAINTEXT_PASSWORD);

    return account;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    assets = getInstrumentation().getContext().getAssets();
  }

  public void testDeserialize() throws Exception {
    final FlockAccount account = fromJson(
        jsonFixture(assets, "fixtures/FlockAccount.json"),
        FlockAccount.class
    );

    assertTrue(accountNoPlan().equals(account));
  }

  public void testToFromBundle() throws Exception {
    assertTrue(FlockAccount.build(accountNoPlan().toBundle()).get().equals(accountNoPlan()));
    assertTrue(FlockAccount.build(accountStripePlan().toBundle()).get().equals(accountStripePlan()));
    assertTrue(FlockAccount.build(accountGooglePlan().toBundle()).get().equals(accountGooglePlan()));
  }

}
