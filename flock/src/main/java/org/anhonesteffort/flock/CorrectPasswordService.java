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
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLException;

/**
 * Created by rhodey.
 */
public class CorrectPasswordService extends Service {

  private static final String TAG = "org.anhonesteffort.flock.CorrectPasswordService";

  private   static final String KEY_INTENT            = "CorrectPasswordService.KEY_INTENT";
  protected static final String KEY_MESSENGER         = "CorrectPasswordService.KEY_MESSENGER";
  protected static final String KEY_MASTER_PASSPHRASE = "CorrectPasswordService.KEY_OLD_MASTER_PASSPHRASE";
  protected static final String KEY_ACCOUNT           = "CorrectPasswordService.KEY_ACCOUNT";

  private Looper                     serviceLooper;
  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;

  private Messenger  messenger;
  private String     masterPassphrase;
  private DavAccount account;

  private int     resultCode;
  private boolean remoteActivityIsAlive = true;

  private void handleInitializeNotification() {
    Log.d(TAG, "handleInitializeNotification()");

    notificationBuilder.setContentTitle(getString(R.string.title_correct_sync_password))
        .setContentText(getString(R.string.updating_encryption_secrets))
        .setProgress(0, 0, true)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(9001, notificationBuilder.build());
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
        .setContentText(getString(R.string.error_login_unauthorized));

    notifyManager.notify(9001, notificationBuilder.build());
  }


  private void handleCorrectComplete() {
    Log.d(TAG, "handleCorrectComplete()");

    if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      stopForeground(true);
    else
      stopForeground(false);

    stopSelf();
  }

  private void handleUpdateMasterPassphrase(Bundle result, String masterPassphrase) {
    Log.d(TAG, "handleUpdateMasterPassphrase()");
    KeyStore.saveMasterPassphrase(getBaseContext(), masterPassphrase);

    try {

      Optional<String> encryptedKeyMaterial = KeyHelper.buildEncryptedKeyMaterial(getBaseContext());
      if (encryptedKeyMaterial.isPresent()) {
        KeyStore.saveEncryptedKeyMaterial(getBaseContext(), encryptedKeyMaterial.get());
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
      }

      else {
        Log.e(TAG, "unable to build encrypted key material");
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
      }

    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      Log.e(TAG, "caught exception while updating master passphrase", e);
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
    }
  }

  private void handleDavLogin(Bundle result, DavAccount account) {
    Log.d(TAG, "handleDavLogin()");

    if (DavAccountHelper.isUsingOurServers(account)) {
      try {

        String owsAuthToken = KeyUtil.getAuthTokenForPassphrase(masterPassphrase);
               account      = new DavAccount(account.getUserId(), owsAuthToken, account.getDavHostHREF());

      } catch (GeneralSecurityException e) {
        ErrorToaster.handleBundleError(e, result);
        return;
      }
    }

    try {

      if (DavAccountHelper.isAuthenticated(getBaseContext(), account)) {
        DavAccountHelper.setAccountPassword(getBaseContext(), account.getAuthToken());
        NotificationDrawer.cancelAuthNotification(getBaseContext());

        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
      }
      else
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_UNAUTHORIZED);

    } catch (PropertyParseException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (DavException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (SSLException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private void handleStartCorrectPassword() {
    Log.d(TAG, "handleStartCorrectPassword()");

    Bundle result = new Bundle();

    handleInitializeNotification();
    handleDavLogin(result, account);

    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
      if (DavAccountHelper.isUsingOurServers(account))
        handleUpdateMasterPassphrase(result, masterPassphrase);
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

    handleCorrectComplete();
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("CorrectPasswordService", HandlerThread.NORM_PRIORITY);
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
        if (intent.getExtras()                                  != null &&
            intent.getExtras().get(KEY_MESSENGER)               != null &&
            intent.getExtras().getString(KEY_MASTER_PASSPHRASE) != null &&
            intent.getExtras().getBundle(KEY_ACCOUNT)           != null)
        {
          if (!DavAccount.build(intent.getExtras().getBundle(KEY_ACCOUNT)).isPresent()) {
            Log.e(TAG, "received bad account bundle");
            return;
          }

          messenger        = (Messenger) intent.getExtras().get(KEY_MESSENGER);
          masterPassphrase = intent.getExtras().getString(KEY_MASTER_PASSPHRASE);
          account          = DavAccount.build(intent.getExtras().getBundle(KEY_ACCOUNT)).get();

          handleStartCorrectPassword();
        }
        else
          Log.e(TAG, "received intent without messenger, master passphrase or account");
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
