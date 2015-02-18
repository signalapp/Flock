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

import org.anhonesteffort.flock.registration.ModelFactory;
import org.anhonesteffort.flock.test.InstrumentationTestCaseWithMocks;
import org.anhonesteffort.flock.test.registration.model.AugmentedFlockAccountTest;
import org.anhonesteffort.flock.test.registration.model.FlockCardInformationTest;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.anhonesteffort.flock.test.util.JsonHelpers.jsonFixture;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * rhodey
 */
public class ModelFactoryTest extends InstrumentationTestCaseWithMocks {

  private AssetManager assets;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    assets = getInstrumentation().getContext().getAssets();
  }

  private InputStream asStream(String filename) throws IOException {
    return new ByteArrayInputStream(jsonFixture(assets, filename).getBytes());
  }

  public void testBuildAccount() throws Exception {
    final HttpResponse response = mock(HttpResponse.class);
    final HttpEntity   entity   = mock(HttpEntity.class);

    when(response.getEntity()).thenReturn(entity);
    when(entity.getContent()).thenReturn(asStream("fixtures/AugmentedFlockAccount.json"));

    assertTrue(
        ModelFactory.buildAccount(response).equals(AugmentedFlockAccountTest.accountNoPlan())
    );
  }

  public void testBuildCard() throws Exception {
    final HttpResponse response = mock(HttpResponse.class);
    final HttpEntity   entity   = mock(HttpEntity.class);

    when(response.getEntity()).thenReturn(entity);
    when(entity.getContent()).thenReturn(asStream("fixtures/FlockCardInformation.json"));

    assertTrue(
        ModelFactory.buildCard(response).equals(FlockCardInformationTest.card())
    );
  }

  public void testBuildBooleanTrue() throws Exception {
    final HttpResponse response   = mock(HttpResponse.class);
    final HttpEntity   entity     = mock(HttpEntity.class);
    final InputStream  trueStream = new ByteArrayInputStream("true".getBytes());

    when(response.getEntity()).thenReturn(entity);
    when(entity.getContent()).thenReturn(trueStream);

    assertTrue(
        ModelFactory.buildBoolean(response).equals(Boolean.TRUE)
    );
  }

  public void testBuildBooleanFalse() throws Exception {
    final HttpResponse response    = mock(HttpResponse.class);
    final HttpEntity   entity      = mock(HttpEntity.class);
    final InputStream  falseStream = new ByteArrayInputStream("false".getBytes());

    when(response.getEntity()).thenReturn(entity);
    when(entity.getContent()).thenReturn(falseStream);

    assertTrue(
        ModelFactory.buildBoolean(response).equals(Boolean.FALSE)
    );
  }

}
