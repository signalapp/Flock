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
import org.anhonesteffort.flock.crypto.KeyUtil;
import org.anhonesteffort.flock.registration.RegistrationApi;
import org.anhonesteffort.flock.registration.RegistrationApiException;
import org.anhonesteffort.flock.registration.ResourceAlreadyExistsException;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLException;

/**
 * Created by rhodey
 */
public class RegisterAccountService extends ImportAccountService {

  private static final String TAG = "org.anhonesteffort.flock.RegisterAccountService";

  private   static final String KEY_INTENT            = "RegisterAccountService.KEY_INTENT";
  protected static final String KEY_MESSENGER         = "RegisterAccountService.KEY_MESSENGER";
  protected static final String KEY_ACCOUNT_ID        = "RegisterAccountService.KEY_ACCOUNT_ID";
  protected static final String KEY_MASTER_PASSPHRASE = "RegisterAccountService.KEY_OLD_MASTER_PASSPHRASE";

  private Looper                     serviceLooper;
  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;

  private Messenger  messenger;
  private String     accountId;
  private String     masterPassphrase;
  private DavAccount registerAcount;

  private int     resultCode;
  private boolean remoteActivityIsAlive = true;
  private boolean accountWasImported    = false;

  private void handleInitializeNotification() {
    Log.d(TAG, "handleInitializeNotification()");

    notificationBuilder.setContentTitle(getString(R.string.title_register_account))
        .setContentText(getString(R.string.registering_account))
        .setProgress(0, 0, true)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(9005, notificationBuilder.build());
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy()");

    if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      return;

    Bundle errorBundler = new Bundle();
    errorBundler.putInt(ErrorToaster.KEY_STATUS_CODE,             resultCode);
    ErrorToaster.handleDisplayToastBundledError(getBaseContext(), errorBundler);

    if (accountWasImported) {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentText(getString(R.string.account_import_completed_with_errors));
    }
    else {
      notificationBuilder
          .setProgress(0, 0, false)
          .setContentText(getString(R.string.account_register_failed));
    }

    notifyManager.notify(9005, notificationBuilder.build());
  }


  private void handleImportComplete() {
    Log.d(TAG, "handleImportComplete()");

    if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      stopForeground(true);
    else
      stopForeground(false);

    stopSelf();
  }

  private void handleRegisterAccount(Bundle result) {
    Log.d(TAG, "handleRegisterAccount()");

    try {

      String          authToken       = KeyUtil.getAuthTokenForPassphrase(masterPassphrase);
      RegistrationApi registrationApi = new RegistrationApi(getBaseContext());
                      registerAcount  = new DavAccount(accountId, authToken, OwsWebDav.HREF_WEBDAV_HOST);

      registrationApi.createAccount(registerAcount);
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

    } catch (ResourceAlreadyExistsException e) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_ACCOUNT_ID_TAKEN);
    } catch (RegistrationApiException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private void handleImportAddressbook() {
    LocalAddressbookStore localStore  = new LocalAddressbookStore(getBaseContext(), registerAcount);
    String                remotePath  = OwsWebDav.getAddressbookPathForUsername(registerAcount.getUserId());
    String                displayName = getString(R.string.addressbook);

    if (localStore.getCollections().size() == 0) {
      localStore.addCollection(remotePath, displayName);
      new AddressbookSyncScheduler(getBaseContext()).requestSync();
    }
  }

  private void handleDeleteDefaultCalendars(Bundle result) {
    try {

      CalDavStore store = DavAccountHelper.getCalDavStore(getBaseContext(), registerAcount);
      for (CalDavCollection collection : store.getCollections()) {
        if (!collection.getPath().contains(DavKeyStore.PATH_KEY_COLLECTION)) {
          Log.d(TAG, "deleting default calendar " + collection.getPath());
          store.removeCollection(collection.getPath());
        }
      }

      store.closeHttpConnection();
      new CalendarsSyncScheduler(getBaseContext()).requestSync();
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

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

  private void handleRevertRegisterAccount(Bundle result) {
    Log.w(TAG, "handleRevertRegisterAccount()");

    int statusSave = result.getInt(ErrorToaster.KEY_STATUS_CODE);

    try {

      new RegistrationApi(getBaseContext()).deleteAccount(registerAcount);
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

    } catch (ResourceAlreadyExistsException e) {
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_ACCOUNT_ID_TAKEN);
    } catch (RegistrationApiException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }

    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) != ErrorToaster.CODE_SUCCESS)
      Log.e(TAG, "unable to revert registed account!!! XXX :(");

    result.putInt(ErrorToaster.KEY_STATUS_CODE, statusSave);
  }

  private void handleUiCallbackAccountImported() {
    Log.d(TAG, "handleUiCallbackAccountImported()");

    Message message      = Message.obtain();
            message.arg1 = RegisterAccountFragment.CODE_ACCOUNT_IMPORTED;

    try {

      messenger.send(message);

    } catch (RemoteException e) {
      Log.e(TAG, "caught exception while sending message to activity >> ", e);
      remoteActivityIsAlive = false;
    }
  }

  private void handleStartRegisterAccount() {
    Log.d(TAG, "handleStartRegisterAccount()");

    Bundle result = new Bundle();
    handleInitializeNotification();

    handleRegisterAccount(result);
    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {

      handleImportAccount(result, registerAcount, masterPassphrase);
      if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
        accountWasImported = true;
        handleUiCallbackAccountImported();

        handleImportAddressbook();
        handleDeleteDefaultCalendars(result);
      }
      else
        handleRevertRegisterAccount(result);
    }

    Message message      = Message.obtain();
            message.arg1 = result.getInt(ErrorToaster.KEY_STATUS_CODE);
            resultCode   = result.getInt(ErrorToaster.KEY_STATUS_CODE);

    try {

      if (!accountWasImported)
        messenger.send(message);

    } catch (RemoteException e) {
      Log.e(TAG, "caught exception while sending message to activity >> ", e);
      remoteActivityIsAlive = false;
    }

    handleImportComplete();
  }

  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("RegisterAccountService", HandlerThread.NORM_PRIORITY);
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
            intent.getExtras().getString(KEY_ACCOUNT_ID)        != null &&
            intent.getExtras().getString(KEY_MASTER_PASSPHRASE) != null)
        {
          messenger        = (Messenger) intent.getExtras().get(KEY_MESSENGER);
          accountId        = intent.getExtras().getString(KEY_ACCOUNT_ID);
          masterPassphrase = intent.getExtras().getString(KEY_MASTER_PASSPHRASE);

          handleStartRegisterAccount();
        }
        else
          Log.e(TAG, "received intent without messenger, account id or master passphrase");
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
