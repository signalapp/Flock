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
import android.view.View;
import android.widget.CompoundButton;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.sync.calendar.LocalCalendarStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class RemoteCalendarListAdapter extends AbstractDavCollectionArrayAdapter<HidingCalDavCollection> {

  private View.OnClickListener colorClickListener;

  public RemoteCalendarListAdapter(Context                  context,
                                   boolean                  hasSyncOption,
                                   HidingCalDavCollection[] remoteCalendars,
                                   LocalCalendarStore       localStore,
                                   List<String>             selectedCalendars,
                                   View.OnClickListener     colorClickListener)
  {
    super(context, hasSyncOption, R.layout.row_remote_calendar_details, remoteCalendars, localStore, selectedCalendars);
    this.colorClickListener = colorClickListener;
  }

  protected class CalendarViewHolder extends ViewHolder {

    public CalendarViewHolder(ViewHolder viewHolder) {
      super(viewHolder);
    }

    public View colorView;
  }

  @Override
  protected ViewHolder getViewHolder(View collectionRowView) {
    CalendarViewHolder viewHolder = new CalendarViewHolder(super.getViewHolder(collectionRowView));
    viewHolder.colorView = collectionRowView.findViewById(R.id.calendar_color);

    return viewHolder;
  }

  @Override
  protected void handlePopulateView(int position, ViewHolder viewHolder) {
    CalendarViewHolder calendarViewHolder = (CalendarViewHolder) viewHolder;

    calendarViewHolder.colorView.setOnClickListener(colorClickListener);
    calendarViewHolder.colorView.setTag(R.integer.tag_collection_path, remoteCollections[position].getPath());

    try {

      Optional<Integer> color = remoteCollections[position].getHiddenColor();

      if (color.isPresent()) {
        calendarViewHolder.colorView.setBackgroundColor(color.get());
        calendarViewHolder.colorView.setTag(R.integer.tag_calendar_color,  color.get());
      }
      else {
        calendarViewHolder.colorView.setBackgroundColor(getContext().getResources().getColor(R.color.flocktheme_color));
        calendarViewHolder.colorView.setTag(R.integer.tag_calendar_color, getContext().getResources().getColor(R.color.flocktheme_color));
      }

    } catch (PropertyParseException e) {
      ErrorToaster.handleShowError(getContext(), e);
    } catch (InvalidMacException e) {
      ErrorToaster.handleShowError(getContext(), e);
    } catch (GeneralSecurityException e) {
      ErrorToaster.handleShowError(getContext(), e);
    } catch (IOException e) {
      ErrorToaster.handleShowError(getContext(), e);
    }
  }

  @Override
  protected CompoundButton.OnCheckedChangeListener getOnCheckChangedListener(HidingCalDavCollection remoteCollection) {
    return new SyncChangeListener(remoteCollection);
  }

  private class SyncChangeListener implements CompoundButton.OnCheckedChangeListener {

    private HidingCalDavCollection remoteCollection;
    private LocalCalendarStore     localCalendarStore;

    public SyncChangeListener(HidingCalDavCollection remoteCollection) {
      this.remoteCollection   = remoteCollection;
      this.localCalendarStore = (LocalCalendarStore) localStore;
    }

    @Override
    public void onCheckedChanged(CompoundButton checkBoxView, boolean isChecked) {
      if (!checkBoxView.isShown())
        return;

      try {

        if (isChecked) {
          Optional<String>  displayName = remoteCollection.getHiddenDisplayName();
          Optional<Integer> color       = remoteCollection.getHiddenColor();

          if (displayName.isPresent() && color.isPresent()) {
            localCalendarStore.addCollection(remoteCollection.getPath(),
                                             displayName.get(),
                                             color.get());
          }
          else if (displayName.isPresent()) {
            localCalendarStore.addCollection(remoteCollection.getPath(),
                                             displayName.get(),
                                             getContext().getResources().getColor(R.color.flocktheme_color));
          }
          else {
            localStore.addCollection(remoteCollection.getPath(),
                                     getContext().getString(R.string.display_name_missing));
          }
        }
        else
          localStore.removeCollection(remoteCollection.getPath());

        new CalendarsSyncScheduler(getContext()).requestSync();

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
