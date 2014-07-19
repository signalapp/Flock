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
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.util.Base64;
import org.anhonesteffort.flock.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Programmer: rhodey
 */
public class KeyHelper {

  private static final String TAG = "org.anhonesteffort.flock.crypto.KeyHelper";

  public static void generateAndSaveSaltAndKeyMaterial(Context context)
      throws IOException, GeneralSecurityException
  {
    Log.d(TAG, "GENERATING SALT AND KEY MATERIAL!");
    byte[] cipherKey = KeyUtil.generateCipherKey();
    byte[] macKey    = KeyUtil.generateMacKey();
    byte[] salt      = KeyUtil.generateSalt();

    Log.d(TAG, "SAVING SALT AND KEY MATERIAL!");
    KeyStore.saveCipherKey(      context, cipherKey);
    KeyStore.saveMacKey(         context, macKey);
    KeyStore.saveKeyMaterialSalt(context, salt);

    Optional<String> encryptedKeyMaterial = buildEncryptedKeyMaterial(context);
    if (encryptedKeyMaterial.isPresent())
      KeyStore.saveEncryptedKeyMaterial(context, encryptedKeyMaterial.get());
  }

  public static Optional<MasterCipher> getMasterCipher(Context context) throws IOException {
    Optional<byte[]> cipherKeyBytes = KeyStore.getCipherKey(context);
    Optional<byte[]> macKeyBytes    = KeyStore.getMacKey(context);

    if (!cipherKeyBytes.isPresent() || !macKeyBytes.isPresent())
      return Optional.absent();

    SecretKey cipherKey = new SecretKeySpec(cipherKeyBytes.get(), "AES");
    SecretKey macKey    = new SecretKeySpec(cipherKeyBytes.get(), "SHA256");

    return Optional.of(new MasterCipher(cipherKey, macKey));
  }

  public static Optional<String> buildEncodedSalt(Context context) throws IOException {
    Optional<byte[]> salt = KeyStore.getKeyMaterialSalt(context);

    if (!salt.isPresent())
      return Optional.absent();

    return Optional.of(Base64.encodeBytes(salt.get()));
  }

  public static Optional<String> buildEncryptedKeyMaterial(Context context)
      throws IOException, GeneralSecurityException
  {
    Optional<byte[]> cipherKey        = KeyStore.getCipherKey(context);
    Optional<byte[]> macKey           = KeyStore.getMacKey(context);
    Optional<byte[]> salt             = KeyStore.getKeyMaterialSalt(context);
    Optional<String> masterPassphrase = KeyStore.getMasterPassphrase(context);

    if (!masterPassphrase.isPresent() || !cipherKey.isPresent() ||
        !macKey.isPresent() || !salt.isPresent())
      return Optional.absent();

    SecretKey[]  masterKeys      = KeyUtil.getCipherAndMacKeysForPassphrase(salt.get(), masterPassphrase.get());
    SecretKey    masterCipherKey = masterKeys[0];
    SecretKey    masterMacKey    = masterKeys[1];
    MasterCipher masterCipher    = new MasterCipher(masterCipherKey, masterMacKey);

    byte[] keyMaterial          = Util.combine(cipherKey.get(), macKey.get());
    byte[] encryptedKeyMaterial = masterCipher.encryptAndEncode(keyMaterial);

    return Optional.of(new String(encryptedKeyMaterial));
  }

  public static void importSaltAndEncryptedKeyMaterial(Context  context,
                                                       String[] saltAndEncryptedKeyMaterial)
      throws GeneralSecurityException, InvalidMacException, IOException
  {
    Log.d(TAG, "IMPORTING ENCRYPTED KEY MATERIAL!");

    Optional<String> masterPassphrase = KeyStore.getMasterPassphrase(context);
    if (!masterPassphrase.isPresent())
      throw new InvalidMacException("Passphrase unavailable.");

    byte[]       salt                 = Base64.decode(saltAndEncryptedKeyMaterial[0]);
    SecretKey[]  masterKeys           = KeyUtil.getCipherAndMacKeysForPassphrase(salt, masterPassphrase.get());
    SecretKey    masterCipherKey      = masterKeys[0];
    SecretKey    masterMacKey         = masterKeys[1];
    MasterCipher masterCipher         = new MasterCipher(masterCipherKey, masterMacKey);
    byte[]       plaintextKeyMaterial = masterCipher.decodeAndDecrypt(saltAndEncryptedKeyMaterial[1].getBytes());

    boolean saltLengthValid        = salt.length                 == KeyUtil.SALT_LENGTH_BYTES;
    boolean keyMaterialLengthValid = plaintextKeyMaterial.length == (KeyUtil.CIPHER_KEY_LENGTH_BYTES + KeyUtil.MAC_KEY_LENGTH_BYTES);

    if (!saltLengthValid || !keyMaterialLengthValid)
      throw new GeneralSecurityException("invalid length on salt or key material >> " +
                                         saltLengthValid + " " + keyMaterialLengthValid);

    byte[] plaintextCipherKey   = Arrays.copyOfRange(plaintextKeyMaterial, 0, KeyUtil.CIPHER_KEY_LENGTH_BYTES);
    byte[] plaintextMacKey      = Arrays.copyOfRange(plaintextKeyMaterial,
                                                     KeyUtil.CIPHER_KEY_LENGTH_BYTES,
                                                     plaintextCipherKey.length);

    KeyStore.saveEncryptedKeyMaterial(context, saltAndEncryptedKeyMaterial[1]);
    KeyStore.saveKeyMaterialSalt(     context, salt);
    KeyStore.saveCipherKey(           context, plaintextCipherKey);
    KeyStore.saveMacKey(              context, plaintextMacKey);
  }

  public static boolean masterPassphraseIsValid(Context context)
      throws GeneralSecurityException, IOException
  {
    Optional<String> masterPassphrase = KeyStore.getMasterPassphrase(context);
    if (!masterPassphrase.isPresent())
      return false;

    Optional<String> encryptedKeyMaterial = KeyStore.getEncryptedKeyMaterial(context);
    if (!encryptedKeyMaterial.isPresent())
      throw new GeneralSecurityException("Where did my key material go! XXX!!!!");

    Optional<byte[]> salt = KeyStore.getKeyMaterialSalt(context);
    if (!salt.isPresent())
      throw new GeneralSecurityException("Where did my salt go! XXX!!!!");

    SecretKey[]  masterKeys      = KeyUtil.getCipherAndMacKeysForPassphrase(salt.get(), masterPassphrase.get());
    SecretKey    masterCipherKey = masterKeys[0];
    SecretKey    masterMacKey    = masterKeys[1];
    MasterCipher masterCipher    = new MasterCipher(masterCipherKey, masterMacKey);

    try {

      masterCipher.decodeAndDecrypt(encryptedKeyMaterial.get());

    } catch (InvalidMacException e) {
      return false;
    }

    return true;
  }

}
