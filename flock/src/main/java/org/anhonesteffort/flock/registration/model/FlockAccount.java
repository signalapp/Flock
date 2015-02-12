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

import android.os.Bundle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.DateDeserializers;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * Programmer: rhodey
 * Date: 1/2/14
 */
public class FlockAccount {

  private static final String KEY_ACCOUNT_ID                = "KEY_ACCOUNT_ID";
  private static final String KEY_VERSION                   = "KEY_ACCOUNT_VERSION";
  private static final String KEY_SALT                      = "KEY_ACCOUNT_SALT";
  private static final String KEY_PASSWORD_SHA512           = "KEY_ACCOUNT_PASSWORD_SHA512";
  private static final String KEY_STRIPE_CUSTOMER_ID        = "KEY_ACCOUNT_STRIPE_CUSTOMER_ID";
  private static final String KEY_CREATE_DATE               = "KEY_ACCOUNT_CREATE_DATE";
  private static final String KEY_LAST_STRIPE_CHARGE_FAILED = "KEY_ACCOUNT_LAST_STRIPE_CHARGE_FAILED";
  private static final String KEY_AUTO_RENEW_ENABLED        = "KEY_ACCOUNT_AUTO_RENEW_ENABLED";
  private static final String KEY_SUBSCRIPTION_PLAN_TYPE    = "KEY_ACCOUNT_SUBSCRIPTION_PLAN_TYPE";
  private static final String KEY_SUBSCRIPTION_PLAN         = "KEY_ACCOUNT_SUBSCRIPTION_PLAN";

  @JsonProperty
  protected String id;

  @JsonProperty
  protected Integer version;

  @JsonProperty
  protected String salt;

  @JsonProperty
  protected String passwordSha512;

  @JsonProperty
  protected String stripeCustomerId;

  @JsonProperty
  @JsonSerialize(using = DateSerializer.class)
  @JsonDeserialize(using = DateDeserializers.DateDeserializer.class)
  protected Date createDate;

  @JsonProperty
  protected Boolean lastStripeChargeFailed;

  @JsonProperty
  protected Boolean autoRenewEnabled;

  @JsonProperty
   protected SubscriptionPlan subscriptionPlan;

  public FlockAccount(String           id,
                      Integer          version,
                      String           passwordSha512,
                      String           salt,
                      String           stripeCustomerId,
                      Date             createDate,
                      Boolean          lastStripeChargeFailed,
                      Boolean          autoRenewEnabled,
                      SubscriptionPlan subscriptionPlan)
  {
    this.id                      = id;
    this.version                 = version;
    this.salt                    = salt;
    this.passwordSha512          = passwordSha512;
    this.stripeCustomerId        = stripeCustomerId;
    this.createDate              = createDate;
    this.lastStripeChargeFailed  = lastStripeChargeFailed;
    this.autoRenewEnabled        = autoRenewEnabled;
    this.subscriptionPlan        = subscriptionPlan;
  }

  public FlockAccount() {}

  private String getHashedPassword(String password) {
    try {

      MessageDigest digest = MessageDigest.getInstance("SHA-512");
      digest.update(password.getBytes());
      return Base64.encodeBytes(digest.digest());

    } catch (NoSuchAlgorithmException e) {
      return null;
    }
  }

  public String getId() {
    return id.toLowerCase();
  }

  public Integer getVersion() {
    return version;
  }

  public String getSalt() {
    return salt;
  }

  public String getPasswordSha512() {
    return passwordSha512;
  }

  public void setPassword(String password) {
    this.passwordSha512 = getHashedPassword(password);
  }

  public String getStripeCustomerId() {
    return stripeCustomerId;
  }

  public Date getCreateDate() {
    return createDate;
  }

  public Boolean getLastStripeChargeFailed() {
    return lastStripeChargeFailed;
  }

  public Boolean getAutoRenewEnabled() {
    return autoRenewEnabled;
  }

  public SubscriptionPlan getSubscriptionPlan() {
    return subscriptionPlan;
  }

  public Bundle toBundle() throws JsonProcessingException {
    Bundle bundle = new Bundle();

    bundle.putString(KEY_ACCOUNT_ID,                 id);
    bundle.putInt(KEY_VERSION,                       version);
    bundle.putString(KEY_STRIPE_CUSTOMER_ID,         stripeCustomerId);
    bundle.putString(KEY_PASSWORD_SHA512,            passwordSha512);
    bundle.putLong(KEY_CREATE_DATE,                  createDate.getTime());
    bundle.putBoolean(KEY_LAST_STRIPE_CHARGE_FAILED, lastStripeChargeFailed);
    bundle.putBoolean(KEY_AUTO_RENEW_ENABLED,        autoRenewEnabled);
    bundle.putInt(KEY_SUBSCRIPTION_PLAN_TYPE,        subscriptionPlan.getPlanType());
    bundle.putString(KEY_SUBSCRIPTION_PLAN,          subscriptionPlan.serialize());

    return bundle;
  }

  public static Optional<FlockAccount> build(Bundle bundledAccount) throws JsonParseException {
    if (bundledAccount == null || bundledAccount.getString(KEY_ACCOUNT_ID) == null)
      return Optional.absent();

    Integer planType       = bundledAccount.getInt(KEY_SUBSCRIPTION_PLAN_TYPE);
    String  serializedPlan = bundledAccount.getString(KEY_SUBSCRIPTION_PLAN);

    SubscriptionPlan subscriptionPlan =
        SubscriptionPlan.buildFromSerialized(planType, serializedPlan);

    return Optional.of(new FlockAccount(bundledAccount.getString(KEY_ACCOUNT_ID),
                                        bundledAccount.getInt(KEY_VERSION),
                                        bundledAccount.getString(KEY_SALT),
                                        bundledAccount.getString(KEY_PASSWORD_SHA512),
                                        bundledAccount.getString(KEY_STRIPE_CUSTOMER_ID),
                                        new Date(bundledAccount.getLong(KEY_CREATE_DATE)),
                                        bundledAccount.getBoolean(KEY_LAST_STRIPE_CHARGE_FAILED),
                                        bundledAccount.getBoolean(KEY_AUTO_RENEW_ENABLED),
                                        subscriptionPlan));
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                    return false;
    if (!(other instanceof FlockAccount)) return false;

    FlockAccount that = (FlockAccount)other;

    if (subscriptionPlan != null && that.subscriptionPlan == null)
      return false;
    if (subscriptionPlan == null && that.subscriptionPlan != null)
      return false;

    if (subscriptionPlan == null) {
      return this.id.equals(that.id)                                         &&
             this.version.equals(that.version)                               &&
             this.salt.equals(that.salt)                                     &&
             this.passwordSha512.equals(that.passwordSha512)                 &&
             this.stripeCustomerId.equals(that.stripeCustomerId)             &&
             this.createDate.equals(that.createDate)                         &&
             this.lastStripeChargeFailed.equals(that.lastStripeChargeFailed) &&
             this.autoRenewEnabled.equals(that.autoRenewEnabled);
    }

    return this.id.equals(that.id)                                         &&
           this.version.equals(that.version)                               &&
           this.salt.equals(that.salt)                                     &&
           this.passwordSha512.equals(that.passwordSha512)                 &&
           this.stripeCustomerId.equals(that.stripeCustomerId)             &&
           this.createDate.equals(that.createDate)                         &&
           this.lastStripeChargeFailed.equals(that.lastStripeChargeFailed) &&
           this.autoRenewEnabled.equals(that.autoRenewEnabled)             &&
           this.subscriptionPlan.equals(that.subscriptionPlan);
  }

  @Override
  public int hashCode() {
    if (subscriptionPlan == null) {
      return id.hashCode() ^ version.hashCode() ^ salt.hashCode() ^
             passwordSha512.hashCode() ^ stripeCustomerId.hashCode() ^
             createDate.hashCode() ^ lastStripeChargeFailed.hashCode() ^
             autoRenewEnabled.hashCode();
    }

    return id.hashCode() ^ version.hashCode() ^ salt.hashCode() ^
           passwordSha512.hashCode() ^ stripeCustomerId.hashCode() ^
           createDate.hashCode() ^ lastStripeChargeFailed.hashCode() ^
           autoRenewEnabled.hashCode() ^ subscriptionPlan.hashCode();
  }

}