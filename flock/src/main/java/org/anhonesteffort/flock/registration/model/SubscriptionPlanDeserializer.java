/*
 * Copyright (C) 2014 Open Whisper Systems
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

package org.anhonesteffort.flock.registration.model;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * rhodey
 */
public class SubscriptionPlanDeserializer extends JsonDeserializer<SubscriptionPlan> {

  @Override
  public SubscriptionPlan deserialize(JsonParser             jsonParser,
                                      DeserializationContext deserializationContext)
    throws IOException, JsonProcessingException
  {
    try {

      JsonNode node      = jsonParser.getCodec().readTree(jsonParser);
      Integer  planType  = (Integer) node.get("plan_type").numberValue();
      String   accountId = node.get("account_id").textValue();
      String   planId    = node.get("plan_id").textValue();

      switch (planType) {
        case SubscriptionPlan.PLAN_TYPE_NONE:
          return new SubscriptionPlan(accountId, planType, planId);

        case SubscriptionPlan.PLAN_TYPE_STRIPE:
          return new StripePlan(accountId, planId);

        case SubscriptionPlan.PLAN_TYPE_GOOGLE:
          String purchaseToken  = node.get("purchase_token").textValue();
          Long   expirationDate = (Long) node.get("expiration_date").numberValue();
          return new GooglePlan(accountId, planId, purchaseToken, expirationDate);

        default: {
          throw new JsonParseException("unknown plan type " + planType, JsonLocation.NA);
        }
      }

    } catch (NullPointerException e) {
      throw new JsonParseException("something was null D:", JsonLocation.NA);
    }
  }

}
