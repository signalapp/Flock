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

import org.anhonesteffort.flock.registration.model.FlockSubscription;
import org.anhonesteffort.flock.test.util.DateHelper;

import static org.anhonesteffort.flock.test.util.JsonHelpers.fromJson;
import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;

/**
 * Programmer: rhodey
 */
public class FlockSubscriptionTest extends InstrumentationTestCase {

  public static final String  TEST_PAYMENT_ID  = "payment00";
  public static final Integer TEST_DAYS_CREDIT = 365;
  public static final Double  TEST_COST_USD    = 1.337;

  private AssetManager assets;

  public static FlockSubscription subscription() {
    return new FlockSubscription(
      FlockAccountTest.TEST_ACCOUNT_ID, TEST_PAYMENT_ID, DateHelper.getMockDate(),
      TEST_DAYS_CREDIT,                 TEST_COST_USD
    );
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    assets = getInstrumentation().getContext().getAssets();
  }

  public void testDeserialize() throws Exception {
    final FlockSubscription subscription = fromJson(
        jsonFixture(assets, "fixtures/FlockSubscription.json"),
        FlockSubscription.class
    );

    assertTrue(subscription().equals(subscription));
  }

}
