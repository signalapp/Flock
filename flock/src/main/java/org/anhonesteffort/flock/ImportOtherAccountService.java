package org.anhonesteffort.flock;

import android.app.NotificationManager;
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

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;

/**
 * Created by rhodey
 */
public class ImportOtherAccountService extends ImportAccountService {

  private static final String TAG = "org.anhonesteffort.flock.ImportOtherAccountService";

  private   static final String KEY_INTENT            = "ImportOtherAccountService.KEY_INTENT";
  protected static final String KEY_MESSENGER         = "ImportOtherAccountService.KEY_MESSENGER";
  protected static final String KEY_ACCOUNT           = "ImportOtherAccountService.KEY_ACCOUNT";
  protected static final String KEY_MASTER_PASSPHRASE = "ImportOtherAccountService.KEY_MASTER_PASSPHRASE";

  private Looper                     serviceLooper;
  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;

  private Messenger  messenger;
  private String     masterPassphrase;
  private DavAccount importAccount;

  private int     resultCode;
  private boolean remoteActivityIsAlive = true;

  private void handleInitializeNotification() {
    Log.d(TAG, "handleInitializeNotification()");

    notificationBuilder.setContentTitle(getString(R.string.title_import_account))
        .setContentText(getString(R.string.importing_encryption_secrets))
        .setProgress(0, 0, true)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(9004, notificationBuilder.build());
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");

    if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      return;

    Bundle errorBundler = new Bundle();
    errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE, resultCode);
    ErrorToaster.handleDisplayToastBundledError(getBaseContext(), errorBundler);

    notificationBuilder
        .setProgress(0, 0, false)
        .setContentText(getString(R.string.account_import_failed));

    notifyManager.notify(9004, notificationBuilder.build());
  }


  private void handleImportComplete() {
    Log.d(TAG, "handleImportComplete()");

    if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      stopForeground(true);
    else
      stopForeground(false);

    stopSelf();
  }

  private void handleDavLogin(Bundle result) {
    Log.d(TAG, "handleDavLogin()");

    try {

      if (DavAccountHelper.isAuthenticated(getBaseContext(), importAccount))
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
      else
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_UNAUTHORIZED);

    } catch (DavException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (PropertyParseException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private void handleStartImportOtherAccount() {
    Log.d(TAG, "handleStartImportOtherAccount()");

    Bundle result = new Bundle();
    handleInitializeNotification();

    handleDavLogin(result);
    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS)
      handleImportAccount(result, importAccount, masterPassphrase);

    Message message      = Message.obtain();
            message.arg1 = result.getInt(ErrorToaster.KEY_STATUS_CODE);
            resultCode   = result.getInt(ErrorToaster.KEY_STATUS_CODE);

    try {

      messenger.send(message);

    } catch (RemoteException e) {
      Log.e(TAG, "caught exception while sending message to activity >> ", e);
      remoteActivityIsAlive = false;
    }

    handleImportComplete();
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("ImportOtherAccountService", HandlerThread.NORM_PRIORITY);
    thread.start();

    serviceLooper  = thread.getLooper();
    serviceHandler = new ServiceHandler(serviceLooper);

    notifyManager       = (NotificationManager) getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);
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
            intent.getExtras().getBundle(KEY_ACCOUNT)           != null &&
            intent.getExtras().getString(KEY_MASTER_PASSPHRASE) != null)
        {
          if (!DavAccount.build(intent.getExtras().getBundle(KEY_ACCOUNT)).isPresent()) {
            Log.e(TAG, "received bad account bundle");
            return;
          }

          messenger        = (Messenger) intent.getExtras().get(KEY_MESSENGER);
          importAccount    = DavAccount.build(intent.getExtras().getBundle(KEY_ACCOUNT)).get();
          masterPassphrase = intent.getExtras().getString(KEY_MASTER_PASSPHRASE);

          handleStartImportOtherAccount();
        }
        else
          Log.e(TAG, "received intent without messenger, account or master passphrase");
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
