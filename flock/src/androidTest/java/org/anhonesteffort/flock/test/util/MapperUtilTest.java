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

package org.anhonesteffort.flock.test.util;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.anhonesteffort.flock.test.util.JsonHelpers.fromJson;
import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;

/**
 * rhodey
 *
 */
public class MapperUtilTest extends InstrumentationTestCase {

  public static final String KNOWN = "known";

  public static TestObj buildTestObj() {
    return new TestObj(KNOWN);
  }

  public void testDeserializeWithUnknownProperty() throws Exception {
    final AssetManager assets     = getInstrumentation().getContext().getAssets();
    final TestObj      testObj = fromJson(
        jsonFixture(assets, "fixtures/TestObjWithUnknown.json"),
        TestObj.class
    );

    assertTrue(testObj.equals(buildTestObj()));
  }

  public static class TestObj {

    @JsonProperty
    private String known;

    public TestObj() { }

    public TestObj(String known) {
      this.known = known;
    }

    public String getKnown() {
      return known;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null)               return false;
      if (!(other instanceof TestObj)) return false;

      final TestObj that = (TestObj) other;

      return this.known.equals(that.known);
    }
  }

}
