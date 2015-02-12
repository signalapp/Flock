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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import com.android.vending.billing.IInAppBillingService;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.model.SubscriptionPlan;
import org.anhonesteffort.flock.sync.account.AccountStore;

/**
 * Programmer: rhodey
 */
public class ManageSubscriptionActivity extends FragmentActivity {

  private static final String TAG = "org.anhonesteffort.flock.ManageSubscriptionActivity";

  public static final String KEY_DAV_ACCOUNT_BUNDLE = "KEY_DAV_ACCOUNT_BUNDLE";
  public static final String KEY_CURRENT_FRAGMENT   = "KEY_CURRENT_FRAGMENT";
  public static final String KEY_REQUEST_CODE       = "KEY_REQUEST_CODE";
  public static final String KEY_RESULT_CODE        = "KEY_RESULT_CODE";
  public static final String KEY_RESULT_DATA        = "KEY_RESULT_DATA";

  protected IInAppBillingService billingService;
  protected DavAccount           davAccount;
  protected Menu                 optionsMenu;

  private   int               currentFragment     = -1;
  protected Optional<Integer> activityRequestCode = Optional.absent();
  protected Optional<Integer> activityResultCode  = Optional.absent();
  protected Optional<Intent>  activityResultData  = Optional.absent();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.simple_fragment_activity);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_manage_subscription);

    if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
      if (!DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        Log.e(TAG, "where did my dav account bundle go?! :(");
        finish();
        return;
      }

      davAccount          = DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
      currentFragment     = savedInstanceState.getInt(KEY_CURRENT_FRAGMENT, -1);
      activityRequestCode = Optional.fromNullable(savedInstanceState.getInt(KEY_REQUEST_CODE));
      activityResultCode  = Optional.fromNullable(savedInstanceState.getInt(KEY_RESULT_CODE));
      activityResultData  = Optional.fromNullable((Intent) savedInstanceState.getParcelable(KEY_RESULT_DATA));
    }
    else if (getIntent().getExtras() != null) {
      if (!DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        Log.e(TAG, "where did my dav account bundle go?! :(");
        finish();
        return;
      }

      davAccount          = DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
      currentFragment     = getIntent().getExtras().getInt(KEY_CURRENT_FRAGMENT, -1);
      activityRequestCode = Optional.fromNullable(getIntent().getExtras().getInt(KEY_REQUEST_CODE));
      activityResultCode  = Optional.fromNullable(getIntent().getExtras().getInt(KEY_RESULT_CODE));
      activityResultData  = Optional.fromNullable((Intent) getIntent().getExtras().getParcelable(KEY_RESULT_DATA));
    }

    Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
    serviceIntent.setPackage("com.android.vending");
    bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.manage_subscription, menu);

    if (currentFragment == SubscriptionPlan.PLAN_TYPE_NONE)
      menu.findItem(R.id.button_send_bitcoin).setVisible(false);

    optionsMenu = menu;
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {

      case android.R.id.home:
        finish();
        break;

      case R.id.button_send_bitcoin:
        Intent nextIntent = new Intent(getBaseContext(), SendBitcoinActivity.class);
        nextIntent.putExtra(ManageSubscriptionActivity.KEY_DAV_ACCOUNT_BUNDLE, davAccount.toBundle());
        startActivity(nextIntent);
        break;

    }
    return false;
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putBundle(KEY_DAV_ACCOUNT_BUNDLE, davAccount.toBundle());
    savedInstanceState.putInt(KEY_CURRENT_FRAGMENT, currentFragment);

    if (activityRequestCode.isPresent())
      savedInstanceState.putInt(KEY_REQUEST_CODE, activityRequestCode.get());

    if (activityResultCode.isPresent())
      savedInstanceState.putInt(KEY_RESULT_CODE, activityResultCode.get());

    if (activityResultData.isPresent())
      savedInstanceState.putParcelable(KEY_RESULT_DATA, activityResultData.get());

    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
      if (!DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        Log.e(TAG, "where did my dav account bundle go?! :(");
        finish();
        return;
      }

      davAccount          = DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
      currentFragment     = savedInstanceState.getInt(KEY_CURRENT_FRAGMENT, -1);
      activityRequestCode = Optional.fromNullable(savedInstanceState.getInt(KEY_REQUEST_CODE));
      activityResultCode  = Optional.fromNullable(savedInstanceState.getInt(KEY_RESULT_CODE));
      activityResultData  = Optional.fromNullable((Intent) savedInstanceState.getParcelable(KEY_RESULT_DATA));
    }

    super.onRestoreInstanceState(savedInstanceState);
  }

  protected void updateFragmentWithPlanType(int planType) {
    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    Fragment            nextFragment        = null;

    switch (planType) {
      case SubscriptionPlan.PLAN_TYPE_GOOGLE:
        nextFragment = new SubscriptionGoogleFragment();

        break;

      case SubscriptionPlan.PLAN_TYPE_STRIPE:
        nextFragment = new SubscriptionStripeFragment();
        break;

      default:
        nextFragment = new UnsubscribedFragment();
        if (optionsMenu != null)
          optionsMenu.findItem(R.id.button_send_bitcoin).setVisible(false);
        break;
    }

    fragmentTransaction.replace(R.id.fragment_view, nextFragment);
    fragmentTransaction.commit();

    currentFragment = planType;
  }

  @Override
  public void onResume() {
    super.onResume();

    if (currentFragment >= 0)
      updateFragmentWithPlanType(currentFragment);
    else
      updateFragmentWithPlanType(AccountStore.getSubscriptionPlanType(getBaseContext()));
  }

  @Override
  public void onBackPressed() {
    int activePlanType = AccountStore.getSubscriptionPlanType(getBaseContext());

    switch (currentFragment) {
      case SubscriptionPlan.PLAN_TYPE_GOOGLE:
        if (activePlanType == SubscriptionPlan.PLAN_TYPE_GOOGLE)
          super.onBackPressed();
        else
          updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_NONE);
        break;

      case SubscriptionPlan.PLAN_TYPE_STRIPE:
        if (activePlanType == SubscriptionPlan.PLAN_TYPE_STRIPE)
          super.onBackPressed();
        else
          updateFragmentWithPlanType(SubscriptionPlan.PLAN_TYPE_NONE);
        break;

      default:
        super.onBackPressed();
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Log.d(TAG, "ON ACTIVITY RESULT");

    activityRequestCode = Optional.of(requestCode);
    activityResultCode  = Optional.of(resultCode);
    activityResultData  = Optional.of(data);
  }

  protected void handleClearActivityResult() {
    activityRequestCode = Optional.absent();
    activityResultCode  = Optional.absent();
    activityResultData  = Optional.absent();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    if (serviceConnection != null)
      unbindService(serviceConnection);
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {

    @Override
    public void onServiceDisconnected(ComponentName name) {
      billingService = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      billingService = IInAppBillingService.Stub.asInterface(service);
    }

  };
}
