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

package org.anhonesteffort.flock.test.sync.account;

import android.test.AndroidTestCase;

import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.FlockCardInformation;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.sync.account.AccountStore;
import org.anhonesteffort.flock.test.registration.model.AugmentedFlockAccountTest;
import org.anhonesteffort.flock.test.registration.model.FlockCardInformationTest;
import org.anhonesteffort.flock.test.registration.model.SubscriptionPlanTest;
import org.anhonesteffort.flock.util.guava.Optional;

/**
 * rhodey
 */
public class AccountStoreTest extends AndroidTestCase {

  public void testGetSetLastStripeChargeFailed() throws Exception {
    AccountStore.setLastChargeFailed(getContext(), true);
    assertTrue(AccountStore.getLastChargeFailed(getContext()));

    AccountStore.setLastChargeFailed(getContext(), false);
    assertTrue(!AccountStore.getLastChargeFailed(getContext()));
  }

  public void testGetSetAutoRenew() throws Exception {
    AccountStore.setAutoRenew(getContext(), true);
    assertTrue(AccountStore.getAutoRenew(getContext()));

    AccountStore.setAutoRenew(getContext(), false);
    assertTrue(!AccountStore.getAutoRenew(getContext()));
  }

  public void testSetGetDaysRemainingPositive() throws Exception {
    final Long daysRemaining = 1337L;

    AccountStore.setDaysRemaining(getContext(), daysRemaining);
    assertTrue(AccountStore.getDaysRemaining(getContext()).get().equals(daysRemaining));
  }

  public void testSetGetDaysRemainingNegative() throws Exception {
    final Long daysRemaining = -1337L;

    AccountStore.setDaysRemaining(getContext(), daysRemaining);
    assertTrue(AccountStore.getDaysRemaining(getContext()).get().equals(daysRemaining));
  }

  public void testSetGetSubscriptionPlanNone() throws Exception {
    final SubscriptionPlan planNone = SubscriptionPlanTest.nonePlan();

    AccountStore.setSubscriptionPlan(getContext(), planNone);
    assertTrue(AccountStore.getSubscriptionPlanType(getContext()).equals(planNone.getPlanType()));
    assertTrue(AccountStore.getSubscriptionPlan(getContext()).equals(planNone));
  }

  public void testSetGetSubscriptionPlanStripe() throws Exception {
    final SubscriptionPlan stripePlan = SubscriptionPlanTest.stripePlan();

    AccountStore.setSubscriptionPlan(getContext(), stripePlan);
    assertTrue(AccountStore.getSubscriptionPlanType(getContext()).equals(stripePlan.getPlanType()));
    assertTrue(AccountStore.getSubscriptionPlan(getContext()).equals(stripePlan));
  }

  public void testSetGetSubscriptionPlanGoogle() throws Exception {
    final SubscriptionPlan googlePlan = SubscriptionPlanTest.googlePlan();

    AccountStore.setSubscriptionPlan(getContext(), googlePlan);
    assertTrue(AccountStore.getSubscriptionPlanType(getContext()).equals(googlePlan.getPlanType()));
    assertTrue(AccountStore.getSubscriptionPlan(getContext()).equals(googlePlan));
  }

  public void testSetGetCardInformationPresent() throws Exception {
    final FlockCardInformation card = FlockCardInformationTest.card();

    AccountStore.setCardInformation(getContext(), Optional.of(card));
    assertTrue(AccountStore.getCardInformation(getContext()).get().equals(card));
  }

  public void testSetGetCardInformationAbsent() throws Exception {
    AccountStore.setCardInformation(getContext(), Optional.<FlockCardInformation>absent());
    assertTrue(!AccountStore.getCardInformation(getContext()).isPresent());
  }

  public void testUpdateStore() throws Exception {
    final AugmentedFlockAccount account = AugmentedFlockAccountTest.accountNoPlan();

    AccountStore.updateStore(getContext(), account);

    assertTrue(AccountStore.getLastChargeFailed(getContext()) == account.getLastStripeChargeFailed());
    assertTrue(AccountStore.getAutoRenew(getContext()) == account.getAutoRenewEnabled());
    assertTrue(AccountStore.getDaysRemaining(getContext()).get().equals(account.getDaysRemaining()));
    assertTrue(AccountStore.getSubscriptionPlanType(getContext()).equals(account.getSubscriptionPlan().getPlanType()));
    assertTrue(AccountStore.getSubscriptionPlan(getContext()).equals(account.getSubscriptionPlan()));
  }

}
