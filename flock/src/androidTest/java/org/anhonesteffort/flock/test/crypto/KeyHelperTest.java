/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.anhonesteffort.flock.test.crypto;

import android.content.Context;
import android.test.AndroidTestCase;

import org.anhonesteffort.flock.util.guava.Optional;

import org.anhonesteffort.flock.crypto.KeyHelper;
import org.anhonesteffort.flock.crypto.KeyStore;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.util.Base64;
import org.anhonesteffort.flock.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * rhodey.
 */
public class KeyHelperTest extends AndroidTestCase {

  private Context context;

  @Override
  protected void setUp() throws Exception {
    context = this.getContext();
  }

  private byte[] encryptAndEncode(byte[] iv, SecretKey cipherKey, SecretKey macKey, byte[] data)
      throws IOException, GeneralSecurityException
  {
    IvParameterSpec ivSpec           = new IvParameterSpec(iv);
    Cipher          encryptingCipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
    Mac             hmac             = Mac.getInstance("HmacSHA256");

    encryptingCipher.init(Cipher.ENCRYPT_MODE, cipherKey, ivSpec);
    hmac.init(macKey);

    byte[] ciphertext = encryptingCipher.doFinal(data);
    byte[] mac        = hmac.doFinal(Util.combine(new byte[]{MasterCipher.CURRENT_CIPHER_VERSION}, iv, ciphertext));

    return Base64.encodeBytesToBytes(Util.combine(new byte[]{MasterCipher.CURRENT_CIPHER_VERSION}, iv, ciphertext, mac));
  }

  public void testKeyHelperGenerateKeyMaterial() throws Exception {
    KeyHelper.generateAndSaveSaltAndKeyMaterial(context);

    Optional<byte[]> resultCipherKeyBytes = KeyStore.getCipherKey(context);
    Optional<byte[]> resultMacKeyBytes    = KeyStore.getMacKey(context);
    Optional<byte[]> resultSaltBytes      = KeyStore.getKeyMaterialSalt(context);

    assertTrue("KeyHelper can generate key material.",
               resultCipherKeyBytes.get().length > 0 &&
               resultMacKeyBytes.get().length    > 0 &&
               resultSaltBytes.get().length      > 0 &&
               !Arrays.equals(resultCipherKeyBytes.get(), resultMacKeyBytes.get()) &&
               !Arrays.equals(resultCipherKeyBytes.get(), resultSaltBytes.get())   &&
               !Arrays.equals(resultMacKeyBytes.get(),    resultSaltBytes.get()));
  }

  public void testKeyHelperMasterCipher() throws Exception {
    final byte[] cipherKeyBytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    final byte[] macKeyBytes    = new byte[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    final byte[] plaintext      = new byte[] {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};

    final SecretKey testCipherKey = new SecretKeySpec(cipherKeyBytes, "AES");
    final SecretKey testMacKey    = new SecretKeySpec(macKeyBytes,    "SHA256");

    KeyStore.saveCipherKey(context, cipherKeyBytes);
    KeyStore.saveMacKey(context, macKeyBytes);

    byte[] encodedKeyHelperResult = KeyHelper.getMasterCipher(context).get().encryptAndEncode(plaintext);
    byte[] keyHelperResult        = Base64.decode(encodedKeyHelperResult);
    byte[] keyHelperIv            = Arrays.copyOfRange(keyHelperResult, 1, 1 + 16);

    byte[] encodedTestResult = encryptAndEncode(keyHelperIv, testCipherKey, testMacKey, plaintext);
    byte[] testResult        = Base64.decode(encodedTestResult);

    assertTrue("KeyHelper's MasterCipher works.",
               new String(testResult).equals(new String(keyHelperResult)));
  }

  public void testBuildAndImportEncryptedKeyMaterial() throws Exception {
    final String masterPassphrase = "oioioi";
    final byte[] cipherKeyBytes   = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    final byte[] macKeyBytes      = new byte[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    final byte[] saltBytes        = new byte[] {2, 2, 2, 2, 2, 2, 2, 2};

    KeyStore.saveCipherKey(context, cipherKeyBytes);
    KeyStore.saveMacKey(context, macKeyBytes);
    KeyStore.saveKeyMaterialSalt(context, saltBytes);
    KeyStore.saveMasterPassphrase(context, masterPassphrase);

    Optional<String> encodedSalt                  = KeyHelper.buildEncodedSalt(context);
    Optional<String> encryptedKeyMaterial         = KeyHelper.buildEncryptedKeyMaterial(context);
    String[]         saltAndEncryptedKeyMaterial  = new String[] {
        encodedSalt.get(),
        encryptedKeyMaterial.get()
    };

    KeyStore.invalidateKeyMaterial(context);
    KeyStore.saveMasterPassphrase(context, masterPassphrase);

    KeyHelper.importSaltAndEncryptedKeyMaterial(context, saltAndEncryptedKeyMaterial);

    Optional<byte[]> resultCipherKeyBytes = KeyStore.getCipherKey(context);
    Optional<byte[]> resultMacKeyBytes    = KeyStore.getMacKey(context);
    Optional<byte[]> resultSaltBytes      = KeyStore.getKeyMaterialSalt(context);

    assertTrue("KeyHelper can export and import encrypted key material.",
               Arrays.equals(resultCipherKeyBytes.get(), cipherKeyBytes) &&
               Arrays.equals(resultMacKeyBytes.get(),    macKeyBytes)    &&
               Arrays.equals(resultSaltBytes.get(),      saltBytes));
  }
}
