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

package org.anhonesteffort.flock.sync.account;

import android.content.Context;
import android.content.SyncResult;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.model.AugmentedFlockAccount;
import org.anhonesteffort.flock.sync.SyncWorker;
import org.anhonesteffort.flock.sync.SyncWorkerUtil;

import java.io.IOException;

/**
 * rhodey
 */
public class AccountSyncWorker implements SyncWorker {

  private static final String TAG = "org.anhonesteffort.flock.sync.account.AccountSyncWorker";

  private final Context              context;
  private final DavAccount           account;
  private final SyncResult           result;
  private final RegistrationApi      registration;

  public AccountSyncWorker(Context              context,
                           DavAccount           account,
                           SyncResult           syncResult)
      throws RegistrationApiException
  {
    this.context        = context;
    this.account        = account;
    this.result         = syncResult;

    registration = new RegistrationApi(context);
  }

  private Optional<AugmentedFlockAccount> handleUpdateFlockAccountCache() {
    Log.d(TAG, "handleUpdateFlockAccountCache");

    AugmentedFlockAccount flockAccount = null;

    try {

      flockAccount = registration.getAccount(account);
      AccountStore.updateStore(context, flockAccount);

    } catch (RegistrationApiException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (JsonProcessingException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }

    return Optional.fromNullable(flockAccount);
  }

  @Override
  public void run() {
    handleUpdateFlockAccountCache();
  }

  @Override
  public void cleanup() {

  }

}
