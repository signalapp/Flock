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

import android.util.Log;

import org.anhonesteffort.flock.util.Base64;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Programmer: rhodey
 */
public class KeyUtil {

  private static final String TAG = "org.anhonesteffort.flock.crypto.KeyUtil";

  protected static final int CIPHER_KEY_LENGTH_BYTES = 32;
  protected static final int MAC_KEY_LENGTH_BYTES    = 32;
  protected static final int SALT_LENGTH_BYTES       =  8;

  private static final int ITERATION_COUNT_AUTH_TOKEN   = 20050;
  private static final int ITERATION_COUNT_KEY_MATERIAL = 20000;

  protected static byte[] generateCipherKey() throws NoSuchAlgorithmException {
    Log.d(TAG, "generateCipherKey()");

    byte[] cipherKey = new byte[CIPHER_KEY_LENGTH_BYTES];
    SecureRandom.getInstance("SHA1PRNG").nextBytes(cipherKey);
    return cipherKey;
  }

  protected static byte[] generateMacKey() throws NoSuchAlgorithmException {
    Log.d(TAG, "generateMacKey()");

    byte[] macKey = new byte[MAC_KEY_LENGTH_BYTES];
    SecureRandom.getInstance("SHA1PRNG").nextBytes(macKey);

    return macKey;
  }

  protected static byte[] generateSalt() throws NoSuchAlgorithmException {
    Log.d(TAG, "generateSalt()");

    byte[] salt = new byte[SALT_LENGTH_BYTES];
    SecureRandom.getInstance("SHA1PRNG").nextBytes(salt);

    return salt;
  }

  public static String getAuthTokenForPassphrase(String passphrase)
      throws GeneralSecurityException
  {
    byte[]    salt    = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    SecretKey authKey = getKeyForPassphrase(passphrase, salt, ITERATION_COUNT_AUTH_TOKEN);
    return Base64.encodeBytes(authKey.getEncoded());
  }

  protected static SecretKey[] getCipherAndMacKeysForPassphrase(byte[] salt, String passphrase)
      throws GeneralSecurityException
  {
    SecretKey combinedKeys = getKeyForPassphrase(passphrase, salt, ITERATION_COUNT_KEY_MATERIAL);

    byte[] cipherKeyBytes = Arrays.copyOfRange(combinedKeys.getEncoded(), 0, CIPHER_KEY_LENGTH_BYTES);
    byte[] macKeyBytes    = Arrays.copyOfRange(combinedKeys.getEncoded(),
                                               CIPHER_KEY_LENGTH_BYTES,
                                               CIPHER_KEY_LENGTH_BYTES + MAC_KEY_LENGTH_BYTES);

    SecretKey cipherKey = new SecretKeySpec(cipherKeyBytes, combinedKeys.getAlgorithm());
    SecretKey macKey    = new SecretKeySpec(macKeyBytes,    combinedKeys.getAlgorithm());

    return new SecretKey[] {cipherKey, macKey};
  }

  private static SecretKey getKeyForPassphrase(String passphrase, byte[] salt, int iterationCount)
      throws GeneralSecurityException
  {
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
    PBEKeySpec       keySpec    = new PBEKeySpec(passphrase.toCharArray(), salt, iterationCount, (64 * 8));
    return keyFactory.generateSecret(keySpec);
  }

}
