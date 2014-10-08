package org.anhonesteffort.flock;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.crypto.KeyUtil;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.sync.key.DavKeyCollection;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Created by rhodey.
 */
public class ChangeEncryptionPasswordService extends Service {

  private static final String TAG = "org.anhonesteffort.flock.ChangeEncryptionPasswordService";

  private   static final String KEY_INTENT                = "ChangeEncryptionPasswordService.KEY_INTENT";
  protected static final String KEY_MESSENGER             = "ChangeEncryptionPasswordService.KEY_MESSENGER";
  protected static final String KEY_OLD_MASTER_PASSPHRASE = "ChangeEncryptionPasswordService.KEY_OLD_MASTER_PASSPHRASE";
  protected static final String KEY_NEW_MASTER_PASSPHRASE = "ChangeEncryptionPasswordService.KEY_NEW_MASTER_PASSPHRASE";
  protected static final String KEY_ACCOUNT               = "ChangeEncryptionPasswordService.KEY_ACCOUNT";

  private Looper                     serviceLooper;
  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;

  private Messenger  messenger;
  private String     oldMasterPassphrase;
  private String     newMasterPassphrase;
  private DavAccount account;

  private int     resultCode;
  private boolean remoteActivityIsAlive = true;

  private void handleInitializeNotification() {
    Log.d(TAG, "handleInitializeNotification()");

    notificationBuilder.setContentTitle(getString(R.string.title_change_encryption_password))
        .setContentText(getString(R.string.updating_encryption_secrets))
        .setProgress(0, 0, true)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(9002, notificationBuilder.build());
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");

    if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      return;

    Bundle errorBundler = new Bundle();
    errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE,             resultCode);
    ErrorToaster.handleDisplayToastBundledError(getBaseContext(), errorBundler);

    notificationBuilder
        .setProgress(0, 0, false)
        .setContentText(getString(R.string.password_change_failed));

    notifyManager.notify(9002, notificationBuilder.build());
  }


  private void handleChangeComplete() {
    Log.d(TAG, "handleChangeComplete()");

    if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      stopForeground(true);
    else
      stopForeground(false);

    stopSelf();
  }

  private void handleChangeOwsAuthToken(Bundle result, String passphrase) {
    Log.d(TAG, "handleChangeOwsAuthToken()");
    RegistrationApi registrationApi = new RegistrationApi(getBaseContext());

    try {

      String newAuthToken = KeyUtil.getAuthTokenForPassphrase(passphrase);

      registrationApi.setAccountPassword(account, newAuthToken);
      DavAccountHelper.setAccountPassword(getBaseContext(), newAuthToken);
      NotificationDrawer.disableAuthNotificationsForRunningAdapters(getBaseContext(), account.getOsAccount());

      account = new DavAccount(account.getUserId(), newAuthToken, account.getDavHostHREF());

      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

    } catch (RegistrationApiException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private String handleUpdateMasterPassphrase(Bundle result) {
    Log.d(TAG, "handleUpdateMasterPassphrase()");

    Optional<String> oldEncryptedKeyMaterial = KeyStore.getEncryptedKeyMaterial(getBaseContext());
    if (!oldEncryptedKeyMaterial.isPresent()) {
      Log.e(TAG, "old encrypted key material is missing");
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
      return null;
    }

    KeyStore.saveMasterPassphrase(getBaseContext(), newMasterPassphrase);

    try {

      Optional<String> encryptedKeyMaterial = KeyHelper.buildEncryptedKeyMaterial(getBaseContext());
      if (encryptedKeyMaterial.isPresent()) {
        KeyStore.saveEncryptedKeyMaterial(getBaseContext(), encryptedKeyMaterial.get());

        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
        return encryptedKeyMaterial.get();
      }

      else {
        Log.e(TAG, "new encrypted key material is missing");
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
      }

    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }

    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS) {
      Log.w(TAG, "something went wrong, reverting to old passphrase");
      KeyStore.saveMasterPassphrase(getBaseContext(),     oldMasterPassphrase);
      KeyStore.saveEncryptedKeyMaterial(getBaseContext(), oldEncryptedKeyMaterial.get());
    }

    return null;
  }

  private void handleUpdateRemoteKeyMaterial(Bundle result, String encryptedKeyMaterial) {
    Log.d(TAG, "handleUpdateRemoteKeyMaterial()");

    try {

      DavKeyStore                davKeyStore   = DavAccountHelper.getDavKeyStore(getBaseContext(), account);
      Optional<DavKeyCollection> keyCollection = davKeyStore.getCollection();

      if (!keyCollection.isPresent()) {
        Log.e(TAG, "key collection is missing!");
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
        return;
      }

      keyCollection.get().setEncryptedKeyMaterial(encryptedKeyMaterial);
      davKeyStore.closeHttpConnection();

      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

    } catch (PropertyParseException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (DavException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private void handleRevertLocalKeyMaterial(Bundle result) {
    Log.w(TAG, "handleRevertLocalKeyMaterial()");

    KeyStore.saveMasterPassphrase(getBaseContext(), oldMasterPassphrase);

    try {

      Optional<String> encryptedKeyMaterial = KeyHelper.buildEncryptedKeyMaterial(getBaseContext());
      if (encryptedKeyMaterial.isPresent())
        KeyStore.saveEncryptedKeyMaterial(getBaseContext(), encryptedKeyMaterial.get());

      else {
        Log.e(TAG, "old, reverted encrypted key material is missing!!! XXX :(");
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
        DavAccountHelper.invalidateAccount(getBaseContext());
        KeyStore.invalidateKeyMaterial(getBaseContext());
      }

    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private void handleRevertOwsAuthToken(Bundle result) {
    Log.w(TAG, "handleRevertOwsAuthToken()");
    int statusSave = result.getInt(ErrorToaster.KEY_STATUS_CODE);

    handleChangeOwsAuthToken(result, oldMasterPassphrase);
    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS) {
      Log.e(TAG, "unable to revert OWS auth token!!! XXX :(");
      DavAccountHelper.invalidateAccount(getBaseContext());
      KeyStore.invalidateKeyMaterial(getBaseContext());
    }

    result.putInt(ErrorToaster.KEY_STATUS_CODE, statusSave);
  }

  private void handleStartChangeEncryptionPassword() {
    Log.d(TAG, "handleStartChangeEncryptionPassword()");

    Bundle result = new Bundle();
    handleInitializeNotification();

    if (DavAccountHelper.isUsingOurServers(account)) {
      handleChangeOwsAuthToken(result, newMasterPassphrase);
      if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {

        String encryptedKeyMaterial = handleUpdateMasterPassphrase(result);
        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {

          handleUpdateRemoteKeyMaterial(result, encryptedKeyMaterial);
          if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS) {
            handleRevertOwsAuthToken(result);
            handleRevertLocalKeyMaterial(result);
          }
        }
        else
          handleRevertOwsAuthToken(result);
      }
    }

    else {
      String encryptedKeyMaterial = handleUpdateMasterPassphrase(result);
      if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
        handleUpdateRemoteKeyMaterial(result, encryptedKeyMaterial);
        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS)
          handleRevertLocalKeyMaterial(result);
      }
    }

    Message message      = Message.obtain();
            message.arg1 = result.getInt(ErrorToaster.KEY_STATUS_CODE);
            resultCode   = result.getInt(ErrorToaster.KEY_STATUS_CODE);

    try {

      messenger.send(message);

    } catch (RemoteException e) {
      Log.e(TAG, "caught exception while sending message to activity >> ", e);
      remoteActivityIsAlive = false;
    }

    handleChangeComplete();
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("ChangeEncryptionPasswordService", HandlerThread.NORM_PRIORITY);
    thread.start();

    serviceLooper  = thread.getLooper();
    serviceHandler = new ServiceHandler(serviceLooper);

    notifyManager       = (NotificationManager)getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);
    notificationBuilder = new NotificationCompat.Builder(getBaseContext());
  }

  private final class ServiceHandler extends Handler {

    public ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      Log.d(TAG, "handleMessage()");
      Intent intent = msg.getData().getParcelable(KEY_INTENT);

      if (intent != null) {
        if (intent.getExtras()                                      != null &&
            intent.getExtras().get(KEY_MESSENGER)                   != null &&
            intent.getExtras().getString(KEY_OLD_MASTER_PASSPHRASE) != null &&
            intent.getExtras().getString(KEY_NEW_MASTER_PASSPHRASE) != null &&
            intent.getExtras().getBundle(KEY_ACCOUNT)               != null)
        {
          if (!DavAccount.build(intent.getExtras().getBundle(KEY_ACCOUNT)).isPresent()) {
            Log.e(TAG, "received bad account bundle");
            return;
          }

          messenger           = (Messenger) intent.getExtras().get(KEY_MESSENGER);
          oldMasterPassphrase = intent.getExtras().getString(KEY_OLD_MASTER_PASSPHRASE);
          newMasterPassphrase = intent.getExtras().getString(KEY_NEW_MASTER_PASSPHRASE);
          account             = DavAccount.build(intent.getExtras().getBundle(KEY_ACCOUNT)).get();

          handleStartChangeEncryptionPassword();
        }
        else
          Log.e(TAG, "received intent without messenger, old or new master passphrase, or account");
      }
      else
        Log.e(TAG, "received message with null intent");
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Message msg = serviceHandler.obtainMessage();
    msg.getData().putParcelable(KEY_INTENT, intent);
    serviceHandler.sendMessage(msg);

    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "onBind()");
    return null;
  }

}
