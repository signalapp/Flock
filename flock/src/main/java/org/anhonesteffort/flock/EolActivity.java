/*
 * *
 *  Copyright (C) 2015 Open Whisper Systems
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
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

/**
 * rhodey
 */
public class EolActivity extends Activity {

  public static final String EXTRA_BACK_DISABLED = "EolActivity.EXTRA_BACK_DISABLED";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.eol_activity);
    getActionBar().setDisplayHomeAsUpEnabled(false);
    getActionBar().setTitle(R.string.shutting_down);

    initButtons();
  }

  private void initButtons() {
    findViewById(R.id.button_export).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        startService(new Intent(getBaseContext(), ExportService.class));
        Toast.makeText(getBaseContext(), R.string.export_started, Toast.LENGTH_SHORT).show();
        finish();
      }
    });
  }

  @Override
  public void onBackPressed() {
    if (!getIntent().getBooleanExtra(EXTRA_BACK_DISABLED, false))
      super.onBackPressed();
  }

}
