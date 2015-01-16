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

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.anhonesteffort.flock.util.MapperUtil;

import java.io.IOException;

/**
 * rhodey
 */
@JsonDeserialize(using = SubscriptionPlanDeserializer.class)
public class SubscriptionPlan {

  public static final int PLAN_TYPE_NONE   = 0;
  public static final int PLAN_TYPE_STRIPE = 1;
  public static final int PLAN_TYPE_GOOGLE = 2;

  public static final SubscriptionPlan PLAN_NONE = new SubscriptionPlan("nope", SubscriptionPlan.PLAN_TYPE_NONE, "nope");

  @JsonProperty
  protected String accountId;

  @JsonProperty
  protected Integer planType;

  @JsonProperty
  protected String planId;

  public SubscriptionPlan() { }

  public SubscriptionPlan(String accountId, Integer planType, String planId) {
    this.accountId = accountId;
    this.planType  = planType;
    this.planId    = planId;
  }

  public static SubscriptionPlan buildFromSerialized(Integer planType,
                                                     String  serializedPlan)
      throws JsonParseException
  {
    try {
      switch (planType) {
        case PLAN_TYPE_NONE:
          return PLAN_NONE;

        case PLAN_TYPE_STRIPE:
          return MapperUtil.getMapper().readValue(serializedPlan, StripePlan.class);

        case PLAN_TYPE_GOOGLE:
          return MapperUtil.getMapper().readValue(serializedPlan, GooglePlan.class);

        default:
          Log.e(SubscriptionPlan.class.getName(), "unknown plan type" + planType);
          return PLAN_NONE;
      }
    } catch (IOException e) {
      throw new JsonParseException("unable to build plan for type " + planType, null, e);
    }
  }

  public String getAccountId() {
    return accountId;
  }

  public Integer getPlanType() {
    return planType;
  }

  public String getPlanId() {
    return planId;
  }

  public String serialize() throws JsonProcessingException {
    return MapperUtil.getMapper().writeValueAsString(this);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                        return false;
    if (!(other instanceof SubscriptionPlan)) return false;

    SubscriptionPlan that = (SubscriptionPlan)other;

    return this.accountId.equals(that.accountId) &&
           this.planType.equals(that.planType)   &&
           this.planId.equals(that.planId);
  }

  @Override
  public int hashCode() {
    return accountId.hashCode()  ^ planType.hashCode() ^ planId.hashCode();
  }

}
