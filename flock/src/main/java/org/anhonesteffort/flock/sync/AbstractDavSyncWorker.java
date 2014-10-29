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
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public abstract class AbstractDavSyncWorker<T> implements SyncWorker {

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

  protected abstract Namespace getNamespace();

  protected void handleLogMessage(String message) {
    Log.d(TAG, localCollection.getPath() + " - " + message);
  }

  @Override
  public void run() {
    Log.d(TAG, "now syncing local: " + localCollection.getPath() +
               " with remote: "      + remoteCollection.getPath());

    try {

      SyncWorkerUtil.handleMakeFlockCollection(localCollection, remoteCollection);

      localCTag  = localCollection.getCTag();
      remoteCTag = remoteCollection.getCTag();

      if (localCTag.isPresent())
        handleLogMessage("local ctag pre push local: " + localCTag.get());
      else
        handleLogMessage("local ctag not present pre push local");

      if (remoteCTag.isPresent())
        handleLogMessage("remote ctag pre push local: " + remoteCTag.get());
      else
        handleLogMessage("remote ctag not present pre push local");

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
        handleLogMessage("remote ctag post pull remote: " + remoteCTag.get());

        if (result.stats.numAuthExceptions  > 0 ||
            result.stats.numSkippedEntries  > 0 ||
            result.stats.numParseExceptions > 0 ||
            result.stats.numIoExceptions    > 0)
        {
          handleLogMessage("sync result has errors, will not save remote CTag to local collection");
          return;
        }

        localCollection.setCTag(remoteCTag.get());
        localCollection.commitPendingOperations();
      }
      else
        throw new PropertyParseException("Remote collection is missing CTag, things could get funny",
                                         remoteCollection.getPath(), CalDavConstants.PROPERTY_NAME_CTAG);

    } catch (PropertyParseException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (DavException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch(IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  @Override
  public void cleanup() {
    remoteCollection.closeHttpConnection();
  }

  protected abstract Optional<String> getComponentUid(T component);

  protected void pushLocallyCreatedProperties(SyncResult result) {
    handleLogMessage("pushLocallyCreatedProperties()");

    try {

      Optional<String> localDisplayName = localCollection.getDisplayName();
      if (localDisplayName.isPresent()) {

        Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
        if (!remoteDisplayName.isPresent()) {
          handleLogMessage("remote display name not present, setting using local");
          remoteCollection.setHiddenDisplayName(localDisplayName.get());
          result.stats.numInserts++;
        }
      }

    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (PropertyParseException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (DavException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (InvalidMacException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void pushLocallyChangedProperties(SyncResult result) {
    handleLogMessage("pushLocallyChangedProperties()");

    try {

      if (localCTag.isPresent() && remoteCTag.isPresent() && localCTag.get().equals(remoteCTag.get())) {
        Optional<String> localDisplayName = localCollection.getDisplayName();
        if (localDisplayName.isPresent()) {

          Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
          if (remoteDisplayName.isPresent() && !localDisplayName.get().equals(remoteDisplayName.get())) {
            handleLogMessage("remote display name present, updating using local");
            remoteCollection.setHiddenDisplayName(localDisplayName.get());
            result.stats.numUpdates++;
          }
        }
      }

    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (PropertyParseException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (DavException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (InvalidMacException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void pushLocallyDeletedComponents(SyncResult result) {
    handleLogMessage("pushLocallyDeletedComponents()");

   try {

     List<Pair<Long, String>> deletedIds = localCollection.getDeletedComponentIds();
     handleLogMessage("found " + deletedIds.size() + " locally deleted components");

     for (Pair<Long, String> componentId : deletedIds) {
       try {

         handleLogMessage("removing remote component: (" + componentId.first + ", " + componentId.second + ")");
         remoteCollection.removeComponent(componentId.second);
         localCollection.removeComponent(componentId.first);
         localCollection.commitPendingOperations();
         result.stats.numDeletes++;

       } catch (DavException e) {
         SyncWorkerUtil.handleException(context, e, result);
       } catch (IOException e) {
         SyncWorkerUtil.handleException(context, e, result);
       } catch (RemoteException e) {
         SyncWorkerUtil.handleException(context, e, result);
       } catch (OperationApplicationException e) {
         SyncWorkerUtil.handleException(context, e, result);
       }
     }

     if (deletedIds.size() > 0)
       SyncWorkerUtil.handleRefreshCollectionProperties(context, result, remoteCollection);

    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void pushLocallyChangedComponents(SyncResult result) {
    handleLogMessage("pushLocallyChangedComponents()");

    try {

      List<Pair<Long, String>> updatedIds = localCollection.getUpdatedComponentIds();
      handleLogMessage("found " + updatedIds.size() + " locally updated components");

      for (Pair<Long, String> componentId : updatedIds) {
        try {

          Optional<ComponentETagPair<T>> component = localCollection.getComponent(componentId.second);

          if (component.isPresent()) {
            handleLogMessage("updating remote component: (" + componentId.first + ", " + componentId.second + ")");
            remoteCollection.updateHiddenComponent(component.get());
            localCollection.cleanComponent(componentId.first);
            localCollection.commitPendingOperations();
            result.stats.numUpdates++;
          }
          else
            handleLogMessage("could not get component with id " + componentId.second + " from local collection");

        } catch (InvalidComponentException e) {

          SyncWorkerUtil.handleException(context, e, result);
          SyncWorkerUtil.handleServerRejectedLocalComponent(localCollection, componentId.first, context, result);

        } catch (GeneralSecurityException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (DavException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (IOException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (RemoteException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          SyncWorkerUtil.handleException(context, e, result);
        }
      }

      if (updatedIds.size() > 0)
        SyncWorkerUtil.handleRefreshCollectionProperties(context, result, remoteCollection);

    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected abstract void prePushLocallyCreatedComponent(T component);

  protected void pushLocallyCreatedComponents(SyncResult result) {
    handleLogMessage("pushLocallyCreatedComponents()");

    try {

      List<Long> newIds = localCollection.getNewComponentIds();
      handleLogMessage("found " + newIds.size() + " locally created components");

      for (Long componentId : newIds) {
        try {

          String      uid       = localCollection.populateComponentUid(componentId);
          Optional<T> component = localCollection.getComponent(componentId);

          if (component.isPresent()) {
            handleLogMessage("creating remote component: (" + componentId + ", " + uid + ")");
            prePushLocallyCreatedComponent(component.get());
            remoteCollection.addHiddenComponent(component.get());
            localCollection.cleanComponent(componentId);
            localCollection.commitPendingOperations();
            result.stats.numInserts++;
          }
          else
            handleLogMessage("could not get component (" + componentId + ", " + uid + ") from local collection");

        } catch (InvalidComponentException e) {

          SyncWorkerUtil.handleException(context, e, result);
          SyncWorkerUtil.handleServerRejectedLocalComponent(localCollection, componentId, context, result);

        } catch (DavException e) {

          SyncWorkerUtil.handleException(context, e, result);

          if (e.getErrorCode() == DavServletResponse.SC_PRECONDITION_FAILED)
            SyncWorkerUtil.handleServerRejectedLocalComponent(localCollection, componentId, context, result);
          else
            SyncWorkerUtil.handleServerErrorOnPushNewLocalComponent(localCollection, componentId, context, result);

        } catch (IOException e) {

          SyncWorkerUtil.handleException(context, e, result);
          SyncWorkerUtil.handleServerErrorOnPushNewLocalComponent(localCollection, componentId, context, result);

        } catch (GeneralSecurityException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (RemoteException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          SyncWorkerUtil.handleException(context, e, result);
        }
      }

      if (newIds.size() > 0)
        SyncWorkerUtil.handleRefreshCollectionProperties(context, result, remoteCollection);

    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void pullRemotelyCreatedProperties(SyncResult result) {
    handleLogMessage("pullRemotelyCreatedProperties()");

    try {

      Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
      if (remoteDisplayName.isPresent()) {
        Optional<String> localDisplayName = localCollection.getDisplayName();

        if (!localDisplayName.isPresent()) {
          handleLogMessage("local display name not present, setting using remote");
          localCollection.setDisplayName(remoteDisplayName.get());
          localCollection.commitPendingOperations();
          result.stats.numInserts++;
        }
      }

    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (PropertyParseException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (InvalidMacException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void pullRemotelyChangedProperties(SyncResult result) {
    handleLogMessage("pullRemotelyChangedProperties()");

    try {

      if (localCTag.isPresent() && remoteCTag.isPresent() && !localCTag.get().equals(remoteCTag.get())) {
        Optional<String> remoteDisplayName = remoteCollection.getHiddenDisplayName();
        if (remoteDisplayName.isPresent()) {

          Optional<String> localDisplayName = localCollection.getDisplayName();
          if (localDisplayName.isPresent() && !localDisplayName.get().equals(remoteDisplayName.get())) {
            handleLogMessage("local display name present, updating using remote");
            localCollection.setDisplayName(remoteDisplayName.get());
            localCollection.commitPendingOperations();
            result.stats.numUpdates++;
          }
        }
      }

    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (OperationApplicationException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (PropertyParseException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (InvalidMacException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void pullRemotelyCreatedComponents(SyncResult result) {
    handleLogMessage("pullRemotelyCreatedComponents()");
    List<ComponentETagPair<T>> retryList = new LinkedList<ComponentETagPair<T>>();

    try {

      HashMap<String, String> remoteETagMap      = remoteCollection.getComponentETags();
      List<String>            uidsMissingLocally = SyncWorkerUtil.handleFilterUidsMissingLocally(localCollection, remoteETagMap.keySet());
      List<List<String>>      reportLists        = SyncWorkerUtil.handlePartitionUidsForReports(uidsMissingLocally);

      handleLogMessage("found " + remoteETagMap.size() + " remote components, " + uidsMissingLocally.size() +
                      " are missing locally, will require " + reportLists.size() + " multi-get report(s)");

      for (List<String> uidsForReport : reportLists) {
        try {

          DecryptedMultiStatusResult<T> remoteComponents = remoteCollection.getHiddenComponents(uidsForReport);
          SyncWorkerUtil.handleDoStuffWithMultiStatusResult(uidsForReport, remoteComponents, context, result);

          for (ComponentETagPair<T> remoteComponent : remoteComponents.getComponentETagPairs()) {
            try {

              Optional<String> componentUid = getComponentUid(remoteComponent.getComponent());
              if (componentUid.isPresent()) {
                try {

                  handleLogMessage("creating local component " + componentUid.get() + " using remote");
                  localCollection.addComponent(remoteComponent);
                  localCollection.commitPendingOperations();
                  result.stats.numInserts++;

                } catch (InvalidComponentException e) {
                  handleLogMessage("caught invalid component exception, could be a recurrence exception " +
                                   "who's parent has yet to get pulled down, will retry.");
                  retryList.add(remoteComponent);
                }

              }
              else
                throw new InvalidRemoteComponentException("remote component is missing UID",
                                                          getNamespace(), remoteCollection.getPath());

            } catch (InvalidRemoteComponentException e) {
              SyncWorkerUtil.handleException(context, e, result);
            } catch (RemoteException e) {
              SyncWorkerUtil.handleException(context, e, result);
            } catch (OperationApplicationException e) {
              SyncWorkerUtil.handleException(context, e, result);
            }
          }

        } catch (GeneralSecurityException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (DavException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (IOException e) {
          SyncWorkerUtil.handleException(context, e, result);
        }
      }

      for (ComponentETagPair<T> retryComponent : retryList) {
        Optional<String> componentUid = getComponentUid(retryComponent.getComponent());
        try {

          if (componentUid.isPresent()) {
            handleLogMessage("retying creation of local component " + componentUid.get() + " using remote");
            localCollection.addComponent(retryComponent);
            localCollection.commitPendingOperations();
            result.stats.numInserts++;
          }
          else
            throw new InvalidRemoteComponentException("retry remote component is missing UID",
                                                      getNamespace(), remoteCollection.getPath());

        } catch (InvalidRemoteComponentException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (RemoteException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          SyncWorkerUtil.handleException(context, e, result);
        }
      }

    } catch (DavException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void pullRemotelyChangedComponents(SyncResult result) {
    handleLogMessage("pullRemotelyChangedComponents()");

    try {

      HashMap<String, String>           remoteETagMap  = remoteCollection.getComponentETags();
      HashMap<String, Optional<String>> changedETagMap = SyncWorkerUtil.handleFilterUidsChangedRemotely(localCollection, remoteETagMap);
      List<List<String>>                reportLists    = SyncWorkerUtil.handlePartitionUidsForReports(changedETagMap.keySet());

      handleLogMessage("found " + remoteETagMap.size() + " remote components, " + changedETagMap.size() +
                      " are updated remotely, will require " + reportLists.size() + " multi-get report(s)");

      for (List<String> uidsForReport : reportLists) {
        try {

          DecryptedMultiStatusResult<T> remoteComponents = remoteCollection.getHiddenComponents(uidsForReport);
          SyncWorkerUtil.handleDoStuffWithMultiStatusResult(uidsForReport, remoteComponents, context, result);

          for (ComponentETagPair<T> remoteComponent : remoteComponents.getComponentETagPairs()) {
            try {

              Optional<String> componentUid = getComponentUid(remoteComponent.getComponent());
              if (componentUid.isPresent()) {
                handleLogMessage("updating local component " + componentUid.get() + " using remote");
                localCollection.updateComponent(remoteComponent);
                localCollection.commitPendingOperations();
                result.stats.numUpdates++;
              }
              else
                throw new InvalidRemoteComponentException("remote component is missing UID",
                                                          getNamespace(), remoteCollection.getPath());

            } catch (InvalidRemoteComponentException e) {
              SyncWorkerUtil.handleException(context, e, result);
            } catch (RemoteException e) {
              SyncWorkerUtil.handleException(context, e, result);
            } catch (OperationApplicationException e) {
              SyncWorkerUtil.handleException(context, e, result);
            }
          }

        } catch (GeneralSecurityException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (DavException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (IOException e) {
          SyncWorkerUtil.handleException(context, e, result);
        }
      }

    } catch (DavException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }

  protected void purgeRemotelyDeletedComponents(SyncResult result) {
    handleLogMessage("pullRemotelyDeletedComponents()");

    try {

      HashMap<String, String> localETagMap  = localCollection.getComponentETags();
      HashMap<String, String> remoteETagMap = remoteCollection.getComponentETags();

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

      handleLogMessage("found " + componentsMissingRemotely.size()  + " local components missing remotely");
      for (String remoteUid : componentsMissingRemotely) {
        try {

          handleLogMessage("deleting local component " + remoteUid + " missing from remote");
          localCollection.removeComponent(remoteUid);
          localCollection.commitPendingOperations();
          result.stats.numDeletes++;

        } catch (RemoteException e) {
          SyncWorkerUtil.handleException(context, e, result);
        } catch (OperationApplicationException e) {
          SyncWorkerUtil.handleException(context, e, result);
        }
      }

    } catch (DavException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (IOException e) {
      SyncWorkerUtil.handleException(context, e, result);
    } catch (RemoteException e) {
      SyncWorkerUtil.handleException(context, e, result);
    }
  }
}
