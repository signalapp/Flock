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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Programmer: rhodey
 */
public class IntroductionFragment extends Fragment {

  private SetupActivity fragmentActivity;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof SetupActivity)
      this.fragmentActivity = (SetupActivity) activity;
    else
      throw new ClassCastException(activity.toString() + " not what I expected D: !");
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container,
                           Bundle savedInstanceState)
  {
    View view = inflater.inflate(R.layout.fragment_intro, container, false);
    initButtons();

    return view;
  }

  private void initButtons() {
    getActivity().findViewById(R.id.button_next).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        fragmentActivity.updateFragmentUsingState(SetupActivity.STATE_SELECT_SERVICE_PROVIDER);
      }

    });
  }
}
