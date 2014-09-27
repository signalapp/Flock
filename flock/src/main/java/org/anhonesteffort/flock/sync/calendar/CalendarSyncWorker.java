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

package org.anhonesteffort.flock.sync.calendar;

import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.os.RemoteException;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ConstraintViolationException;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.util.Calendars;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.sync.AbstractDavSyncWorker;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Programmer: rhodey
 */
public class CalendarSyncWorker extends AbstractDavSyncWorker<Calendar> {

  private static final String TAG = "org.anhonesteffort.flock.sync.calendar.CalendarSyncWorker";

  protected CalendarSyncWorker(Context                context,
                               SyncResult             result,
                               LocalEventCollection   localCollection,
                               HidingCalDavCollection remoteCollection)
  {
    super(context, result, localCollection, remoteCollection);

    Thread.currentThread().setContextClassLoader(context.getClassLoader());
  }

  private LocalEventCollection getLocalCollection() {
    return (LocalEventCollection) localCollection;
  }

  private HidingCalDavCollection getRemoteHidingCollection() {
    return (HidingCalDavCollection) remoteCollection;
  }

  @Override
  protected Namespace getNamespace() {
    return CalDavConstants.CALDAV_NAMESPACE;
  }

  @Override
  protected Optional<String> getComponentUid(Calendar component) {
    try {

      Uid uid = Calendars.getUid(component);
      if (uid != null)
        return Optional.of(uid.getValue());

      return Optional.absent();

    } catch (ConstraintViolationException e) {
      return Optional.absent();
    }
  }

  @Override
  protected void prePushLocallyCreatedComponent(Calendar component) {
    VEvent vEvent = (VEvent) component.getComponent(VEvent.VEVENT);

    if (vEvent != null) {
      Property copyIdProp = vEvent.getProperty(EventFactory.PROPERTY_NAME_FLOCK_COPY_EVENT_ID);
      if (copyIdProp != null)
        vEvent.getProperties().remove(copyIdProp);
    }
  }

  @Override
  protected void pushLocallyCreatedProperties(SyncResult result) {
    super.pushLocallyCreatedProperties(result);
    handleLogMessage("pushLocallyCreatedProperties()");

    try {

      Optional<Integer> localColor = getLocalCollection().getColor();
      if (localColor.isPresent()) {
        Optional<Integer> remoteColor = getRemoteHidingCollection().getHiddenColor();

        if (!remoteColor.isPresent()) {
          handleLogMessage("remote hidden color not present, setting using local");
          getRemoteHidingCollection().setHiddenColor(localColor.get());
          result.stats.numInserts++;
        }
      }

    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (PropertyParseException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (DavException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (InvalidMacException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (GeneralSecurityException e){
      AbstractDavSyncAdapter.handleException(context, e, result);
    }

    try {

      Optional<Calendar> localTimeZone = getLocalCollection().getTimeZone();
      if (localTimeZone.isPresent()) {
        Optional<Calendar> remoteTimeZone = getRemoteHidingCollection().getTimeZone();

        if (!remoteTimeZone.isPresent()) {
          handleLogMessage("remote time zone not present, setting using local");
          getRemoteHidingCollection().setTimeZone(localTimeZone.get());
          result.stats.numInserts++;
        }
      }

    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (PropertyParseException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (DavException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  @Override
  protected void pushLocallyChangedProperties(SyncResult result) {
    super.pushLocallyChangedProperties(result);
    handleLogMessage("pushLocallyChangedProperties()");

    if (localCTag.isPresent() && remoteCTag.isPresent() && localCTag.get().equals(remoteCTag.get())) {
      try {

        Optional<Integer> localColor = getLocalCollection().getColor();
        if (localColor.isPresent()) {
          Optional<Integer> remoteColor = getRemoteHidingCollection().getHiddenColor();
          if (remoteColor.isPresent() && !localColor.get().equals(remoteColor.get())) {
            handleLogMessage("remote hidden color present, updating using local");
            getRemoteHidingCollection().setHiddenColor(localColor.get());
            result.stats.numUpdates++;
          }
        }

      } catch (IOException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (RemoteException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (PropertyParseException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (DavException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (InvalidMacException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (GeneralSecurityException e){
        AbstractDavSyncAdapter.handleException(context, e, result);
      }

      try {

        Optional<Calendar> localTimeZone = getLocalCollection().getTimeZone();
        if (localTimeZone.isPresent()) {
          Optional<Calendar> remoteTimeZone = getRemoteHidingCollection().getTimeZone();
          if (remoteTimeZone.isPresent() && !localTimeZone.get().equals(remoteTimeZone.get())) {
            handleLogMessage("remote time zone present, updating using local");
            getRemoteHidingCollection().setTimeZone(localTimeZone.get());
            result.stats.numUpdates++;
          }
        }

      } catch (IOException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (RemoteException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (PropertyParseException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (DavException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      }
    }
  }

  @Override
  protected void pullRemotelyCreatedProperties(SyncResult result) {
    super.pullRemotelyCreatedProperties(result);
    handleLogMessage("pullRemotelyCreatedProperties()");

    try {

      Optional<Integer> remoteColor = getRemoteHidingCollection().getHiddenColor();
      if (remoteColor.isPresent()) {
        Optional<Integer> localColor = getLocalCollection().getColor();

        if (!localColor.isPresent()) {
          handleLogMessage("local color not present, setting using remote");
          getLocalCollection().setColor(remoteColor.get());
          localCollection.commitPendingOperations();
          result.stats.numInserts++;
        }
      }

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (PropertyParseException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (InvalidMacException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (GeneralSecurityException e){
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }

    try {

      Optional<Calendar> remoteTimeZone = getRemoteHidingCollection().getTimeZone();
      if (remoteTimeZone.isPresent()) {
        Optional<Calendar> localTimeZone = getLocalCollection().getTimeZone();

        if (!localTimeZone.isPresent()) {
          handleLogMessage("local time zone not present, setting using remote");
          getLocalCollection().setTimeZone(remoteTimeZone.get());
          localCollection.commitPendingOperations();
          result.stats.numInserts++;
        }
      }

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (PropertyParseException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  @Override
  protected void pullRemotelyChangedProperties(SyncResult result) {
    super.pullRemotelyChangedProperties(result);
    handleLogMessage("pullRemotelyChangedProperties()");

    if (localCTag.isPresent() && remoteCTag.isPresent() && !localCTag.get().equals(remoteCTag.get())) {
      try {

        Optional<Integer> remoteColor = getRemoteHidingCollection().getHiddenColor();
        if (remoteColor.isPresent()) {
          Optional<Integer> localColor = getLocalCollection().getColor();
          if (localColor.isPresent() && !localColor.get().equals(remoteColor.get())) {
            handleLogMessage("local color present, updating using remote");
            getLocalCollection().setColor(remoteColor.get());
            localCollection.commitPendingOperations();
            result.stats.numUpdates++;
          }
        }

      } catch (RemoteException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (OperationApplicationException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (PropertyParseException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (InvalidMacException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (GeneralSecurityException e){
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (IOException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      }

      try {

        Optional<Calendar> remoteTimeZone = getRemoteHidingCollection().getTimeZone();
        if (remoteTimeZone.isPresent()) {
          Optional<Calendar> localTimeZone = getLocalCollection().getTimeZone();
          if (localTimeZone.isPresent() && !localTimeZone.get().equals(remoteTimeZone.get())) {
            handleLogMessage("local time zone present, updating using remote");
            getLocalCollection().setTimeZone(remoteTimeZone.get());
            localCollection.commitPendingOperations();
            result.stats.numUpdates++;
          }
        }

      } catch (RemoteException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (OperationApplicationException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (PropertyParseException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      }
    }
  }
}
