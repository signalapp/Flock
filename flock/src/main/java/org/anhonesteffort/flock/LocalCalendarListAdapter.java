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
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.sync.calendar.LocalEventCollection;

import java.util.List;

/**
 * Programmer: rhodey
 * Date: 3/13/14
 */
public class LocalCalendarListAdapter extends ArrayAdapter<LocalEventCollection>
    implements View.OnClickListener, CompoundButton.OnCheckedChangeListener
{
  private static final String TAG = "org.anhonesteffort.flock.LocalCalendarListAdapter";

  private LocalEventCollection[]                 localCalendars;
  private List<LocalEventCollection>             selectedCalendars;
  private CompoundButton.OnCheckedChangeListener checkListener;

  public LocalCalendarListAdapter(Context                                context,
                                  LocalEventCollection[]                 localCalendars,
                                  List<LocalEventCollection>             selectedCalendars,
                                  CompoundButton.OnCheckedChangeListener checkListener
  )
  {
    super(context, R.layout.fragment_simple_list, localCalendars);

    this.localCalendars    = localCalendars;
    this.selectedCalendars = selectedCalendars;
    this.checkListener     = checkListener;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    LayoutInflater inflater        = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View           rowView         = inflater.inflate(R.layout.row_local_calendar_details, parent, false);
    View           colorView       =            rowView.findViewById(R.id.calendar_color);
    TextView       displayNameView = (TextView) rowView.findViewById(R.id.calendar_display_name);
    TextView       accountNameView = (TextView) rowView.findViewById(R.id.calendar_account_name);
    CheckBox       importCheckbox  = (CheckBox) rowView.findViewById(R.id.import_checkbox);

    importCheckbox.setTag(R.integer.tag_account_name,      localCalendars[position].getAccount().name);
    importCheckbox.setTag(R.integer.tag_account_type,      localCalendars[position].getAccount().type);
    importCheckbox.setTag(R.integer.tag_calendar_local_id, localCalendars[position].getLocalId());

    accountNameView.setText(localCalendars[position].getAccount().name);

    try {

      Optional<String>  displayName = localCalendars[position].getDisplayName();
      Optional<Integer> color       = localCalendars[position].getColor();

      if (displayName.isPresent())
        displayNameView.setText(displayName.get());
      else
        displayNameView.setText(R.string.display_name_missing);

      if (color.isPresent())
        colorView.setBackgroundColor(color.get());
      else
        colorView.setBackgroundColor(getContext().getResources().getColor(R.color.flocktheme_color));

    } catch (RemoteException e) {
      Log.e(TAG, "caught exception while trying to build calendar row view", e);
      ErrorToaster.handleShowError(getContext(), e);
    }

    Account account = new Account(localCalendars[position].getAccount().name,
                                  localCalendars[position].getAccount().type);
    for (LocalEventCollection selectedCalendar : selectedCalendars) {
      if (selectedCalendar.getAccount().equals(account) &&
          selectedCalendar.getLocalId().equals(localCalendars[position].getLocalId()))
      {
        importCheckbox.setChecked(true);
        break;
      }
    }

    rowView.setOnClickListener(this);
    importCheckbox.setOnCheckedChangeListener(this);

    return rowView;
  }

  @Override
  public void onClick(View view) {
    CheckBox importCheckbox = (CheckBox) view.findViewById(R.id.import_checkbox);
    importCheckbox.setChecked(!importCheckbox.isChecked());
  }

  @Override
  public void onCheckedChanged(CompoundButton importCheckbox, boolean isChecked) {
    String   accountName    = (String) importCheckbox.getTag(R.integer.tag_account_name);
    String   accountType    = (String) importCheckbox.getTag(R.integer.tag_account_type);
    Long     localId        = (Long)   importCheckbox.getTag(R.integer.tag_calendar_local_id);
    Account  tappedAccount  = new Account(accountName, accountType);

    Optional<LocalEventCollection> selectedCalendar = Optional.absent();
    for (LocalEventCollection calendar : selectedCalendars) {
      if (calendar.getAccount().equals(tappedAccount) && calendar.getLocalId().equals(localId)) {
        selectedCalendar = Optional.of(calendar);
        break;
      }
    }

    if (!isChecked && selectedCalendar.isPresent())
      selectedCalendars.remove(selectedCalendar.get());
    else if (isChecked && !selectedCalendar.isPresent()) {
      for (LocalEventCollection calendar : localCalendars) {
        if (calendar.getAccount().equals(tappedAccount) && calendar.getLocalId().equals(localId)) {
          selectedCalendars.add(calendar);
          break;
        }
      }
    }

    checkListener.onCheckedChanged(importCheckbox, isChecked);
  }
}
