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

package org.anhonesteffort.flock.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.util.Base64;

import java.io.IOException;

/**
 * Programmer: rhodey
 */
public class KeyStore {

  private static final String TAG = "org.anhonesteffort.flock.crypto.KeyStore";

  private static final String PREFERENCES_NAME            = "org.anhonesteffort.flock.crypto.KeyStore";
  private static final String KEY_USE_CIPHER_VERSION_ZERO = "KEY_USE_CIPHER_VERSION_ZERO";
  private static final String KEY_MASTER_PASSPHRASE       = "KEY_OLD_MASTER_PASSPHRASE";
  private static final String KEY_CIPHER_KEY              = "KEY_CIPHER_KEY";
  private static final String KEY_MAC_KEY                 = "KEY_MAC_KEY";
  private static final String KEY_KEY_MATERIAL_SALT       = "KEY_KEY_MATERIAL_SALT";
  private static final String KEY_ENCRYPTED_KEY_MATERIAL  = "KEY_ENCRYPTED_KEY_MATERIAL";

  protected static boolean getUseCipherVersionZero(Context context) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
    return settings.getBoolean(KEY_USE_CIPHER_VERSION_ZERO, false);
  }

  public static void setUseCipherVersionZero(Context context, boolean useCipherVersionZero) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
    settings.edit().putBoolean(KEY_USE_CIPHER_VERSION_ZERO, useCipherVersionZero).commit();
  }

  protected static void saveCipherKey(Context context, byte[] cipherKey) {
    Log.d(TAG, "SAVING CIPHER KEY MATERIAL...");
    saveBytes(context, KEY_CIPHER_KEY, cipherKey);
  }

  protected static Optional<byte[]> getCipherKey(Context context) throws IOException {
    return retrieveBytes(context, KEY_CIPHER_KEY);
  }

  protected static void saveMacKey(Context context, byte[] cipherKey) {
    Log.d(TAG, "SAVING MAC KEY MATERIAL...");
    saveBytes(context, KEY_MAC_KEY, cipherKey);
  }

  protected static Optional<byte[]> getMacKey(Context context) throws IOException {
    return retrieveBytes(context, KEY_MAC_KEY);
  }

  protected static void saveKeyMaterialSalt(Context context, byte[] salt) {
    Log.d(TAG, "SAVING SALT FOR KEY MATERIAL...");
    saveBytes(context, KEY_KEY_MATERIAL_SALT, salt);
  }

  protected static Optional<byte[]> getKeyMaterialSalt(Context context) throws IOException {
    return retrieveBytes(context, KEY_KEY_MATERIAL_SALT);
  }

  public static void saveMasterPassphrase(Context context, String passphrase) {
    Log.d(TAG, "SAVING MASTER PASSPHRASE...");
    saveString(context, KEY_MASTER_PASSPHRASE, passphrase);
  }

  public static Optional<String> getMasterPassphrase(Context context) {
    return retrieveString(context, KEY_MASTER_PASSPHRASE);
  }

  public static void saveEncryptedKeyMaterial(Context context, String encryptedKeyMaterial) {
    Log.d(TAG, "SAVING ENCRYPTED KEY MATERIAL...");
    saveString(context, KEY_ENCRYPTED_KEY_MATERIAL, encryptedKeyMaterial);
  }

  public static Optional<String> getEncryptedKeyMaterial(Context context) {
    return retrieveString(context, KEY_ENCRYPTED_KEY_MATERIAL);
  }

  public static void invalidateKeyMaterial(Context context) {
    Log.w(TAG, "INVALIDATING ALL KEY MATERIAL...");
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME,
                                                              Context.MODE_MULTI_PROCESS);

    settings.edit().remove(KEY_CIPHER_KEY).commit();
    settings.edit().remove(KEY_MAC_KEY).commit();
    settings.edit().remove(KEY_KEY_MATERIAL_SALT).commit();
    settings.edit().remove(KEY_MASTER_PASSPHRASE).commit();
  }

  private static void saveBytes(Context context, String key, byte[] value) {
    SharedPreferences        settings = context.getSharedPreferences(PREFERENCES_NAME,
                                                              Context.MODE_MULTI_PROCESS);
    SharedPreferences.Editor editor   = settings.edit();

    editor.putString(key, Base64.encodeBytes(value));
    editor.commit();
  }

  private static void saveString(Context context, String key, String value) {
    SharedPreferences        settings = context.getSharedPreferences(PREFERENCES_NAME,
                                                                     Context.MODE_MULTI_PROCESS);
    SharedPreferences.Editor editor   = settings.edit();

    editor.putString(key, value);
    editor.commit();
  }

  private static Optional<byte[]> retrieveBytes(Context context, String key) throws IOException {
    SharedPreferences settings     = context.getSharedPreferences(PREFERENCES_NAME,
                                                                  Context.MODE_MULTI_PROCESS);
    String            encodedValue = settings.getString(key, null);

    if (encodedValue == null)
      return Optional.absent();

    return Optional.of(Base64.decode(encodedValue));
  }

  private static Optional<String> retrieveString(Context context, String key) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME,
                                                              Context.MODE_MULTI_PROCESS);
    return Optional.fromNullable(settings.getString(key, null));
  }

}
