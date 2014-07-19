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

package org.anhonesteffort.flock.sync.key;

import android.content.Context;
import android.content.SyncResult;
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Programmer: rhodey
 */
public class KeySyncWorker {

  private static final String TAG = "org.anhonesteffort.flock.sync.key.KeySyncWorker";

  private final Context    context;
  private final DavAccount account;

  public KeySyncWorker(Context context, DavAccount account) {
    this.context = context;
    this.account = account;
  }

  public void run(SyncResult result) {
    Log.d(TAG, "now syncing");

    Optional<String> localKeyMaterialSalt      = Optional.absent();
    Optional<String> localEncryptedKeyMaterial = Optional.absent();

    try {

      localKeyMaterialSalt      = KeyHelper.buildEncodedSalt(context);
      localEncryptedKeyMaterial = KeyStore.getEncryptedKeyMaterial(context);

      if (!localKeyMaterialSalt.isPresent() || !localEncryptedKeyMaterial.isPresent()) {
        Log.w(TAG, "missing local key material salt or local encrypted key material.");
        return;
      }

    } catch (IOException e) {
      Log.e(TAG, "caught exception while retrieving salt and encrypted key material", e);
      AbstractDavSyncAdapter.handleException(context, e, result);
      return;
    }

    try {

      if (!KeyHelper.masterPassphraseIsValid(context) &&
          !DavAccountHelper.isUsingOurServers(context))
      {
        KeySyncUtil.showCipherPassphraseInvalidNotification(context);
        return;
      }

    } catch (GeneralSecurityException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }

    try {

      HidingCalDavCollection keyCollection = KeySyncUtil.getOrCreateKeyCollection(context, account);

      try {

        Optional<String> remoteKeyMaterialSalt = keyCollection.getKeyMaterialSalt();
        if (!remoteKeyMaterialSalt.isPresent()) {
          Log.e(TAG, "remote key material salt is missing, will put local");
          keyCollection.setKeyMaterialSalt(localKeyMaterialSalt.get());
        }

      } catch (PropertyParseException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (DavException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (IOException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      }

      Optional<String> remoteKeyMaterial = keyCollection.getEncryptedKeyMaterial();
      if (!remoteKeyMaterial.isPresent()) {
        Log.e(TAG, "remote encrypted key material is missing, will put local");
        keyCollection.setEncryptedKeyMaterial(localEncryptedKeyMaterial.get());
      }

      else if (!DavAccountHelper.isUsingOurServers(account) &&
               !localEncryptedKeyMaterial.get().equals(remoteKeyMaterial.get()))
      {
        Log.e(TAG, "remote encrypted key material is different, will import locally");
        KeyStore.saveEncryptedKeyMaterial(context, remoteKeyMaterial.get());
        KeySyncUtil.showCipherPassphraseInvalidNotification(context);
      }

      keyCollection.getStore().closeHttpConnection();

    } catch (PropertyParseException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (DavException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (GeneralSecurityException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }
}
