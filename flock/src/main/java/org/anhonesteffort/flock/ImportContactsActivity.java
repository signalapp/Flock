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
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.MenuItem;
import android.view.Window;

/**
 * Programmer: rhodey
 */
public class ImportContactsActivity extends FragmentActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.activity_with_action_button);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_import_contacts);

    FragmentTransaction fragmentTransaction    = getSupportFragmentManager().beginTransaction();
    Fragment            importContactsFragment = new ImportContactsFragment();

    importContactsFragment.setHasOptionsMenu(false);
    fragmentTransaction.replace(R.id.fragment_view, importContactsFragment);
    fragmentTransaction.commit();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        break;
    }

    return false;
  }

}
