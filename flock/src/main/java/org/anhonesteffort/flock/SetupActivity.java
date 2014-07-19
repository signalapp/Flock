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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.wizardpager.wizard.ui.StepPagerStrip;
import com.google.common.base.Optional;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;

/**
 * Programmer: rhodey
 */
public class SetupActivity extends FragmentActivity {

  protected static final String KEY_SETUP_STATE         = "SetupActivity.KEY_MANAGE_STATE";
  protected static final String KEY_NAVIGATION_DISABLED = "SetupActivity.KEY_NAVIGATION_DISABLED";
  protected static final String KEY_SERVICE_PROVIDER    = "SetupActivity.KEY_SERVICE_PROVIDER";
  protected static final String KEY_IS_NEW_ACCOUNT      = "SetupActivity.KEY_IS_NEW_ACCOUNT";
  protected static final String KEY_DAV_TEST_HOST       = "SetupActivity.KEY_DAV_TEST_HOST";
  protected static final String KEY_DAV_TEST_USERNAME   = "KEY_DAV_TEST_USERNAME";

  protected static final int STATE_INTRO                      = 0;
  protected static final int STATE_SELECT_SERVICE_PROVIDER    = 1;
  protected static final int STATE_TEST_SERVICE_PROVIDER      = 2;
  protected static final int STATE_CONFIGURE_SERVICE_PROVIDER = 3;
  protected static final int STATE_IMPORT_CONTACTS            = 4;
  protected static final int STATE_IMPORT_CALENDARS           = 5;
  protected static final int STATE_SELECT_REMOTE_ADDRESSBOOK  = 6;
  protected static final int STATE_SELECT_REMOTE_CALENDARS    = 7;

  protected static final int SERVICE_PROVIDER_OWS   = 0;
  protected static final int SERVICE_PROVIDER_OTHER = 1;

  private int               state              = STATE_INTRO;
  private boolean           navigationDisabled = false;
  private Optional<Integer> serviceProvider    = Optional.absent();
  private Optional<Boolean> isNewAccount       = Optional.absent();
  private Optional<String>  davTestHost        = Optional.absent();
  private Optional<String>  davTestUsername    = Optional.absent();

  private StepPagerStrip setupStepIndicator;
  private TextView       setupStepTitle;
  private Button         buttonPrevious;
  private Button         buttonNext;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.setup_activity);
    getActionBar().setDisplayHomeAsUpEnabled(false);
    getActionBar().setTitle(R.string.app_name);

    setupStepIndicator = (StepPagerStrip) findViewById(R.id.setup_step_indicator);
    setupStepTitle     = (TextView)       findViewById(R.id.setup_activity_large_text);
    buttonPrevious     = (Button)         findViewById(R.id.button_previous);
    buttonNext         =  (Button)        findViewById(R.id.button_next);

    buttonPrevious.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View view) {
        handleButtonPrevious();
      }

    });
  }

  protected void setNavigationDisabled(boolean navigationDisabled) {
    this.navigationDisabled = navigationDisabled;
  }

  @Override
  public void onBackPressed() {
    handleButtonPrevious();
  }

  private void limitMultipleAccounts() {
    if (DavAccountHelper.isAccountRegistered(getBaseContext())) {
      Toast.makeText(this, R.string.error_multiple_accounts_not_allowed, Toast.LENGTH_SHORT).show();
      finish();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (state == STATE_SELECT_SERVICE_PROVIDER || state == STATE_TEST_SERVICE_PROVIDER ||
        state == STATE_CONFIGURE_SERVICE_PROVIDER)
    {
      limitMultipleAccounts();
    }

    updateFragmentUsingState(state);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);

    if (savedInstanceState != null) {
      if (savedInstanceState.containsKey(KEY_SETUP_STATE))
        state = savedInstanceState.getInt(KEY_SETUP_STATE);

      if (savedInstanceState.containsKey(KEY_NAVIGATION_DISABLED))
        navigationDisabled = savedInstanceState.getBoolean(KEY_NAVIGATION_DISABLED);

      if (savedInstanceState.containsKey(KEY_SERVICE_PROVIDER))
        serviceProvider = Optional.of(savedInstanceState.getInt(KEY_SERVICE_PROVIDER));

      if (savedInstanceState.containsKey(KEY_IS_NEW_ACCOUNT))
        isNewAccount = Optional.of(savedInstanceState.getBoolean(KEY_IS_NEW_ACCOUNT));

      if (savedInstanceState.containsKey(KEY_DAV_TEST_HOST))
        davTestHost = Optional.of(savedInstanceState.getString(KEY_DAV_TEST_HOST));

      if (savedInstanceState.containsKey(KEY_DAV_TEST_USERNAME))
        davTestUsername = Optional.of(savedInstanceState.getString(KEY_DAV_TEST_USERNAME));
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(KEY_SETUP_STATE, state);
    outState.putBoolean(KEY_NAVIGATION_DISABLED, navigationDisabled);

    if (serviceProvider.isPresent())
      outState.putInt(KEY_SERVICE_PROVIDER, serviceProvider.get());

    if (isNewAccount.isPresent())
      outState.putBoolean(KEY_IS_NEW_ACCOUNT, isNewAccount.get());

    if (davTestHost.isPresent())
      outState.putString(KEY_DAV_TEST_HOST, davTestHost.get());

    if (davTestUsername.isPresent())
      outState.putString(KEY_DAV_TEST_USERNAME, davTestUsername.get());
  }

  protected void setServiceProvider(Integer provider) {
    this.serviceProvider = Optional.of(provider);
  }

  protected void setDavTestOptions(String davTestHost, String davTestUsername) {
    this.davTestHost     = Optional.of(davTestHost);
    this.davTestUsername = Optional.of(davTestUsername);
  }

  protected void setIsNewAccount(Boolean isNew) {
    this.isNewAccount = Optional.of(isNew);
  }

  protected void handleSetupComplete() {
    new KeySyncScheduler(getBaseContext()).requestSync();
    Toast.makeText(getBaseContext(), R.string.setup_complete, Toast.LENGTH_LONG).show();

    Intent nextIntent = new Intent(getBaseContext(), PreferencesActivity.class);
    nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(nextIntent);

    finish();
  }

  protected void updateFragmentUsingState(int newState) {
    FragmentTransaction fragmentTransaction;
    Fragment            nextFragment;
    boolean             replaceFragment = (newState != STATE_INTRO && state != newState);

    switch (newState) {

      case STATE_INTRO:
        setupStepIndicator.setVisibility(View.GONE);
        setupStepTitle.setVisibility(View.GONE);

        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        nextFragment        = new IntroductionFragment();

        fragmentTransaction.replace(R.id.fragment_view, nextFragment);
        fragmentTransaction.commit();

        buttonPrevious.setVisibility(View.INVISIBLE);
        buttonNext.setText(R.string.begin);
        buttonNext.setVisibility(View.VISIBLE);
        break;

      case STATE_SELECT_SERVICE_PROVIDER:
        setupStepIndicator.setVisibility(View.GONE);
        setupStepIndicator.setCurrentPage(0);
        setupStepTitle.setVisibility(View.VISIBLE);
        setupStepTitle.setText(R.string.title_chose_sync_service);

        if (replaceFragment) {
          fragmentTransaction = getSupportFragmentManager().beginTransaction();
          nextFragment        = new SelectServiceProviderFragment();

          fragmentTransaction.replace(R.id.fragment_view, nextFragment);
          fragmentTransaction.commit();
        }

        buttonPrevious.setVisibility(View.VISIBLE);
        buttonNext.setText(R.string.next);
        buttonNext.setVisibility(View.VISIBLE);
        break;

      case STATE_TEST_SERVICE_PROVIDER:
        setupStepIndicator.setVisibility(View.VISIBLE);
        setupStepIndicator.setPageCount(7);
        setupStepIndicator.setCurrentPage(1);
        setupStepTitle.setText(R.string.title_server_tests);

        if (replaceFragment) {
          fragmentTransaction = getSupportFragmentManager().beginTransaction();
          nextFragment        = new ServerTestsFragment();

          fragmentTransaction.replace(R.id.fragment_view, nextFragment);
          fragmentTransaction.commit();
        }

        buttonPrevious.setVisibility(View.VISIBLE);
        buttonNext.setText(R.string.next);
        buttonNext.setVisibility(View.VISIBLE);
        break;

      case STATE_CONFIGURE_SERVICE_PROVIDER:
        setupStepIndicator.setVisibility(View.VISIBLE);
        setupStepTitle.setText(R.string.title_server_tests);

        if (serviceProvider.isPresent() && serviceProvider.get().equals(SERVICE_PROVIDER_OTHER)) {
          setupStepIndicator.setPageCount(7);
          setupStepIndicator.setCurrentPage(2);
          setupStepTitle.setText(R.string.title_import_account);

          ImportOtherAccountFragment hack = new ImportOtherAccountFragment();
          if (davTestHost.isPresent() && davTestUsername.isPresent())
            hack.setDavTestOptions(davTestHost.get(), davTestUsername.get());

          nextFragment = hack;
        }
        else if (isNewAccount.isPresent() && isNewAccount.get()) {
          setupStepIndicator.setPageCount(4);
          setupStepIndicator.setCurrentPage(1);
          setupStepTitle.setText(R.string.title_register_account);

          nextFragment = new RegisterAccountFragment();
        }
        else {
          setupStepIndicator.setPageCount(2);
          setupStepIndicator.setCurrentPage(1);
          setupStepTitle.setText(R.string.title_import_account);

          nextFragment = new ImportOwsAccountFragment();
        }

        if (replaceFragment) {
          fragmentTransaction = getSupportFragmentManager().beginTransaction();
          fragmentTransaction.replace(R.id.fragment_view, nextFragment);
          fragmentTransaction.commit();
        }

        buttonPrevious.setVisibility(View.VISIBLE);
        buttonNext.setText(R.string.next);
        buttonNext.setVisibility(View.VISIBLE);
        break;

      case STATE_IMPORT_CONTACTS:
        if (serviceProvider.isPresent() && serviceProvider.get().equals(SERVICE_PROVIDER_OWS) &&
            isNewAccount.isPresent() && !isNewAccount.get())
        {
          handleSetupComplete();
          break;
        }

        if (serviceProvider.isPresent() && !serviceProvider.get().equals(SERVICE_PROVIDER_OWS)) {
          setupStepIndicator.setPageCount(7);
          setupStepIndicator.setCurrentPage(3);
        }
        else {
          setupStepIndicator.setPageCount(4);
          setupStepIndicator.setCurrentPage(2);
        }

        setupStepTitle.setText(R.string.title_import_contacts);

        Toast.makeText(getBaseContext(),
                       R.string.select_accounts_to_import_contacts_from,
                       Toast.LENGTH_LONG).show();

        if (replaceFragment) {
          fragmentTransaction = getSupportFragmentManager().beginTransaction();
          nextFragment        = new ImportContactsFragment();

          fragmentTransaction.replace(R.id.fragment_view, nextFragment);
          fragmentTransaction.commit();
        }

        buttonPrevious.setVisibility(View.INVISIBLE);
        buttonNext.setText(R.string.next);
        buttonNext.setVisibility(View.VISIBLE);
        break;

      case STATE_IMPORT_CALENDARS:
        if (serviceProvider.isPresent() && !serviceProvider.get().equals(SERVICE_PROVIDER_OWS)) {
          setupStepIndicator.setPageCount(7);
          setupStepIndicator.setCurrentPage(4);
        }
        else {
          setupStepIndicator.setPageCount(4);
          setupStepIndicator.setCurrentPage(3);
        }
        setupStepTitle.setText(R.string.title_import_calendars);

        Toast.makeText(getBaseContext(),
                       R.string.select_calendars_to_import,
                       Toast.LENGTH_SHORT).show();

        if (replaceFragment) {
          fragmentTransaction = getSupportFragmentManager().beginTransaction();
          nextFragment        = new ImportCalendarsFragment();

          fragmentTransaction.replace(R.id.fragment_view, nextFragment);
          fragmentTransaction.commit();
        }

        buttonPrevious.setVisibility(View.VISIBLE);
        buttonNext.setText(R.string.next);
        buttonNext.setVisibility(View.VISIBLE);
        break;

      case STATE_SELECT_REMOTE_ADDRESSBOOK:
        if (serviceProvider.isPresent() && serviceProvider.get().equals(SERVICE_PROVIDER_OWS) &&
            isNewAccount.isPresent() && isNewAccount.get())
        {
          handleSetupComplete();
          break;
        }

        setupStepIndicator.setPageCount(7);
        setupStepIndicator.setCurrentPage(5);
        setupStepTitle.setText(R.string.title_my_addressbooks);

        Toast.makeText(getBaseContext(),
                       R.string.select_a_single_remote_addressbook_in_which_to_store,
                       Toast.LENGTH_LONG).show();

        if (replaceFragment) {
          fragmentTransaction = getSupportFragmentManager().beginTransaction();
          nextFragment        = new MyAddressbooksFragment();

          fragmentTransaction.replace(R.id.fragment_view, nextFragment);
          fragmentTransaction.commit();
        }

        buttonPrevious.setVisibility(View.VISIBLE);
        buttonNext.setText(R.string.next);
        buttonNext.setVisibility(View.VISIBLE);
        break;

      case STATE_SELECT_REMOTE_CALENDARS:
        setupStepIndicator.setPageCount(7);
        setupStepIndicator.setCurrentPage(6);
        setupStepTitle.setText(R.string.title_my_calendars);

        Toast.makeText(getBaseContext(),
                       R.string.select_the_calendars_you_would_like_to_sync_with_this_device,
                       Toast.LENGTH_LONG).show();

        if (replaceFragment) {
          fragmentTransaction = getSupportFragmentManager().beginTransaction();
          nextFragment        = new MyCalendarsFragment();

          fragmentTransaction.replace(R.id.fragment_view, nextFragment);
          fragmentTransaction.commit();
        }

        buttonPrevious.setVisibility(View.VISIBLE);
        buttonNext.setText(R.string.next);
        buttonNext.setVisibility(View.VISIBLE);
        break;
    }

    state = newState;
  }

  private void handleButtonPrevious() {
    if (navigationDisabled)
      return;

    setupStepTitle.setTextColor(0xff0099cc);
    buttonNext.setBackgroundResource(R.drawable.selectable_item_background);
    buttonNext.setText(R.string.next);

    switch (state) {

      case STATE_INTRO:
        finish();
        break;

      case STATE_SELECT_SERVICE_PROVIDER:
        updateFragmentUsingState(STATE_INTRO);
        break;

      case STATE_TEST_SERVICE_PROVIDER:
        updateFragmentUsingState(STATE_SELECT_SERVICE_PROVIDER);
        break;

      case STATE_CONFIGURE_SERVICE_PROVIDER:
        if (serviceProvider.isPresent() && serviceProvider.get().equals(SERVICE_PROVIDER_OTHER))
          updateFragmentUsingState(STATE_TEST_SERVICE_PROVIDER);
        else
          updateFragmentUsingState(STATE_SELECT_SERVICE_PROVIDER);
        break;

      case STATE_IMPORT_CONTACTS:
        updateFragmentUsingState(STATE_IMPORT_CONTACTS);
        break;

      case STATE_IMPORT_CALENDARS:
        updateFragmentUsingState(STATE_IMPORT_CONTACTS);
        break;

      case STATE_SELECT_REMOTE_ADDRESSBOOK:
        updateFragmentUsingState(STATE_IMPORT_CALENDARS);
        break;

      case STATE_SELECT_REMOTE_CALENDARS:
        updateFragmentUsingState(STATE_SELECT_REMOTE_ADDRESSBOOK);
        break;

    }
  }

}
