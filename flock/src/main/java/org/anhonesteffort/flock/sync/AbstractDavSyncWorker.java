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

package org.anhonesteffort.flock.sync;

import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public abstract class AbstractDavSyncWorker<T> implements Runnable {

  private static final String TAG = "org.anhonesteffort.flock.sync.AbstractDavSyncWorker";

  protected Context                             context;
  protected SyncResult                          result;
  protected AbstractLocalComponentCollection<T> localCollection;
  protected HidingDavCollection<T>              remoteCollection;

  protected Optional<String> localCTag  = Optional.absent();
  protected Optional<String> remoteCTag = Optional.absent();

  public AbstractDavSyncWorker(Context                             context,
                               SyncResult                          result,
                               AbstractLocalComponentCollection<T> localCollection,
                               HidingDavCollection<T>              remoteCollection)
  {
    this.context          = context;
    this.result           = result;
    this.localCollection  = localCollection;
    this.remoteCollection = remoteCollection;
  }

  protected void handleMakeFlockCollection()
      throws PropertyParseException, DavException,
             RemoteException, GeneralSecurityException, IOException
  {
    if (!remoteCollection.isFlockCollection()) {
      if (!localCollection.getDisplayName().isPresent())
        remoteCollection.makeFlockCollection(" ");
      else
        remoteCollection.makeFlockCollection(localCollection.getDisplayName().get());
    }
  }

  @Override
  public void run() {
    Log.d(TAG, "now syncing local: " + localCollection.getPath() +
               " with remote: "      + remoteCollection.getPath());

    try {

      handleMakeFlockCollection();

      localCTag  = localCollection.getCTag();
      remoteCTag = remoteCollection.getCTag();

      if (localCTag.isPresent())
        Log.d(TAG, "local ctag pre push local: " + localCTag.get());
      else
        Log.d(TAG, "local ctag not present pre push local");

      if (remoteCTag.isPresent())
        Log.d(TAG, "remote ctag pre push local: " + remoteCTag.get());
      else
        Log.d(TAG, "remote ctag not present pre push local");

      pushLocallyCreatedProperties(result);
      pushLocallyChangedProperties(result);

      pushLocallyDeletedComponents(result);
      pushLocallyChangedComponents(result);
      pushLocallyCreatedComponents(result);

      boolean pull_remote = result.stats.numInserts > 0 ||
                            result.stats.numUpdates > 0 ||
                            result.stats.numDeletes > 0 ||
                            !localCTag.isPresent()      ||
                            (remoteCTag.isPresent() && !localCTag.get().equals(remoteCTag.get()));

      if (!pull_remote)
        return;

      remoteCTag = remoteCollection.getCTag();

      pullRemotelyCreatedProperties(result);
      pullRemotelyChangedProperties(result);

      pullRemotelyCreatedComponents(result);
      pullRemotelyChangedComponents(result);
      purgeRemotelyDeletedComponents(result);

      if (remoteCTag.isPresent()) {
        Log.d(TAG, "remote ctag post pull remote: " + remoteCTag.get());

        if (result.stats.numAuthExceptions  > 0 ||
            result.stats.numSkippedEntries  > 0 ||
            result.stats.numParseExceptions > 0 ||
            result.stats.numIoExceptions    > 0)
        {
          Log.w(TAG, "sync result has errors, will not save remote CTag to local collection");
          return;
        }

        localCollection.setCTag(remoteCTag.get());
        localCollection.commitPendingOperations();

        if (localCollection.getCTag().isPresent())
          Log.d(TAG, "local ctag post pull remote: "  + localCollection.getCTag().get());
      }
      else
        throw new PropertyParseException("Remote collection is missing CTag, things could get funny.",
                                         remoteCollection.getPath(), CalDavConstants.PROPERTY_NAME_CTAG);

    } catch (PropertyParseException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (DavException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch(IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected abstract Namespace getNamespace();

  protected abstract boolean componentHasUid(T component);

  protected void pushLocallyCreatedProperties(SyncResult result) {
    Log.d(TAG, "pushLocallyCreatedProperties()");

    try {

      Optional<String> localDisplayName = localCollection.getDisplayName();
      if (localDisplayName.isPresent()) {

        Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
        if (!remoteDisplayName.isPresent()) {
          Log.d(TAG, "remote display name not present, setting using local");
          remoteCollection.setHiddenDisplayName(localDisplayName.get());
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
    } catch (GeneralSecurityException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void pushLocallyChangedProperties(SyncResult result) {
    Log.d(TAG, "pushLocallyChangedProperties()");

    try {

      if (localCTag.isPresent() && remoteCTag.isPresent() && localCTag.get().equals(remoteCTag.get())) {
        Optional<String> localDisplayName = localCollection.getDisplayName();
        if (localDisplayName.isPresent()) {

          Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
          if (remoteDisplayName.isPresent() && !localDisplayName.get().equals(remoteDisplayName.get())) {
            Log.d(TAG, "remote display name present, updating using local");
            remoteCollection.setHiddenDisplayName(localDisplayName.get());
            result.stats.numUpdates++;
          }
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
    } catch (GeneralSecurityException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void pushLocallyDeletedComponents(SyncResult result) {
    Log.d(TAG, "pushLocallyDeletedComponents()");

   try {

     List<Pair<Long, String>> deletedIds = localCollection.getDeletedComponentIds();
     Log.d(TAG, "found + " + deletedIds.size() + " locally deleted components");

     for (Pair<Long, String> componentId : deletedIds) {
       Log.d(TAG, "removing remote component: (" + componentId.first + ", " + componentId.second + ")");

       try {

         remoteCollection.removeComponent(componentId.second);
         localCollection.removeComponent(componentId.first);
         localCollection.commitPendingOperations();
         result.stats.numDeletes++;

       } catch (DavException e) {
         AbstractDavSyncAdapter.handleException(context, e, result);
       } catch (IOException e) {
         AbstractDavSyncAdapter.handleException(context, e, result);
       } catch (RemoteException e) {
         AbstractDavSyncAdapter.handleException(context, e, result);
       } catch (OperationApplicationException e) {
         AbstractDavSyncAdapter.handleException(context, e, result);
       }
     }

     if (deletedIds.size() > 0) {
       try {

         remoteCollection.fetchProperties();

       }  catch (DavException e) {
         AbstractDavSyncAdapter.handleException(context, e, result);
       } catch (IOException e) {
         AbstractDavSyncAdapter.handleException(context, e, result);
       }
     }

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }

  }

  protected void handleServerRejectedLocalComponent(SyncResult result, Long localId) {
    Log.e(TAG, "handleServerRejectedLocalComponent() >> " + localId);

    try {

      localCollection.removeComponent(localId);
      localCollection.commitPendingOperations();

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void pushLocallyChangedComponents(SyncResult result) {
    Log.d(TAG, "pushLocallyChangedComponents()");

    try {

      List<Pair<Long, String>> updatedIds = localCollection.getUpdatedComponentIds();
      Log.d(TAG, "found + " + updatedIds.size() + " locally updated components");

      for (Pair<Long, String> componentId : updatedIds) {
        try {

          Optional<ComponentETagPair<T>> component = localCollection.getComponent(componentId.second);

          if (component.isPresent()) {
            Log.d(TAG, "updating remote component: (" + componentId.first + ", " + componentId.second + ")");

            remoteCollection.updateHiddenComponent(component.get());
            localCollection.cleanComponent(componentId.first);
            localCollection.commitPendingOperations();
            result.stats.numUpdates++;
          }
          else
            Log.e(TAG, "could not get component with id " + componentId.second + " from local collection");

        } catch (InvalidComponentException e) {

          AbstractDavSyncAdapter.handleException(context, e, result);
          handleServerRejectedLocalComponent(result, componentId.first);

        } catch (GeneralSecurityException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (DavException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (IOException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (RemoteException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

      if (updatedIds.size() > 0) {
        try {

          remoteCollection.fetchProperties();

        }  catch (DavException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (IOException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void handleServerErrorOnPushNewLocalComponent(SyncResult result, Long localId) {
    Log.e(TAG, "handleServerErrorOnPushNewLocalComponent() >> " + localId);

    try {

      localCollection.setUidToNull(localId);
      localCollection.commitPendingOperations();

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected abstract void prePushLocallyCreatedComponent(T component);

  protected void pushLocallyCreatedComponents(SyncResult result) {
    Log.d(TAG, "pushLocallyCreatedComponents()");

    try {

      List<Long> newIds = localCollection.getNewComponentIds();
      Log.d(TAG, "found + " + newIds.size() + " locally created components");

      for (Long componentId : newIds) {
        try {

          Log.d(TAG, "new local component id >> " + componentId);
          String      uid       = localCollection.populateComponentUid(componentId);
          Optional<T> component = localCollection.getComponent(componentId);

          if (component.isPresent()) {
            Log.d(TAG, "creating remote component " + componentId + " with uid " + uid);

            prePushLocallyCreatedComponent(component.get());
            remoteCollection.addHiddenComponent(component.get());
            localCollection.cleanComponent(componentId);
            localCollection.commitPendingOperations();
            result.stats.numInserts++;
          }
          else
            Log.e(TAG, "could not get component with id " + componentId +
                        " from local collection");

        } catch (InvalidComponentException e) {

          AbstractDavSyncAdapter.handleException(context, e, result);
          handleServerRejectedLocalComponent(result, componentId);

        } catch (DavException e) {

          AbstractDavSyncAdapter.handleException(context, e, result);
          handleServerErrorOnPushNewLocalComponent(result, componentId);

        } catch (IOException e) {

          AbstractDavSyncAdapter.handleException(context, e, result);
          handleServerErrorOnPushNewLocalComponent(result, componentId);

        } catch (GeneralSecurityException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (RemoteException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

      if (newIds.size() > 0) {
        try {

          remoteCollection.fetchProperties();

        }  catch (DavException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (IOException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void pullRemotelyCreatedProperties(SyncResult result) {
    Log.d(TAG, "pullRemotelyCreatedProperties()");

    try {

      Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
      if (remoteDisplayName.isPresent()) {
        Optional<String> localDisplayName = localCollection.getDisplayName();

        if (!localDisplayName.isPresent()) {
          Log.d(TAG, "local display name not present, setting using remote");
          localCollection.setDisplayName(remoteDisplayName.get());
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
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (InvalidMacException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void pullRemotelyChangedProperties(SyncResult result) {
    Log.d(TAG, "pullRemotelyChangedProperties()");

    try {

      if (localCTag.isPresent() && remoteCTag.isPresent() && !localCTag.get().equals(remoteCTag.get())) {
        Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
        if (remoteDisplayName.isPresent()) {

          Optional<String> localDisplayName = localCollection.getDisplayName();
          if (localDisplayName.isPresent() && !localDisplayName.get().equals(remoteDisplayName.get())) {
            Log.d(TAG, "local display name present, updating using remote");
            localCollection.setDisplayName(remoteDisplayName.get());
            localCollection.commitPendingOperations();
            result.stats.numUpdates++;
          }
        }
      }

    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (PropertyParseException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (InvalidMacException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  // NOTICE: it would be *almost* safe to pull remote ETags only once for all the following methods...
  protected void pullRemotelyCreatedComponents(SyncResult result) {
    Log.d(TAG, "pullRemotelyCreatedComponents()");
    List<ComponentETagPair<T>> retryList = new LinkedList<ComponentETagPair<T>>();

    try {

      HashMap<String, String> remoteETagMap = remoteCollection.getComponentETags();
      Log.d(TAG, "found " + remoteETagMap.size() + " remote components");

      for (java.util.Map.Entry<String, String> remoteETagEntry : remoteETagMap.entrySet()) {
        try {

          Optional<ComponentETagPair<T>> localComponent = localCollection.getComponent(remoteETagEntry.getKey());
          if (!localComponent.isPresent()) {

            Log.d(TAG, "remote component " + remoteETagEntry.getKey() + " not present locally");
            Optional<ComponentETagPair<T>> remoteComponent = remoteCollection.getHiddenComponent(remoteETagEntry.getKey());
            if (remoteComponent.isPresent()) {

              if (componentHasUid(remoteComponent.get().getComponent())) {
                try {

                  Log.d(TAG, "creating local component " + remoteETagEntry.getKey() + " using remote");
                  localCollection.addComponent(remoteComponent.get());
                  localCollection.commitPendingOperations();
                  result.stats.numInserts++;

                } catch (InvalidComponentException e) {
                  Log.w(TAG, "caught invalid component exception. could be a recurrence exception " +
                             "who's parent has yet to get pulled down.");
                  retryList.add(remoteComponent.get());
                }

              }
              else
                throw new InvalidComponentException("remote component is missing UID", true, getNamespace(),
                                                    remoteCollection.getPath(), remoteETagEntry.getKey());
            }
            else
              Log.e(TAG, "remote component " + remoteETagEntry.getKey() + " from etag set not present remotely");
          }

        } catch (InvalidComponentException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (InvalidMacException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (GeneralSecurityException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (DavException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (IOException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (RemoteException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

      for (ComponentETagPair<T> retryComponent : retryList) {
        try {

          localCollection.addComponent(retryComponent);
          localCollection.commitPendingOperations();
          result.stats.numInserts++;

        } catch (InvalidComponentException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (RemoteException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

    } catch (DavException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void pullRemotelyChangedComponents(SyncResult result) {
    Log.d(TAG, "pullRemotelyChangedComponents()");

    try {

      HashMap<String, String> remoteETagMap = remoteCollection.getComponentETags();
      Log.d(TAG, "found + " + remoteETagMap.size() + " remote components");

      for (java.util.Map.Entry<String, String> remoteETagEntry : remoteETagMap.entrySet()) {
        try {

          Optional<ComponentETagPair<T>> localComponent = localCollection.getComponent(remoteETagEntry.getKey());
          if (localComponent.isPresent()) {
            Log.d(TAG, "remote component " + remoteETagEntry.getKey() + " is present locally");

            if (!localComponent.get().getETag().isPresent() ||
                !localComponent.get().getETag().get().equals(remoteETagEntry.getValue())) {

              Optional<ComponentETagPair<T>> remoteComponent = remoteCollection.getHiddenComponent(remoteETagEntry.getKey());
              if (remoteComponent.isPresent()) {

                if (componentHasUid(remoteComponent.get().getComponent())) {
                  Log.d(TAG, "updating local component " + remoteETagEntry.getKey() + " using remote");
                  localCollection.updateComponent(remoteComponent.get());
                  localCollection.commitPendingOperations();
                  result.stats.numUpdates++;
                } else
                  throw new InvalidComponentException("remote component is missing UID", true, getNamespace(),
                                                      remoteCollection.getPath(), remoteETagEntry.getKey());
              } else
                Log.e(TAG, "remote component " + remoteETagEntry.getKey() + " from etag set not present remotely");
            }
          }

        } catch (InvalidComponentException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (InvalidMacException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (GeneralSecurityException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (DavException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (IOException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (RemoteException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

    } catch (DavException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }

  protected void purgeRemotelyDeletedComponents(SyncResult result) {
    Log.d(TAG, "pullRemotelyDeletedComponents()");

    try {

      HashMap<String, String> localETagMap  = localCollection.getComponentETags();
      HashMap<String, String> remoteETagMap = remoteCollection.getComponentETags();

      Log.d(TAG, "found " + remoteETagMap.size() + " remote components");
      Log.d(TAG, "found " + localETagMap.size()  + " local components");

      List<String> componentsMissingRemotely = new LinkedList<String>();
      for (java.util.Map.Entry<String, String> localETagEntry : localETagMap.entrySet()) {
        boolean found_remotely = false;

        for (java.util.Map.Entry<String, String> remoteETagEntry : remoteETagMap.entrySet()) {
          if (localETagEntry.getKey().equals(remoteETagEntry.getKey()))
            found_remotely = true;
        }

        if (!found_remotely)
          componentsMissingRemotely.add(localETagEntry.getKey());
      }

      for (String remoteUid : componentsMissingRemotely) {
        Log.d(TAG, "deleting local component " + remoteUid + " missing from remote");

        try {

          localCollection.removeComponent(remoteUid);
          localCollection.commitPendingOperations();
          result.stats.numDeletes++;

        } catch (RemoteException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }
      }

    } catch (DavException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (RemoteException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }
}
