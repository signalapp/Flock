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

import com.google.common.base.Optional;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyUtil;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.addressbook.LocalAddressbookStore;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavStore;
import org.anhonesteffort.flock.sync.calendar.LocalCalendarStore;
import org.anhonesteffort.flock.sync.key.DavKeyStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.net.ssl.SSLException;

/**
 * Created by rhodey
 */
public class ImportOwsAccountService extends ImportAccountService {

  private static final String TAG = "org.anhonesteffort.flock.ImportOwsAccountService";

  private   static final String KEY_INTENT            = "ImportOwsAccountService.KEY_INTENT";
  protected static final String KEY_MESSENGER         = "ImportOwsAccountService.KEY_MESSENGER";
  protected static final String KEY_ACCOUNT_ID        = "ImportOwsAccountService.KEY_ACCOUNT_ID";
  protected static final String KEY_MASTER_PASSPHRASE = "ImportOwsAccountService.KEY_OLD_MASTER_PASSPHRASE";

  private Looper                     serviceLooper;
  private ServiceHandler             serviceHandler;
  private NotificationManager        notifyManager;
  private NotificationCompat.Builder notificationBuilder;

  private Messenger  messenger;
  private String     accountId;
  private String     masterPassphrase;
  private DavAccount importAccount;

  private int     resultCode;
  private boolean remoteActivityIsAlive = true;
  private boolean accountWasImported    = false;

  private void handleInitializeNotification() {
    Log.d(TAG, "handleInitializeNotification()");

    notificationBuilder.setContentTitle(getString(R.string.title_import_account))
        .setContentText(getString(R.string.importing_contacts_and_calendars))
        .setProgress(0, 0, true)
        .setSmallIcon(R.drawable.flock_actionbar_icon);

    startForeground(9003, notificationBuilder.build());
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
          .setContentText(getString(R.string.account_import_failed));
    }

    notifyManager.notify(9003, notificationBuilder.build());
  }


  private void handleImportComplete() {
    Log.d(TAG, "handleImportComplete()");

    if (accountWasImported && resultCode != ErrorToaster.CODE_SUCCESS)
      stopForeground(false);
    else if (remoteActivityIsAlive || resultCode == ErrorToaster.CODE_SUCCESS)
      stopForeground(true);
    else
      stopForeground(false);

    stopSelf();
  }

  private void handleDavLogin(Bundle result) {
    Log.d(TAG, "handleDavLogin()");

    try {

      String authToken     = KeyUtil.getAuthTokenForPassphrase(masterPassphrase);
             importAccount = new DavAccount(accountId, authToken, OwsWebDav.HREF_WEBDAV_HOST);

      DavAccountHelper.setAccountDavHREF(getBaseContext(), importAccount.getDavHostHREF());

      if (DavAccountHelper.isAuthenticated(getBaseContext(), importAccount)) {
        if (DavAccountHelper.isExpired(getBaseContext(), importAccount))
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUBSCRIPTION_EXPIRED);
        else
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
      }
      else
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_UNAUTHORIZED);

    } catch (DavException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (PropertyParseException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private void handleImportAddressbook() {
    Log.d(TAG, "handleImportAddressbook()");

    LocalAddressbookStore localStore  = new LocalAddressbookStore(getBaseContext(), importAccount);
    String                remotePath  = OwsWebDav.getAddressbookPathForUsername(importAccount.getUserId());
    String                displayName = getString(R.string.addressbook);

    if (localStore.getCollections().size() == 0) {
      localStore.addCollection(remotePath, displayName);
      new AddressbookSyncScheduler(getBaseContext()).requestSync();
    }
  }

  private List<HidingCalDavCollection> handleRemoveKeyCollection(List<HidingCalDavCollection> collections) {
    Optional<HidingCalDavCollection> keyCollection = Optional.absent();
    for (HidingCalDavCollection collection : collections) {
      if (collection.getPath().contains(DavKeyStore.PATH_KEY_COLLECTION))
        keyCollection = Optional.of(collection);
    }
    if (keyCollection.isPresent())
      collections.remove(keyCollection.get());

    return collections;
  }

  private void handleImportCalendars(Bundle result) {
    Log.d(TAG, "handleImportCalendars()");

    try {

      Optional<MasterCipher> masterCipher = KeyHelper.getMasterCipher(getBaseContext());
      if (!masterCipher.isPresent()) {
        Log.e(TAG, "master cipher is missing at handleImportCalendars()");
        result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_CRYPTO_ERROR);
        return;
      }

      LocalCalendarStore localStore  = new LocalCalendarStore(getBaseContext(), importAccount.getOsAccount());
      HidingCalDavStore  remoteStore = DavAccountHelper.getHidingCalDavStore(getBaseContext(), importAccount, masterCipher.get());

      for (HidingCalDavCollection collection : handleRemoveKeyCollection(remoteStore.getCollections())) {
        Optional<String>  displayName = collection.getHiddenDisplayName();
        Optional<Integer> color       = collection.getHiddenColor();

        if (displayName.isPresent()) {
          if (color.isPresent())
            localStore.addCollection(collection.getPath(), displayName.get(), color.get());
          else
            localStore.addCollection(collection.getPath(), displayName.get());
        }
        else
          localStore.addCollection(collection.getPath());
      }

      remoteStore.releaseConnections();
      new CalendarsSyncScheduler(getBaseContext()).requestSync();
      result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

    } catch (PropertyParseException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (DavException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (InvalidMacException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (RemoteException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (GeneralSecurityException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (SSLException e) {
      ErrorToaster.handleBundleError(e, result);
    } catch (IOException e) {
      ErrorToaster.handleBundleError(e, result);
    }
  }

  private void handleUiCallbackSubscriptionExpired() {
    Log.d(TAG, "handleUiCallbackSubscriptionExpired()");

    Message message      = Message.obtain();
            message.arg1 = ErrorToaster.CODE_SUBSCRIPTION_EXPIRED;
            message.setData(importAccount.toBundle());

    try {

      messenger.send(message);

    } catch (RemoteException e) {
      Log.e(TAG, "caught exception while sending message to activity >> ", e);
      remoteActivityIsAlive = false;
    }
  }

  private void handleUiCallbackAccountImported() {
    Log.d(TAG, "handleUiCallbackAccountImported()");

    Message message      = Message.obtain();
            message.arg1 = ImportOwsAccountFragment.CODE_ACCOUNT_IMPORTED;

    try {

      messenger.send(message);

    } catch (RemoteException e) {
      Log.e(TAG, "caught exception while sending message to activity >> ", e);
      remoteActivityIsAlive = false;
    }
  }

  private void handleStartImportOwsAccount() {
    Log.d(TAG, "handleStartImportOwsAccount()");

    Bundle result = new Bundle();
    handleInitializeNotification();

    handleDavLogin(result);
    if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {

      handleImportAccount(result, importAccount, masterPassphrase);
      if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {

        accountWasImported = true;
        handleUiCallbackAccountImported();
        handleImportAddressbook();
        handleImportCalendars(result);
      }
    }

    else if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUBSCRIPTION_EXPIRED) {
      resultCode = result.getInt(ErrorToaster.KEY_STATUS_CODE);

      handleUiCallbackSubscriptionExpired();
      handleImportComplete();
      return;
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
    HandlerThread thread = new HandlerThread("ImportOwsAccountService", HandlerThread.NORM_PRIORITY);
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

          handleStartImportOwsAccount();
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
