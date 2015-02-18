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

import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.FlockSubscription;
import org.anhonesteffort.flock.util.TimeUtil;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.anhonesteffort.flock.test.util.JsonHelpers.fromJson;
import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;

/**
 * Programmer: rhodey
 */
public class AugmentedFlockAccountTest extends InstrumentationTestCase {

  private AssetManager assets;

  public static AugmentedFlockAccount accountNoPlan() {
    final List<FlockSubscription> subscriptions = new LinkedList<>();
    subscriptions.add(FlockSubscriptionTest.subscription());

    return new AugmentedFlockAccount(
      FlockAccountTest.accountNoPlan(),
      subscriptions
    );
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    assets = getInstrumentation().getContext().getAssets();
  }

  public void testDeserialize() throws Exception {
    final AugmentedFlockAccount account = fromJson(
        jsonFixture(assets, "fixtures/AugmentedFlockAccount.json"),
        AugmentedFlockAccount.class
    );

    assertTrue(accountNoPlan().equals(account));
  }

  public void testDaysCredit() throws Exception {
    final AugmentedFlockAccount account    = accountNoPlan();
    Long                        daysCredit = 0L;

    for (FlockSubscription subscription : account.getSubscriptions())
      daysCredit += subscription.getDaysCredit();

    final Long msSinceCreate   = (new Date().getTime() - account.getCreateDate().getTime());
    final Long daysSinceCreate = TimeUtil.millisecondsToDays(msSinceCreate);
    final Long daysRemaining   = (daysCredit - daysSinceCreate);

    assertTrue("days remaining is calculated correctly",
        daysRemaining.equals(account.getDaysRemaining()));
  }

  public void testDaysCreditMany() throws Exception {
    final AugmentedFlockAccount account    = accountNoPlan();
    Long                        daysCredit = 0L;

    account.getSubscriptions().add(new FlockSubscription(account.getId(), "nope", new Date(), 9001, 0D));
    for (FlockSubscription subscription : account.getSubscriptions())
      daysCredit += subscription.getDaysCredit();

    final Long msSinceCreate   = (new Date().getTime() - account.getCreateDate().getTime());
    final Long daysSinceCreate = TimeUtil.millisecondsToDays(msSinceCreate);
    final Long daysRemaining   = (daysCredit - daysSinceCreate);

    assertTrue("days remaining is calculated correctly",
        daysRemaining.equals(account.getDaysRemaining()));
  }

}
