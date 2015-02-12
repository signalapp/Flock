/*
 * Copyright (C) 2015 Open Whisper Systems
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

package org.anhonesteffort.flock;

import android.app.Application;
import android.util.Log;

import org.whispersystems.libsupplychain.SupplyChain;
import org.whispersystems.libsupplychain.SupplyChainBuilder;

import java.io.InputStream;
import java.security.KeyStore;

/**
 * rhodey
 */
public class ApplicationContext extends Application {

  @Override
  public void onCreate() {
    super.onCreate();

    String installer = getPackageManager().getInstallerPackageName(getPackageName());
    if (installer != null && installer.equals("com.android.vending"))
      return;

    Log.d(getClass().getName(), "app came from outside the play store (" + installer + "), starting SupplyChain.");

    final String packageName = getPackageName();
    final String hostName    = "flock-supplychain.anhonesteffort.org";

    try {

      final InputStream keyStoreInputStream = getAssets().open("flock.store");
      final KeyStore    trustStore          = KeyStore.getInstance("BKS");

      trustStore.load(keyStoreInputStream, "owsflock".toCharArray());

      SupplyChain supplyChain =
          SupplyChainBuilder.newBuilder(this, packageName, hostName)
                            .withTrustStore(trustStore)
                            .withUpdateNotificationDrawable(R.drawable.flock_actionbar_icon)
                            .withUpdateNotificationColor(getResources().getColor(R.color.flocktheme_color))
                            .withUpdateNotificationTitle(getString(R.string.update_available))
                            .withUpdateNotificationText(getString(R.string.touch_to_install_update))
                            .create();

      supplyChain.start();

    } catch (Exception e) {
      Log.e(ApplicationContext.class.getName(), "why D:", e);
    }
  }

}
