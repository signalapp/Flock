/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package org.anhonesteffort.flock.registration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.DateDeserializers;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;

import java.util.Date;

/**
 * Programmer: rhodey
 */
public class FlockSubscription {

  @JsonProperty
  protected String accountId;

  @JsonProperty
  protected String paymentId;

  @JsonProperty
  @JsonSerialize(using = DateSerializer.class)
  @JsonDeserialize(using = DateDeserializers.DateDeserializer.class)
  protected Date createDate;

  @JsonProperty
  protected Integer daysCredit;

  @JsonProperty
  protected Double costUsd;

  public FlockSubscription() {}

  public FlockSubscription(String  accountId,
                           String  paymentId,
                           Date    createDate,
                           Integer days_credit,
                           Double  costUsd)
  {
    this.accountId  = accountId;
    this.paymentId  = paymentId;
    this.createDate = createDate;
    this.daysCredit = days_credit;
    this.costUsd    = costUsd;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getPaymentId() {
    return paymentId;
  }

  public Date getCreateDate() {
    return createDate;
  }

  public int getDaysCredit() {
    return daysCredit;
  }

  public void setDaysCredit(int days_credit) {
    daysCredit = days_credit;
  }

  public Double getCostUsd() {
    return costUsd;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) return false;
    if (!(other instanceof FlockSubscription)) return false;

    FlockSubscription that = (FlockSubscription)other;
    return this.accountId.equals(that.accountId) && this.paymentId.equals(that.paymentId) &&
           this.createDate.equals(that.createDate) && this.daysCredit.equals(that.daysCredit) &&
           this.costUsd.equals(that.costUsd);
  }

  @Override
  public int hashCode() {
    return accountId.hashCode() ^ paymentId.hashCode() ^ createDate.hashCode() ^
           daysCredit.hashCode() ^ costUsd.hashCode();
  }

}
