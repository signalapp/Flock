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
import com.google.common.base.Optional;

/**
 * Programmer: rhodey
 */
public class FlockCardInformation {

  private static final String KEY_ACCOUNT_ID = "KEY_CARD_ACCOUNT_ID";
  private static final String KEY_LAST_FOUR  = "KEY_CARD_LAST_FOUR";
  private static final String KEY_EXPIRATION = "KEY_CARD_EXPIRATION";

  @JsonProperty
  protected String accountId;

  @JsonProperty
  protected String cardLastFour;

  @JsonProperty
  protected String cardExpiration;

  public FlockCardInformation(String accountId, String cardLastFour, String cardExpiration) {
    this.accountId      = accountId;
    this.cardLastFour   = cardLastFour;
    this.cardExpiration = cardExpiration;
  }

  public FlockCardInformation() {}

  public String getAccountId() {
    return accountId;
  }

  public String getCardLastFour() {
    return cardLastFour;
  }

  public String getCardExpiration() {
    if (cardExpiration.length() == 7)
      return cardExpiration.substring(0, 3) + cardExpiration.substring(5);

    return cardExpiration;
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();

    bundle.putString(KEY_ACCOUNT_ID, accountId);
    bundle.putString(KEY_LAST_FOUR,  cardLastFour);
    bundle.putString(KEY_EXPIRATION, cardExpiration);

    return bundle;
  }

  public static Optional<FlockCardInformation> build(Bundle bundledCardInformation) {
    if (bundledCardInformation == null ||
        bundledCardInformation.getString(KEY_ACCOUNT_ID) == null)
      return Optional.absent();

    return Optional.of(new FlockCardInformation(
        bundledCardInformation.getString(KEY_ACCOUNT_ID),
        bundledCardInformation.getString(KEY_LAST_FOUR),
        bundledCardInformation.getString(KEY_EXPIRATION))
    );
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                            return false;
    if (!(other instanceof FlockCardInformation)) return false;

    FlockCardInformation that = (FlockCardInformation)other;
    return this.accountId.equals(that.accountId)       &&
           this.cardLastFour.equals(that.cardLastFour) &&
           this.cardExpiration.equals(that.cardExpiration);
  }

  @Override
  public int hashCode() {
    return accountId.hashCode() ^ cardLastFour.hashCode() ^ cardExpiration.hashCode();
  }

}

