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
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import org.anhonesteffort.flock.CorrectEncryptionPasswordActivity;
import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.R;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection;
import org.anhonesteffort.flock.sync.calendar.HidingCalDavStore;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Programmer: rhodey
 */
public class KeySyncUtil {

  private static final String TAG                               = "org.anhonesteffort.flock.sync.key.KeySyncUtil";
  private static final int    ID_NOTIFICATION_CIPHER_PASSPHRASE = 1022;
  public  static final String PATH_KEY_COLLECTION               = "key-material/";

  private static Optional<HidingCalDavCollection> getKeyCollection(Context    context,
                                                                   DavAccount account)
      throws PropertyParseException, DavException, IOException
  {
    HidingCalDavStore store           = DavAccountHelper.getHidingCalDavStore(context, account, null);
    Optional<String>  calendarHomeSet = store.getCalendarHomeSet();
    if (calendarHomeSet.isPresent())
      return store.getCollection(calendarHomeSet.get().concat(PATH_KEY_COLLECTION));

    return Optional.absent();
  }

  public static HidingCalDavCollection getOrCreateKeyCollection(Context    context,
                                                                DavAccount account)
      throws PropertyParseException, DavException, GeneralSecurityException, IOException
  {
    Log.d(TAG, "getOrCreateKeyCollection()");

    Optional<HidingCalDavCollection> keyCollection = getKeyCollection(context, account);
    if (keyCollection.isPresent())
      return keyCollection.get();

    HidingCalDavStore store           = DavAccountHelper.getHidingCalDavStore(context, account, null);
    Optional<String>  calendarHomeSet = store.getCalendarHomeSet();

    if (!calendarHomeSet.isPresent())
      throw new PropertyParseException("No calendar-home-set property found for user.",
                                       store.getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

    Log.d(TAG, "creating key collection");
    store.addCollection(calendarHomeSet.get().concat(KeySyncUtil.PATH_KEY_COLLECTION));
    keyCollection = store.getCollection(calendarHomeSet.get().concat(KeySyncUtil.PATH_KEY_COLLECTION));

    if (!keyCollection.isPresent())
      throw new DavException(500, "WebDAV server did not create our key collection!");

    return keyCollection.get();
  }

  public static Optional<String[]> getSaltAndEncryptedKeyMaterial(Context    context,
                                                                  DavAccount account)
      throws PropertyParseException, DavException, IOException
  {
    String[] saltAndEncryptedKeyMaterial = {"", ""};

    Optional<HidingCalDavCollection> keyCollection = getKeyCollection(context, account);
    if (keyCollection.isPresent()) {
      if (keyCollection.get().getKeyMaterialSalt().isPresent() &&
          keyCollection.get().getEncryptedKeyMaterial().isPresent())
      {
        saltAndEncryptedKeyMaterial[0] = keyCollection.get().getKeyMaterialSalt().get();
        saltAndEncryptedKeyMaterial[1] = keyCollection.get().getEncryptedKeyMaterial().get();

        keyCollection.get().getStore().closeHttpConnection();
        return Optional.of(saltAndEncryptedKeyMaterial);
      }
      keyCollection.get().getStore().closeHttpConnection();
      return Optional.absent();
    }

    return Optional.absent();
  }

  public static void showCipherPassphraseInvalidNotification(Context context) {
    Log.w(TAG, "showCipherPassphraseInvalidNotification()");
    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);

    notificationBuilder.setContentTitle(context.getString(R.string.notification_flock_encryption_error));
    notificationBuilder.setContentText(context.getString(R.string.notification_tap_to_correct_encryption_password));
    notificationBuilder.setSmallIcon(R.drawable.alert_warning_light);
    notificationBuilder.setAutoCancel(true);

    Intent clickIntent   = new Intent(context, CorrectEncryptionPasswordActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(context,
                                                            0,
                                                            clickIntent,
                                                            PendingIntent.FLAG_UPDATE_CURRENT);
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
