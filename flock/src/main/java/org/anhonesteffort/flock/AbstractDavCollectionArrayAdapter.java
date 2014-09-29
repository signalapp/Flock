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

import android.content.Context;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.HidingDavCollection;
import org.anhonesteffort.flock.sync.LocalComponentStore;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.webdav.PropertyParseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Programmer: rhodey
 */
public abstract class AbstractDavCollectionArrayAdapter<T extends HidingDavCollection>
    extends ArrayAdapter<T>
{
  protected LayoutInflater      inflater;
  protected boolean             hasSyncOption;
  protected int                 rowLayout;
  protected T[]                 remoteCollections;
  protected LocalComponentStore localStore;
  protected List<String>        batchSelections;

  public AbstractDavCollectionArrayAdapter(Context             context,
                                           boolean             hasSyncOption,
                                           int                 rowLayout,
                                           T[]                 remoteCollections,
                                           LocalComponentStore localStore,
                                           List<String>        batchSelections)
  {
    super(context, rowLayout, remoteCollections);

    this.hasSyncOption     = hasSyncOption;
    this.rowLayout         = rowLayout;
    this.remoteCollections = remoteCollections;
    this.localStore        = localStore;
    this.batchSelections   = batchSelections;

    inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  protected abstract void handlePopulateView(int position, ViewHolder viewHolder);

  protected class ViewHolder {

    public ViewHolder() { }

    public ViewHolder(ViewHolder viewHolder) {
      this.displayName = viewHolder.displayName;
      this.syncCheck   = viewHolder.syncCheck;
    }

    public TextView       displayName;
    public CompoundButton syncCheck;
  }

  protected ViewHolder getViewHolder(View collectionRowView) {
    ViewHolder viewHolder  = new ViewHolder();
    viewHolder.displayName = (TextView) collectionRowView.findViewById(R.id.collection_display_name);
    viewHolder.syncCheck   = (CompoundButton) collectionRowView.findViewById(R.id.collection_sync_button);

    return viewHolder;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View collectionRowView = convertView;

    if (convertView == null) {
      collectionRowView = inflater.inflate(rowLayout, parent, false);
      collectionRowView.setTag(R.integer.tag_view_holder, getViewHolder(collectionRowView));
    }

    ViewHolder viewHolder = (ViewHolder) collectionRowView.getTag(R.integer.tag_view_holder);

    if (hasSyncOption)
      viewHolder.syncCheck.setOnCheckedChangeListener(getOnCheckChangedListener(remoteCollections[position]));
    else
      viewHolder.syncCheck.setVisibility(View.GONE);

    collectionRowView.setTag(R.integer.tag_collection_path, remoteCollections[position].getPath());

    if (batchSelections.contains(remoteCollections[position].getPath())) {
      collectionRowView.setTag(R.integer.tag_collection_selected, Boolean.TRUE);
      collectionRowView.setBackgroundResource(R.color.holo_blue_dark);
    }
    else {
      collectionRowView.setTag(R.integer.tag_collection_selected, Boolean.FALSE);
      collectionRowView.setBackgroundResource(0);
    }

    viewHolder.displayName.setText(R.string.display_name_missing);

    try {

      if (remoteCollections[position].isFlockCollection())
        collectionRowView.setTag(R.integer.tag_is_flock_collection, Boolean.TRUE);
      else
        collectionRowView.setTag(R.integer.tag_is_flock_collection, Boolean.FALSE);

      Optional<String> displayName = remoteCollections[position].getHiddenDisplayName();
      if (displayName.isPresent())
        viewHolder.displayName.setText(displayName.get());

      if (hasSyncOption) {
        if (localStore.getCollection(remoteCollections[position].getPath()).isPresent())
          viewHolder.syncCheck.setChecked(true);
        else
          viewHolder.syncCheck.setChecked(false);
      }

    } catch (PropertyParseException e) {
      ErrorToaster.handleShowError(getContext(), e);
    } catch (RemoteException e) {
      ErrorToaster.handleShowError(getContext(), e);
    } catch (InvalidMacException e) {
      ErrorToaster.handleShowError(getContext(), e);
    } catch (GeneralSecurityException e) {
      ErrorToaster.handleShowError(getContext(), e);
    } catch (IOException e) {
      ErrorToaster.handleShowError(getContext(), e);
    }

    handlePopulateView(position, viewHolder);

    return collectionRowView;
  }

  protected CompoundButton.OnCheckedChangeListener getOnCheckChangedListener(T remoteCollection) {
    return new SyncChangeListener(remoteCollection);
  }

  private class SyncChangeListener implements CompoundButton.OnCheckedChangeListener {

    protected T remoteCollection;

    public SyncChangeListener(T remoteCollection) {
      this.remoteCollection = remoteCollection;
    }

    @Override
    public void onCheckedChanged(CompoundButton checkBoxView, boolean isChecked) {
      if (!checkBoxView.isShown())
        return;

      try {

        if (isChecked) {
          Optional<String> displayName = remoteCollection.getHiddenDisplayName();
          if (displayName.isPresent())
            localStore.addCollection(remoteCollection.getPath(), displayName.get());
          else {
            localStore.addCollection(remoteCollection.getPath(),
                getContext().getString(R.string.display_name_missing));
          }
        }
        else
          localStore.removeCollection(remoteCollection.getPath());

        new AddressbookSyncScheduler(getContext()).requestSync();

      } catch (PropertyParseException e) {
        ErrorToaster.handleShowError(getContext(), e);
      } catch (InvalidMacException e) {
        ErrorToaster.handleShowError(getContext(), e);
      } catch (GeneralSecurityException e) {
        ErrorToaster.handleShowError(getContext(), e);
      } catch (RemoteException e) {
        ErrorToaster.handleShowError(getContext(), e);
      } catch (IOException e) {
        ErrorToaster.handleShowError(getContext(), e);
      }
    }
  }
}
