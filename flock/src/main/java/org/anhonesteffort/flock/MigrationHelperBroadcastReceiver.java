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

package org.anhonesteffort.flock;

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncWorker;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.DavKeyCollection;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.anhonesteffort.flock.sync.key.KeySyncWorker;

/**
 * rhodey.
 */
public class MigrationHelperBroadcastReceiver extends BroadcastReceiver {

  private static final String TAG = MigrationHelperBroadcastReceiver.class.getSimpleName();

  private static final String KEY_MIGRATION_UPDATED_HANDLED  = "MigrationHelperBroadcastReceiver.KEY_MIGRATION_UPDATED_HANDLED";
  private static final String KEY_UI_DISABLED_FOR_MIGRATION  = "MigrationHelperBroadcastReceiver.KEY_UI_DISABLED_FOR_MIGRATION";

  public static void setMigrationUpdateHandled(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    preferences.edit().putBoolean(KEY_MIGRATION_UPDATED_HANDLED, true).apply();
  }

  private static boolean getMigrationUpdateHandled(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    return preferences.getBoolean(KEY_MIGRATION_UPDATED_HANDLED, false);
  }

  public static void setUiDisabledForMigration(Context context,
                                               boolean uiDisabled)
  {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    preferences.edit().putBoolean(KEY_UI_DISABLED_FOR_MIGRATION, uiDisabled).apply();
  }

  public static boolean getUiDisabledForMigration(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    return preferences.getBoolean(KEY_UI_DISABLED_FOR_MIGRATION, false);
  }

  private void handleActionMyPackageReplaced(Context context) {
    Log.d(TAG, "handleActionMyPackageReplaced");

    if (MigrationHelperBroadcastReceiver.getMigrationUpdateHandled(context)) {
      Log.d(TAG, "migration already handled for this update, nothing to do.");
      return;
    }

    if (!DavAccountHelper.isUsingOurServers(context)) {
      Intent nextIntent = new Intent(context, MigrationReleaseNotesActivity.class);
      nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(nextIntent);
    }

    new KeySyncScheduler(context).requestSync();
    MigrationHelperBroadcastReceiver.setUiDisabledForMigration(context, true);
    MigrationHelperBroadcastReceiver.setMigrationUpdateHandled(context);

    Optional<Account> account = DavAccountHelper.getOsAccount(context);
    if (account.isPresent()) {
      new CalendarsSyncScheduler(context).cancelPendingSyncs(account.get());
      new AddressbookSyncScheduler(context).cancelPendingSyncs(account.get());

      new CalendarsSyncScheduler(context).setSyncEnabled(account.get(), false);
      new AddressbookSyncScheduler(context).setSyncEnabled(account.get(), false);
    }
    else
      Log.w(TAG, "account not present at handleActionMyPackageReplaced()");
  }

  private void handleActionMigrationStarted(Context context) {
    Log.d(TAG, "handleActionMigrationStarted");
    MigrationHelperBroadcastReceiver.setUiDisabledForMigration(context, true);
  }

  private void handleActionMigrationComplete(Context context) {
    Log.d(TAG, "handleActionMigrationComplete");
    MigrationHelperBroadcastReceiver.setUiDisabledForMigration(context, false);
  }

  private void handleActionKeyMaterialImported(Context context) {
    Log.d(TAG, "handleActionKeyMaterialImported");

    if (!DavKeyCollection.weStartedMigration(context))
      MigrationHelperBroadcastReceiver.setUiDisabledForMigration(context, false);
  }

  private void handleActionPushCreatedContacts(Context context) {
    Log.d(TAG, "handleActionPushCreatedContacts");
    MigrationService.hackOnActionPushCreatedContacts(context);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "onReceive()");

    if (intent == null || intent.getAction() == null) {
      Log.e(TAG, "received null intent or intent with null action.");
      return;
    }

    if (intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED))
      handleActionMyPackageReplaced(context);
    else if (intent.getAction().equals(AddressbookSyncWorker.ACTION_PUSH_CREATED_CONTACTS))
      handleActionPushCreatedContacts(context);
    else if (intent.getPackage() == null ||
             !intent.getPackage().equals(MigrationHelperBroadcastReceiver.class.getPackage().getName()))
    {
      Log.e(TAG, "received intent from untrusted package.");
    }
    else if (intent.getAction().equals(MigrationService.ACTION_MIGRATION_STARTED))
      handleActionMigrationStarted(context);
    else if (intent.getAction().equals(MigrationService.ACTION_MIGRATION_COMPLETE))
      handleActionMigrationComplete(context);
    else if (intent.getAction().equals(KeySyncWorker.ACTION_KEY_MATERIAL_IMPORTED))
      handleActionKeyMaterialImported(context);
    else
      Log.e(TAG, "received intent with unwanted action.");
  }
}
