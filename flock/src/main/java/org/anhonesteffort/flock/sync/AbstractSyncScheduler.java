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

package org.anhonesteffort.flock.sync;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.PreferencesActivity;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;

/**
 * Programmer: rhodey
 */
public abstract class AbstractSyncScheduler extends ContentObserver {

  private static final String PREFERENCES_NAME    = "AbstractSyncScheduler.PREFERENCES_NAME";
  private static final String KEY_SYNC_OVERRIDDEN = "AbstractSyncScheduler.KEY_SYNC_OVERRIDDEN";
  private static final String KEY_TIME_LAST_SYNC  = "AbstractSyncScheduler.KEY_TIME_LAST_SYNC";

  protected Context context;

  public AbstractSyncScheduler(Context context) {
    super(null);
    this.context = context;
  }

  protected abstract String getTAG();
  protected abstract String getAuthority();
  protected abstract Uri    getUri();

  public void registerSelfForBroadcasts() {
    context.getContentResolver().unregisterContentObserver(this);
    context.getContentResolver().registerContentObserver(getUri(), false, this);
  }

  public boolean syncInProgress(Account account) {
    for (SyncInfo syncInfo : ContentResolver.getCurrentSyncs()) {
      if (syncInfo.account.type.equals(account.type) && syncInfo.authority.equals(getAuthority()))
        return true;
    }

    return ContentResolver.isSyncActive(account, getAuthority());
  }

  public boolean getIsSyncEnabled(Account account) {
    return ContentResolver.getIsSyncable(account, getAuthority()) > 0;
  }

  public void setSyncEnabled(Account account, boolean enabled) {
    if (!enabled && getAuthority().equals(KeySyncScheduler.CONTENT_AUTHORITY)) {
      Log.w(getTAG(), "cannot disable key sync service, not changing sync setting.");
      return;
    }

    ContentResolver.setIsSyncable(account, getAuthority(), enabled ? 1 : 0);
  }

  public void cancelPendingSyncs(Account account) {
    ContentResolver.cancelSync(account, getAuthority());
  }

  public void requestSync() {
    Optional<DavAccount> account  = DavAccountHelper.getAccount(context);
    if (!account.isPresent()) {
      Log.e(getTAG(), "account  not present, cannot request sync.");
      return;
    }

    handleInitSyncAdapter();

    ContentResolver.requestSync(account.get().getOsAccount(), getAuthority(), new Bundle());
  }

  public void setSyncInterval(int minutes) {
    long SECONDS_PER_MINUTE = 60L;
    long SYNC_INTERVAL      = minutes * SECONDS_PER_MINUTE;

    Log.d(getTAG(), "setSyncInterval() " + minutes);

    Optional<DavAccount> account  = DavAccountHelper.getAccount(context);
    if (!account.isPresent()) {
      Log.e(getTAG(), "account  not present, interval cannot be set");
      return;
    }

    if (minutes > 0) {
      ContentResolver.addPeriodicSync(account.get().getOsAccount(),
          getAuthority(),
          new Bundle(),
          SYNC_INTERVAL);
    }
  }

  private static SharedPreferences getSharedPreferences(Context context) {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  public void restoreSyncIntervalFromUserSetting() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

    setSyncInterval(Integer.valueOf(
        preferences.getString(PreferencesActivity.KEY_PREF_SYNC_INTERVAL_MINUTES, "60")
    ));
  }

  public void setTimeLastSync(Long timeMilliseconds) {
    getSharedPreferences(context).edit().putLong(
        KEY_TIME_LAST_SYNC + getAuthority(), timeMilliseconds
    ).apply();
  }

  public Optional<Long> getTimeLastSync() {
    Long timeMilliseconds = getSharedPreferences(context).getLong(KEY_TIME_LAST_SYNC + getAuthority(), -1);

    if (timeMilliseconds == -1)
      return Optional.absent();

    return Optional.of(timeMilliseconds);
  }

  public void onAccountRemoved() {
    Log.d(getTAG(), "onAccountRemoved()");
    getSharedPreferences(context).edit().putBoolean(
        KEY_SYNC_OVERRIDDEN + getAuthority(), false
    ).apply();
  }

  private void handleInitSyncAdapter() {
    SharedPreferences settings = getSharedPreferences(context);

    if (settings.getBoolean(KEY_SYNC_OVERRIDDEN + getAuthority(), false))
      return;

    Optional<DavAccount> account = DavAccountHelper.getAccount(context);
    if (!account.isPresent()) {
      Log.e(getTAG(), "account  not present, cannot init sync adapter");
      return;
    }

    ContentResolver.setSyncAutomatically(account.get().getOsAccount(), getAuthority(), true);
    settings.edit().putBoolean(KEY_SYNC_OVERRIDDEN + getAuthority(), true).apply();

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    setSyncInterval(Integer.valueOf(
        preferences.getString(PreferencesActivity.KEY_PREF_SYNC_INTERVAL_MINUTES, "60")
    ));
  }

  @Override
  public void onChange(boolean selfChange, Uri changeUri) {
    SharedPreferences    settings = getSharedPreferences(context);
    Optional<DavAccount> account  = DavAccountHelper.getAccount(context);

    if (account.isPresent())
      handleInitSyncAdapter();

    if (settings.getBoolean(PreferencesActivity.KEY_PREF_SYNC_ON_CONTENT_CHANGE, false) &&
        account.isPresent())
    {
      requestSync();
    }
  }

  @Override
  public void onChange(boolean selfChange) {
    onChange(selfChange, null);
  }

}
