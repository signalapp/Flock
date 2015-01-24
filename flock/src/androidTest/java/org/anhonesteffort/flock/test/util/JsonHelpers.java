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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import org.anhonesteffort.flock.util.MapperUtil;

import java.io.IOException;

public class JsonHelpers {

  public static String asJson(Object object) throws JsonProcessingException {
    return MapperUtil.getMapper().writeValueAsString(object);
  }

  public static <T> T fromJson(String value, Class<T> clazz) throws IOException {
    return MapperUtil.getMapper().readValue(value, clazz);
  }

  public static String jsonFixture(AssetManager assets, String filename) throws IOException {
    return MapperUtil.getMapper().writeValueAsString(
        MapperUtil.getMapper().readValue(assets.open(filename), JsonNode.class)
    );
  }
}
