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
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import org.anhonesteffort.flock.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public abstract class AbstractMyCollectionsFragment extends AccountAndKeyRequiredFragment
    implements ActionMode.Callback, ListView.OnItemClickListener, ListView.OnItemLongClickListener
{

  private static final String TAG = "org.anhonesteffort.flock.AbstractMyCollectionsFragment";

  protected Activity    activity;
  protected AsyncTask   asyncTask;
  protected ListView    collectionsListView;
  private   Menu        optionsMenu;
  protected ActionMode  actionMode;
  protected AlertDialog alertDialog;

  protected Optional<SetupActivity> setupActivity = Optional.absent();
  protected List<String>            batchSelections;

  protected boolean list_is_initializing = false;
  protected boolean progress_is_shown    = false;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof SetupActivity)
      this.setupActivity = Optional.of((SetupActivity) activity);
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
         activity     = getActivity();
    View fragmentView = inflater.inflate(R.layout.fragment_list_sync_collections, container, false);

    if (accountAndKeyAvailable())
      initButtons();

    return fragmentView;
  }

  protected abstract void handleButtonNext();

  private void initButtons() {
    if (activity.findViewById(R.id.button_next) == null)
      return;

    activity.findViewById(R.id.button_next).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View view) {
        if (setupActivity.isPresent())
          handleButtonNext();
      }

    });
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!accountAndKeyAvailable())
      return;

    activity = getActivity();
    initializeList();
  }

  @Override
  public void onPause() {
    super.onPause();

    if (alertDialog != null)
      alertDialog.dismiss();

    if (asyncTask != null && !asyncTask.isCancelled()) {
      asyncTask.cancel(true);
      asyncTask = null;
    }
  }

  protected void initializeList() {
    Log.d(TAG, "initializeList()");

    if (list_is_initializing || account == null)
      return;

    batchSelections = new LinkedList<String>();
    updateActionMode();

    list_is_initializing = true;
    retrieveRemoteCollectionsAsync();
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    actionMode = null;
    batchSelections.clear();

    for(int i = 0; i < collectionsListView.getChildCount(); i++)
      handleUnselectRow(collectionsListView.getChildAt(i));
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    if (!view.isShown() || progress_is_shown)
      return;

    if (batchSelections.size() == 0) {
      CompoundButton syncCheckbox = (CompoundButton) view.findViewById(R.id.collection_sync_button);
      syncCheckbox.setChecked(!syncCheckbox.isChecked());
    }
    else {
      if (view.getTag(R.integer.tag_collection_selected) == Boolean.TRUE)
        handleUnselectRow(view);
      else
        handleSelectRow(view);
    }
  }

  @Override
  public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
    if (!view.isShown() || progress_is_shown)
      return true;

    if (batchSelections.size() == 0)
      handleSelectRow(view);

    return true;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    optionsMenu = menu;
  }

  protected abstract void handleHideOptionsMenuItems(Menu menu);

  protected abstract void handleRestoreOptionsMenuItems(Menu menu);

  protected void handleStartIndeterminateProgress() {
    if (optionsMenu != null)
      handleHideOptionsMenuItems(optionsMenu);

    activity.setProgressBarIndeterminateVisibility(true);
    activity.setProgressBarVisibility(true);
    progress_is_shown = true;
  }

  protected void handleStopIndeterminateProgress() {
    activity.setProgressBarIndeterminateVisibility(false);
    activity.setProgressBarVisibility(false);
    progress_is_shown = false;

    if (optionsMenu != null)
      handleRestoreOptionsMenuItems(optionsMenu);
  }

  protected abstract String getStringCollectionsSelected();

  protected void updateActionMode() {
    if (actionMode != null) {
      if (batchSelections.size() == 0)
        actionMode.finish();

      else {
        actionMode.getMenu().clear();
        actionMode.setSubtitle(batchSelections.size() + " " + getStringCollectionsSelected());

        if (batchSelections.size() == 1)
          actionMode.getMenuInflater().inflate(R.menu.collection_list_edit, actionMode.getMenu());
        else
          actionMode.getMenuInflater().inflate(R.menu.collection_list_delete, actionMode.getMenu());
      }
    }
    else if (batchSelections.size() > 0)
      actionMode = activity.startActionMode(this);
  }

  protected void handleSelectRow(View view) {
    batchSelections.add((String) view.getTag(R.integer.tag_collection_path));
    view.setTag(R.integer.tag_collection_selected, Boolean.TRUE);
    view.setBackgroundResource(R.color.holo_blue_dark);

    updateActionMode();
  }

  protected void handleUnselectRow(View view) {
    batchSelections.remove((String) view.getTag(R.integer.tag_collection_path));
    view.setTag(R.integer.tag_collection_selected, Boolean.FALSE);
    view.setBackgroundResource(0);

    updateActionMode();
  }

  protected Optional<String> getDisplayNameForSelectedCollection() {
    if (batchSelections.size() == 0)
      return Optional.absent();

    for(int i = 0; i < collectionsListView.getChildCount(); i++) {
      View     rowView         = collectionsListView.getChildAt(i);
      TextView displayNameView = (TextView) rowView.findViewById(R.id.collection_display_name);
      Boolean  selected        = (Boolean ) rowView.getTag(R.integer.tag_collection_selected);

      if (selected && displayNameView.getText() != null)
        return Optional.of(displayNameView.getText().toString());
    }

    return Optional.absent();
  }

  protected boolean getIsFlockCollectionForSelectedCollection() {
    if (batchSelections.size() == 0)
      return false;

    for(int i = 0; i < collectionsListView.getChildCount(); i++) {
      View    rowView  = collectionsListView.getChildAt(i);
      Boolean selected = (Boolean ) rowView.getTag(R.integer.tag_collection_selected);

      if (selected)
        return (Boolean) collectionsListView.getChildAt(i).getTag(R.integer.tag_is_flock_collection);
    }

    return false;
  }

  protected abstract void retrieveRemoteCollectionsAsync();

}
