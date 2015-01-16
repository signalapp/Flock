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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * rhodey
 */
public class GooglePlan extends SubscriptionPlan {

  public static final Integer BILLING_SAFETY_DAY_COUNT = 2;

  @JsonProperty
  protected String packageName;

  @JsonProperty
  protected String purchaseToken;

  @JsonProperty
  protected Long expirationDate;

  public GooglePlan() { }

  public GooglePlan(String accountId,
                    String subscriptionId,
                    String purchaseToken,
                    Long   expirationDate)
  {
    super(accountId, SubscriptionPlan.PLAN_TYPE_GOOGLE, subscriptionId);

    this.packageName    = "org.anhonesteffort.flock";
    this.purchaseToken  = purchaseToken;
    this.expirationDate = expirationDate;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getPurchaseToken() {
    return purchaseToken;
  }

  public Long getExpirationDate() {
    return expirationDate;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                  return false;
    if (!(other instanceof GooglePlan)) return false;
    if (!super.equals(other))           return false;

    GooglePlan that = (GooglePlan)other;

    return this.packageName.equals(that.packageName)     &&
           this.purchaseToken.equals(that.purchaseToken) &&
           this.expirationDate.equals(that.expirationDate);
  }

  @Override
  public int hashCode() {
    return super.hashCode() ^ packageName.hashCode() ^
           purchaseToken.hashCode() ^ expirationDate.hashCode();
  }
}