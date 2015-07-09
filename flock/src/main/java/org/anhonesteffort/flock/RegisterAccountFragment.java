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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Programmer: rhodey
 */
public class RegisterAccountFragment extends Fragment {

  protected static final int CODE_ACCOUNT_IMPORTED = 9001;

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_register_ows_account, container, false);
  }

  @Override
  public void onResume() {
    super.onResume();
    getActivity().findViewById(R.id.button_next).setVisibility(View.GONE);
  }

}
