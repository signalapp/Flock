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
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.sync.AbstractDavSyncAdapter;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
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
    try {

      DavKeyStore davKeyStore = DavAccountHelper.getDavKeyStore(context, account);

      try {

        Optional<String> localKeyMaterialSalt      = KeyHelper.buildEncodedSalt(context);
        Optional<String> localEncryptedKeyMaterial = KeyStore.getEncryptedKeyMaterial(context);

        if (!localKeyMaterialSalt.isPresent() || !localEncryptedKeyMaterial.isPresent()) {
          Log.e(TAG, "missing local key material salt or local encrypted key material.");
          return;
        }

        Optional<DavKeyCollection> keyCollection = davKeyStore.getCollection();

        if (!keyCollection.isPresent()) {
          Log.w(TAG, "key collection is missing");
          return;
        }

        try {

          if (!KeyHelper.masterPassphraseIsValid(context) &&
              !DavAccountHelper.isUsingOurServers(context))
          {
            KeySyncService.showCipherPassphraseInvalidNotification(context);
            return;
          }

        } catch (GeneralSecurityException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (IOException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }

        try {

          Optional<String> remoteKeyMaterialSalt      = keyCollection.get().getKeyMaterialSalt();
          Optional<String> remoteEncryptedKeyMaterial = keyCollection.get().getEncryptedKeyMaterial();

          if (!remoteKeyMaterialSalt.isPresent())
            keyCollection.get().setKeyMaterialSalt(localKeyMaterialSalt.get());

          if (!remoteEncryptedKeyMaterial.isPresent())
            keyCollection.get().setEncryptedKeyMaterial(localEncryptedKeyMaterial.get());

          else if (remoteKeyMaterialSalt.isPresent() &&
                   !remoteEncryptedKeyMaterial.get().equals(localEncryptedKeyMaterial.get()))
          {
            try {

              KeyHelper.importSaltAndEncryptedKeyMaterial(context, new String[]{
                  remoteKeyMaterialSalt.get(),
                  remoteEncryptedKeyMaterial.get()
              });

            } catch (InvalidMacException e) {
              Log.d(TAG, "caught invalid mac exception while importing remote key material, " +
                          "assuming password change for non-flock sync user.");
              KeyStore.saveEncryptedKeyMaterial(context, remoteEncryptedKeyMaterial.get());
              KeySyncService.showCipherPassphraseInvalidNotification(context);
            }
          }

        } catch (PropertyParseException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (DavException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (IOException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        } catch (GeneralSecurityException e) {
          AbstractDavSyncAdapter.handleException(context, e, result);
        }

      } catch (PropertyParseException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (DavException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } catch (IOException e) {
        AbstractDavSyncAdapter.handleException(context, e, result);
      } finally {
        davKeyStore.closeHttpConnection();
      }

    } catch (IOException e) {
      AbstractDavSyncAdapter.handleException(context, e, result);
    }
  }
}
