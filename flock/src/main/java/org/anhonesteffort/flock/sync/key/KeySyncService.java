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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.anhonesteffort.flock.CorrectEncryptionPasswordActivity;
import org.anhonesteffort.flock.R;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.sync.AbstractSyncAdapter;
import org.anhonesteffort.flock.sync.SyncWorker;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class KeySyncService extends Service {

  private static final int    ID_NOTIFICATION_CIPHER_PASSPHRASE = 1022;
  private static final String TAG = "org.anhonesteffort.flock.sync.key.KeySyncService";

  private static       KeySyncAdapter sSyncAdapter     = null;
  private static final Object         sSyncAdapterLock = new Object();

  @Override
  public void onCreate() {
    synchronized (sSyncAdapterLock) {
      if (sSyncAdapter == null)
        sSyncAdapter = new KeySyncAdapter(getApplicationContext());
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return sSyncAdapter.getSyncAdapterBinder();
  }

  private static class KeySyncAdapter extends AbstractSyncAdapter {

    public KeySyncAdapter(Context context) {
      super(context);
    }

    @Override
    protected KeySyncScheduler getSyncScheduler() {
      return new KeySyncScheduler(getContext());
    }

    @Override
    protected boolean localHasChanged() throws RemoteException {
      return false;
    }

    @Override
    protected void handlePreSyncOperations()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {

    }

    @Override
    protected List<SyncWorker> getSyncWorkers(boolean localChangesOnly)
        throws DavException, RemoteException, IOException
    {
      List<SyncWorker> workers = new LinkedList<SyncWorker>();
      workers.add(new KeySyncWorker(getContext(), davAccount, syncResult));

      return workers;
    }

    @Override
    protected void handlePostSyncOperations()
        throws PropertyParseException, InvalidMacException, DavException,
               RemoteException, GeneralSecurityException, IOException
    {

    }
  }

  public static void showCipherPassphraseInvalidNotification(Context context) {
    Log.w(TAG, "showCipherPassphraseInvalidNotification()");
    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);

    notificationBuilder.setContentTitle(context.getString(R.string.notification_flock_encryption_error));
    notificationBuilder.setContentText(context.getString(R.string.notification_tap_to_correct_encryption_password));
    notificationBuilder.setSmallIcon(R.drawable.alert_warning_light);
    notificationBuilder.setAutoCancel(true);

    Intent        clickIntent   = new Intent(context, CorrectEncryptionPasswordActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    notificationBuilder.setContentIntent(pendingIntent);

    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

    notificationManager.notify(ID_NOTIFICATION_CIPHER_PASSPHRASE, notificationBuilder.build());
  }

  public static void cancelCipherPassphraseNotification(Context context) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

    notificationManager.cancel(ID_NOTIFICATION_CIPHER_PASSPHRASE);
  }

}
