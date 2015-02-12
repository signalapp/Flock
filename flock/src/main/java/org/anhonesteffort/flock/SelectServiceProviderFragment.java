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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

/**
 * Programmer: rhodey
 */
public class SelectServiceProviderFragment extends Fragment {

  private AlertDialog   alertDialog;
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
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    View view = inflater.inflate(R.layout.fragment_select_sync_provider, container, false);
    initButtons();
    initRadioButtons(view);
    initCostPerYear(view);

    return view;
  }

  @Override
  public void onPause() {
    super.onPause();

    if (alertDialog != null)
      alertDialog.dismiss();
  }

  private void handlePromptNewOrExistingAccount() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

    builder.setTitle(R.string.title_setup_account);
    builder.setMessage(R.string.do_you_have_a_flock_account);
    builder.setPositiveButton(R.string.yes_log_me_in, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int id) {
        fragmentActivity.setIsNewAccount(false);
        fragmentActivity.setServiceProvider(SetupActivity.SERVICE_PROVIDER_OWS);
        fragmentActivity.updateFragmentUsingState(SetupActivity.STATE_CONFIGURE_SERVICE_PROVIDER);
      }

    });
    builder.setNegativeButton(R.string.no_register_me, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        fragmentActivity.setIsNewAccount(true);
        fragmentActivity.setServiceProvider(SetupActivity.SERVICE_PROVIDER_OWS);
        fragmentActivity.updateFragmentUsingState(SetupActivity.STATE_CONFIGURE_SERVICE_PROVIDER);
      }

    });

    alertDialog = builder.show();
  }

  private void initButtons() {
    getActivity().findViewById(R.id.button_next).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        RadioButton radioButtonOws = (RadioButton) getActivity().findViewById(R.id.radio_button_service_ows);

        if (radioButtonOws.isChecked())
          handlePromptNewOrExistingAccount();

        else {
          fragmentActivity.setServiceProvider(SetupActivity.SERVICE_PROVIDER_OTHER);
          fragmentActivity.updateFragmentUsingState(SetupActivity.STATE_TEST_SERVICE_PROVIDER);
        }
      }

    });
  }

  private void initRadioButtons(View fragmentView) {
    final LinearLayout rowSelectOws       = (LinearLayout) fragmentView.findViewById(R.id.row_service_ows);
    final LinearLayout rowSelectOther     = (LinearLayout) fragmentView.findViewById(R.id.row_service_other);
    final RadioButton  radioButtonOws     = (RadioButton)  fragmentView.findViewById(R.id.radio_button_service_ows);
    final RadioButton  radioButtonOther   = (RadioButton)  fragmentView.findViewById(R.id.radio_button_service_other);
    final TextView     serviceDescription = (TextView)     fragmentView.findViewById(R.id.sync_service_description);
    final Double       costPerYearUsd     = (double) getResources().getInteger(R.integer.cost_per_year_usd);

    rowSelectOws.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View view) {
        if (!radioButtonOws.isChecked()) {
          radioButtonOws.setChecked(true);
          radioButtonOther.setChecked(false);
          String descriptionText =  getString(R.string.flock_sync_is_a_service_run_by_open_whisper_systems_available, costPerYearUsd);
          descriptionText        += "<br/><br/>" + getString(R.string.privacy_and_terms_of_service);
          serviceDescription.setText(Html.fromHtml(descriptionText));
          serviceDescription.setMovementMethod(LinkMovementMethod.getInstance());
        }
      }

    });

    rowSelectOther.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View view) {
        if (!radioButtonOther.isChecked()) {
          radioButtonOther.setChecked(true);
          radioButtonOws.setChecked(false);
          serviceDescription.setText(R.string.you_may_chose_to_run_and_configure_your_own_webdav_compliant_server);
        }
      }

    });

    radioButtonOws.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (isChecked) {
          radioButtonOws.setChecked(true);
          radioButtonOther.setChecked(false);
          String descriptionText =  getString(R.string.flock_sync_is_a_service_run_by_open_whisper_systems_available, costPerYearUsd);
          descriptionText        += "<br/><br/>" + getString(R.string.privacy_and_terms_of_service);
          serviceDescription.setText(Html.fromHtml(descriptionText));
          serviceDescription.setMovementMethod(LinkMovementMethod.getInstance());
        }
      }

    });

    radioButtonOther.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (isChecked) {
          radioButtonOther.setChecked(true);
          radioButtonOws.setChecked(false);
          serviceDescription.setText(R.string.you_may_chose_to_run_and_configure_your_own_webdav_compliant_server);
        }
      }

    });
  }

  private void initCostPerYear(View fragmentView) {
    final TextView serviceDescription = (TextView) fragmentView.findViewById(R.id.sync_service_description);
    final Double   costPerYearUsd     = (double)   getResources().getInteger(R.integer.cost_per_year_usd);

    String descriptionText =  getString(R.string.flock_sync_is_a_service_run_by_open_whisper_systems_available, costPerYearUsd);
    descriptionText        += "<br/><br/>" + getString(R.string.privacy_and_terms_of_service);
    serviceDescription.setText(Html.fromHtml(descriptionText));
    serviceDescription.setMovementMethod(LinkMovementMethod.getInstance());
  }
}
