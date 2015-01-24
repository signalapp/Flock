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

package org.anhonesteffort.flock.registration;

import android.util.Log;

import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.registration.model.FlockCardInformation;
import org.anhonesteffort.flock.util.MapperUtil;
import org.apache.http.HttpResponse;

import java.io.IOException;

/**
 * rhodey
 */
public class ModelFactory {

  public static AugmentedFlockAccount buildAccount(HttpResponse response)
      throws RegistrationApiParseException
  {
    try {

      return MapperUtil.getMapper().readValue(
          response.getEntity().getContent(), AugmentedFlockAccount.class
      );

    } catch (IOException e) {
      Log.e(ModelFactory.class.getName(), "unable to build account from HTTP response", e);
      throw new RegistrationApiParseException("unable to build account from HTTP response.");
    }
  }

  public static FlockCardInformation buildCard(HttpResponse response)
      throws RegistrationApiParseException
  {
    try {

      return MapperUtil.getMapper().readValue(
          response.getEntity().getContent(), FlockCardInformation.class
      );

    } catch (IOException e) {
      Log.e(ModelFactory.class.getName(), "unable to build card information from HTTP response", e);
      throw new RegistrationApiParseException("unable to build card information from HTTP response.");
    }
  }

  public static Boolean buildBoolean(HttpResponse response)
      throws RegistrationApiParseException
  {
    try {

      return MapperUtil.getMapper().readValue(
          response.getEntity().getContent(), Boolean.class
      );

    } catch (IOException e) {
      Log.e(ModelFactory.class.getName(), "unable to build boolean from HTTP response", e);
      throw new RegistrationApiParseException("unable to build boolean from HTTP response.");
    }
  }

}
