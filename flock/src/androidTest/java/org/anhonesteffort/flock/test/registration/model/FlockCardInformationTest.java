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

import org.anhonesteffort.flock.registration.model.FlockCardInformation;

import static org.anhonesteffort.flock.test.util.JsonHelpers.fromJson;
import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;

/**
 * rhodey
 */
public class FlockCardInformationTest extends InstrumentationTestCase {

  public static final String TEST_CARD_LAST_FOUR  = "6666";
  public static final String TEST_CARD_EXPIRATION = "10/2020";

  private AssetManager assets;

  public static FlockCardInformation card() {
    return new FlockCardInformation(
        FlockAccountTest.TEST_ACCOUNT_ID,
        TEST_CARD_LAST_FOUR,
        TEST_CARD_EXPIRATION
    );
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    assets = getInstrumentation().getContext().getAssets();
  }

  public void testDeserialize() throws Exception {
    final FlockCardInformation card = fromJson(
        jsonFixture(assets, "fixtures/FlockCardInformation.json"),
        FlockCardInformation.class
    );

    assertTrue(card().equals(card));
  }

  public void testToFromBundle() throws Exception {
    assertTrue(
        FlockCardInformation.build(card().toBundle()).get().equals(card())
    );
  }

}
