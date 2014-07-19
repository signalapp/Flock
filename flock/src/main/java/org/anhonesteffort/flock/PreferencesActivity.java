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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.chiralcode.colorpicker.ColorPickerPreference;
import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.anhonesteffort.flock.util.ColorUtils;

/**
 * Programmer: rhodey
 */
public class PreferencesActivity extends PreferenceActivity
    implements Preference.OnPreferenceChangeListener
{

  public static final String KEY_PREF_SYNC_INTERVAL_MINUTES  = "pref_sync_interval_minutes";
  public static final String KEY_PREF_SYNC_ON_CONTENT_CHANGE = "pref_sync_on_content_change";
  public static final String KEY_PREF_SYNC_NOW               = "pref_sync_now";
  public static final String KEY_PREF_DEFAULT_CALENDAR_COLOR = "pref_default_calendar_color";

  public static final String KEY_PREF_CATEGORY_CONTACTS = "pref_category_contacts";
  public static final String KEY_PREF_ADDRESSBOOKS      = "pref_addressbooks";

  public static final String KEY_PREF_CATEGORY_ACCOUNT = "pref_category_account";
  public static final String KEY_PREF_SUBSCRIPTION     = "pref_subscription";
  public static final String KEY_PREF_DELETE_ACCOUNT   = "pref_delete_account";

  private StatusHeaderView statusHeader;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    statusHeader = new StatusHeaderView(getBaseContext());
    getListView().addHeaderView(statusHeader, null, false);

    addPreferencesFromResource(R.xml.preferences);
    getActionBar().setDisplayHomeAsUpEnabled(false);
    getActionBar().setTitle(R.string.app_name);

    findPreference(KEY_PREF_SYNC_INTERVAL_MINUTES).setOnPreferenceChangeListener(this);
    findPreference(KEY_PREF_SYNC_ON_CONTENT_CHANGE).setOnPreferenceChangeListener(this);
    findPreference(KEY_PREF_DEFAULT_CALENDAR_COLOR).setOnPreferenceChangeListener(this);

    initContentObservers();
    initSyncNowButton();
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!DavAccountHelper.isAccountRegistered(getBaseContext())) {
      Intent nextIntent = new Intent(getBaseContext(), SetupActivity.class);
      nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(nextIntent);
      finish();
    }

    else {
      initPreferencesForOwsUsers();
      initPreferencesForNonOwsUsers();
      updateSyncIntervalSummary(Optional.<String>absent());
      updateCalendarColorSummary(Optional.<Integer>absent());

      statusHeader.handleStartPerpetualRefresh();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    statusHeader.hackOnPause();
  }

  private void initContentObservers() {
    new AddressbookSyncScheduler(getBaseContext()).registerSelfForBroadcasts();
    new CalendarsSyncScheduler(getBaseContext()).registerSelfForBroadcasts();
  }

  private void initSyncNowButton() {
    Preference syncNowPreference = findPreference(KEY_PREF_SYNC_NOW);
    syncNowPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

      @Override
      public boolean onPreferenceClick(Preference preference) {

        new KeySyncScheduler(getBaseContext()).requestSync();
        new CalendarsSyncScheduler(getBaseContext()).requestSync();
        new AddressbookSyncScheduler(getBaseContext()).requestSync();

        Toast.makeText(getBaseContext(),
                       R.string.sync_requested_will_begin_when_possible,
                       Toast.LENGTH_SHORT).show();

        return false;
      }

    });
  }

  private void updateSyncIntervalSummary(Optional<String> value) {
    EditTextPreference syncIntervalPreference =
        (EditTextPreference) findPreference(KEY_PREF_SYNC_INTERVAL_MINUTES);

    if (value.isPresent())
      syncIntervalPreference.setSummary(value.get() + " " + getString(R.string.minutes));
    else {
      String intervalMinutes = syncIntervalPreference.getText();
      syncIntervalPreference.setSummary(intervalMinutes +  " " + getString(R.string.minutes));
    }
  }

  private void updateCalendarColorSummary(Optional<Integer> value) {
    ColorUtils colorUtils = new ColorUtils();

    ColorPickerPreference calendarColorPreference =
        (ColorPickerPreference) findPreference(KEY_PREF_DEFAULT_CALENDAR_COLOR);

    if (value.isPresent()) {
      String colorName = colorUtils.getColorNameFromHex(value.get());
      calendarColorPreference.setSummary(getString(R.string.new_calendars_will_be) + " '" + colorName + "'");
    }
    else {
      SharedPreferences settings        = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      int               colorFlockTheme = getResources().getColor(R.color.flocktheme_color);
      Integer           calendarColor   = settings.getInt(KEY_PREF_DEFAULT_CALENDAR_COLOR, colorFlockTheme);
      String            colorName       = colorUtils.getColorNameFromHex(calendarColor);

      if (colorName != null)
        calendarColorPreference.setSummary(getString(R.string.new_calendars_will_be) + " '" + colorName + "'");
    }
  }

  private void initPreferencesForOwsUsers() {
    if (!DavAccountHelper.isUsingOurServers(getBaseContext()))
      return;

    Preference         manageSubscription =                      findPreference(KEY_PREF_SUBSCRIPTION);
    Preference         addressbooks       =                      findPreference(KEY_PREF_ADDRESSBOOKS);
    PreferenceCategory category           = (PreferenceCategory) findPreference(KEY_PREF_CATEGORY_CONTACTS);

    if (manageSubscription != null) {
      manageSubscription.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
          Optional<DavAccount> account = DavAccountHelper.getAccount(getBaseContext());
          if (account.isPresent()) {
            Intent nextIntent = new Intent(getBaseContext(), ManageSubscriptionActivity.class);
            nextIntent.putExtra(ManageSubscriptionActivity.KEY_DAV_ACCOUNT_BUNDLE,
                                account.get().toBundle());
            startActivity(nextIntent);
          }

          return true;
        }

      });
    }

    if (addressbooks != null)
      category.removePreference(addressbooks);
  }

  private void initPreferencesForNonOwsUsers() {
    if (DavAccountHelper.isUsingOurServers(getBaseContext()))
      return;

    PreferenceCategory accountCategory    = (PreferenceCategory) findPreference(KEY_PREF_CATEGORY_ACCOUNT);
    Preference         manageSubscription =                      findPreference(KEY_PREF_SUBSCRIPTION);
    Preference         deleteAccount      =                      findPreference(KEY_PREF_DELETE_ACCOUNT);

    if (manageSubscription != null) {
      accountCategory.removePreference(manageSubscription);
      accountCategory.removePreference(deleteAccount);
    }
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    if (preference.getKey().equals(KEY_PREF_SYNC_INTERVAL_MINUTES)) {
      new KeySyncScheduler(getBaseContext()).setSyncInterval(Integer.valueOf((String) newValue));
      new AddressbookSyncScheduler(getBaseContext()).setSyncInterval(Integer.valueOf((String) newValue));
      new CalendarsSyncScheduler(getBaseContext()).setSyncInterval(Integer.valueOf((String) newValue));

      updateSyncIntervalSummary(Optional.of((String) newValue));
    }

    else if (preference.getKey().equals(KEY_PREF_DEFAULT_CALENDAR_COLOR))
      updateCalendarColorSummary(Optional.of((Integer) newValue));

    return true;
  }

}