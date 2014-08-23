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

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.widget.Toast;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.MasterCipher;

/**
 * Programmer: rhodey
 */
public class AccountAndKeyRequiredFragment extends Fragment {

  protected DavAccount   account;
  protected MasterCipher masterCipher;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    account      = AccountAndKeyRequiredActivity.handleGetAccountOrFail(getActivity());
    masterCipher = AccountAndKeyRequiredActivity.handleGetMasterCipherOrFail(getActivity());
  }

  protected boolean accountAndKeyAvailableAndMigrationComplete() {
    if (MigrationHelperBroadcastReceiver.getUiDisabledForMigration(getActivity())) {
      Toast.makeText(getActivity(),
                     R.string.migration_in_progress_please_wait,
                     Toast.LENGTH_LONG).show();

      getActivity().finish();
      return false;
    }

    return account != null && masterCipher != null;
  }

}
