package org.anhonesteffort.flock;

import android.accounts.AccountManager;
import android.app.Service;
import android.os.Bundle;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.AccountAuthenticator;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.sync.key.DavKeyCollection;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLException;

/**
 * Created by rhodey
 */
public abstract class ImportAccountService extends Service {

  private static final String TAG = "org.anhonesteffort.flock.ImportAccountService";

  private void handleImportOrGenerateKeyMaterial(Bundle     result,
                                                 DavAccount account,
                                                 String     cipherPassphrase)
  {
    Optional<String[]> saltAndEncryptedKeyMaterial = Optional.absent();
    KeyStore.saveMasterPassphrase(getBaseContext(), cipherPassphrase);
    DavAccountHelper.setAccountDavHREF(getBaseContext(), account.getDavHostHREF());

    try {

      DavKeyStore                davKeyStore   = DavAccountHelper.getDavKeyStore(getBaseContext(), account);
      Optional<DavKeyCollection> keyCollection = davKeyStore.getCollection();

      if (keyCollection.isPresent()) {
        if (keyCollection.get().getKeyMaterialSalt().isPresent()   &&
            keyCollection.get().getEncryptedKeyMaterial().isPresent())
        {
          saltAndEncryptedKeyMaterial = Optional.of(
              new String[] {
                  keyCollection.get().getKeyMaterialSalt().get(),
                  keyCollection.get().getEncryptedKeyMaterial().get()
              }
          );
        }
        if (!keyCollection.get().isMigrationComplete() &&
            !keyCollection.get().isMigrationStarted())
        {
          KeyStore.setUseCipherVersionZero(getBaseContext(), true);
        }
        else if (keyCollection.get().isMigrationComplete()) {
          MigrationHelperBroadcastReceiver.setMigrationUpdateHandled(getBaseContext());
          MigrationHelperBroadcastReceiver.setUiDisabledForMigration(getBaseContext(), false);
        }
      }
      else {
        DavKeyStore.createCollection(getBaseContext(), account);
        keyCollection = davKeyStore.getCollection();

        if (!keyCollection.isPresent()) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_DAV_SERVER_ERROR);
          return;
        }

        keyCollection.get().setMigrationComplete(getBaseContext());
        MigrationHelperBroadcastReceiver.setMigrationUpdateHandled(getBaseContext());
        MigrationHelperBroadcastReceiver.setUiDisabledForMigration(getBaseContext(), false);
      }

    } catch (InvalidComponentException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (PropertyParseException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (DavException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (SSLException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }

    try {

      if (saltAndEncryptedKeyMaterial.isPresent())
        KeyHelper.importSaltAndEncryptedKeyMaterial(getBaseContext(), saltAndEncryptedKeyMaterial.get());
      else
        KeyHelper.generateAndSaveSaltAndKeyMaterial(getBaseContext());

      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

    } catch (InvalidMacException e) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_INVALID_CIPHER_PASSPHRASE);
    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      Log.e(TAG, "handleImportOrGenerateKeyMaterial()", e);
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
    }
  }

  private void handleInvalidateEverything() {
    DavAccountHelper.invalidateAccount(getBaseContext());
    KeyStore.invalidateKeyMaterial(getBaseContext());
  }

  protected Bundle handleImportAccount(Bundle     result,
                                       DavAccount account,
                                       String     cipherPassphrase)
  {
    handleInvalidateEverything();
    handleImportOrGenerateKeyMaterial(result, account, cipherPassphrase);

    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
      AccountManager.get(getBaseContext()).addAccountExplicitly(account.getOsAccount(), "", null);

      DavAccountHelper.setAccountUsername(getBaseContext(), account.getUserId());
      DavAccountHelper.setAccountPassword(getBaseContext(), account.getAuthToken());
      DavAccountHelper.setAccountDavHREF(getBaseContext(),  account.getDavHostHREF());

      AccountAuthenticator.setAllowAccountRemoval(getBaseContext(), false);
      new KeySyncScheduler(getBaseContext()).requestSync();
    }
    else
      handleInvalidateEverything();

    return result;
  }

}
