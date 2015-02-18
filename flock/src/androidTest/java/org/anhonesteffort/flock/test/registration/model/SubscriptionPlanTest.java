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

import org.anhonesteffort.flock.registration.model.GooglePlan;
import org.anhonesteffort.flock.registration.model.StripePlan;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.test.util.DateHelper;

import static org.anhonesteffort.flock.test.util.JsonHelpers.fromJson;
import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;

/**
 * Programmer: rhodey
 */
public class SubscriptionPlanTest extends InstrumentationTestCase {

  public static final String TEST_STRIPE_PLAN_ID        = "stripe00";
  public static final String TEST_GOOGLE_PLAN_ID        = "google00";
  public static final String TEST_GOOGLE_PURCHASE_TOKEN = "purchase00";

  private AssetManager assets;

  public static SubscriptionPlan nonePlan() {
    return SubscriptionPlan.PLAN_NONE;
  }

  public static StripePlan stripePlan() {
    return new StripePlan(FlockAccountTest.TEST_ACCOUNT_ID, TEST_STRIPE_PLAN_ID);
  }

  public static GooglePlan googlePlan() {
    return new GooglePlan(
      FlockAccountTest.TEST_ACCOUNT_ID, TEST_GOOGLE_PLAN_ID,
      TEST_GOOGLE_PURCHASE_TOKEN,       DateHelper.getMockDate().getTime()
    );
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    assets = getInstrumentation().getContext().getAssets();
  }

  public void testDeserializeFixtureNonePlan() throws Exception {
    final SubscriptionPlan plan = fromJson(
        jsonFixture(assets, "fixtures/SubscriptionPlanNone.json"),
        SubscriptionPlan.class
    );

    assertTrue(nonePlan().equals(plan));
  }

  public void testDeserializeFixtureStripePlan() throws Exception {
    final StripePlan plan = fromJson(
        jsonFixture(assets, "fixtures/StripePlan.json"),
        StripePlan.class
    );

    assertTrue(stripePlan().equals(plan));
  }

  public void testDeserializeFixtureGooglePlan() throws Exception {
    final GooglePlan plan = fromJson(
        jsonFixture(assets, "fixtures/GooglePlan.json"),
        GooglePlan.class
    );

    assertTrue(googlePlan().equals(plan));
  }

  public void testSerializeDeserializeNonePlan() throws Exception {
    final SubscriptionPlan plan = SubscriptionPlan.buildFromSerialized(
        SubscriptionPlan.PLAN_TYPE_NONE,
        nonePlan().serialize()
    );

    assertTrue(nonePlan().equals(plan));
  }

  public void testSerializeDeserializeStripePlan() throws Exception {
    final SubscriptionPlan plan = SubscriptionPlan.buildFromSerialized(
        SubscriptionPlan.PLAN_TYPE_STRIPE,
        stripePlan().serialize()
    );

    assertTrue(stripePlan().equals(plan));
  }

  public void testSerializeDeserializeGooglePlan() throws Exception {
    final SubscriptionPlan plan = SubscriptionPlan.buildFromSerialized(
        SubscriptionPlan.PLAN_TYPE_GOOGLE,
        googlePlan().serialize()
    );

    assertTrue(googlePlan().equals(plan));
  }

}
