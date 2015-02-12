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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.anhonesteffort.flock.util.guava.Optional;

import java.util.List;

/**
 * Programmer: rhodey
 */
public class AccountContactDetailsListAdapter
    extends ArrayAdapter<ImportContactsFragment.AccountContactDetails>
    implements View.OnClickListener, CompoundButton.OnCheckedChangeListener
{

  private ImportContactsFragment.AccountContactDetails[]     accountDetails;
  private List<ImportContactsFragment.AccountContactDetails> selectedAccounts;
  private CompoundButton.OnCheckedChangeListener             checkListener;

  public AccountContactDetailsListAdapter(Context                                            context,
                                          ImportContactsFragment.AccountContactDetails[]     accountDetails,
                                          List<ImportContactsFragment.AccountContactDetails> selectedAccounts,
                                          CompoundButton.OnCheckedChangeListener             checkListener)
  {
    super(context, R.layout.fragment_simple_list, accountDetails);

    this.accountDetails   = accountDetails;
    this.selectedAccounts = selectedAccounts;
    this.checkListener    = checkListener;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    LayoutInflater   inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View             rowView  = inflater.inflate(R.layout.row_account_contact_details, parent, false);
    TextView accountNameView  = (TextView) rowView.findViewById(R.id.account_name);
    TextView contactCountView = (TextView) rowView.findViewById(R.id.account_contact_count);
    CheckBox importCheckbox   = (CheckBox) rowView.findViewById(R.id.import_checkbox);

    importCheckbox.setTag(R.integer.tag_account_name, accountDetails[position].account.name);
    importCheckbox.setTag(R.integer.tag_account_type, accountDetails[position].account.type);
    importCheckbox.setTag(R.integer.tag_account_contact_count, accountDetails[position].contact_count);

    accountNameView.setText(accountDetails[position].account.name);
    contactCountView.setText(accountDetails[position].contact_count + " " +
                             getContext().getString(R.string.contacts));

    Account viewAccount = new Account(accountDetails[position].account.name,
                                      accountDetails[position].account.type);

    for (ImportContactsFragment.AccountContactDetails selectedAccount : selectedAccounts) {
      if (selectedAccount.account.equals(viewAccount)) {
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
    Account  tappedAccount  = new Account(accountName, accountType);

    Optional<ImportContactsFragment.AccountContactDetails> accountDetails = Optional.absent();
    for (ImportContactsFragment.AccountContactDetails selectedAccount : selectedAccounts) {
      if (selectedAccount.account.equals(tappedAccount)) {
        accountDetails = Optional.of(selectedAccount);
        break;
      }
    }

    if (!isChecked && accountDetails.isPresent())
      selectedAccounts.remove(accountDetails.get());
    else if (isChecked && !accountDetails.isPresent()) {
      for (ImportContactsFragment.AccountContactDetails details : this.accountDetails) {
        if (details.account.equals(tappedAccount)) {
          selectedAccounts.add(details);
          break;
        }
      }
    }

    checkListener.onCheckedChanged(importCheckbox, isChecked);
  }
}
