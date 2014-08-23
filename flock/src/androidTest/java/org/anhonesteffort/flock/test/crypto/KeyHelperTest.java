package org.anhonesteffort.flock.test.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;

import com.google.common.base.Optional;

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

  private static final String HACK_PREFERENCES_NAME = "org.anhonesteffort.flock.crypto.KeyStore";

  private Context context;

  @Override
  protected void setUp() throws Exception {
    context = this.getContext();
  }

  private static void saveBytes(Context context, String key, byte[] value) {
    SharedPreferences        settings  = context.getSharedPreferences(HACK_PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
    SharedPreferences.Editor editor    = settings.edit();

    editor.putString(key, Base64.encodeBytes(value));
    editor.commit();
  }

  private static void saveString(Context context, String key, String value) {
    SharedPreferences        settings = context.getSharedPreferences(HACK_PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
    SharedPreferences.Editor editor   = settings.edit();

    editor.putString(key, value);
    editor.commit();
  }

  private static Optional<byte[]> retrieveBytes(Context context, String key) throws IOException {
    SharedPreferences settings     = context.getSharedPreferences(HACK_PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
    String            encodedValue = settings.getString(key, null);

    if (encodedValue == null)
      return Optional.absent();

    return Optional.of(Base64.decode(encodedValue));
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

    Optional<byte[]> resultCipherKeyBytes = retrieveBytes(context, "KEY_CIPHER_KEY");
    Optional<byte[]> resultMacKeyBytes    = retrieveBytes(context, "KEY_MAC_KEY");
    Optional<byte[]> resultSaltBytes      = retrieveBytes(context, "KEY_KEY_MATERIAL_SALT");

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

    saveBytes(context, "KEY_CIPHER_KEY", cipherKeyBytes);
    saveBytes(context, "KEY_MAC_KEY",    macKeyBytes);

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

    saveBytes(context,  "KEY_CIPHER_KEY",            cipherKeyBytes);
    saveBytes(context,  "KEY_MAC_KEY",               macKeyBytes);
    saveBytes(context,  "KEY_KEY_MATERIAL_SALT",     saltBytes);
    saveString(context, "KEY_OLD_MASTER_PASSPHRASE", masterPassphrase);

    Optional<String> encodedSalt                  = KeyHelper.buildEncodedSalt(context);
    Optional<String> encryptedKeyMaterial         = KeyHelper.buildEncryptedKeyMaterial(context);
    String[]         saltAndEncryptedKeyMaterial  = new String[] {
        encodedSalt.get(),
        encryptedKeyMaterial.get()
    };

    KeyStore.invalidateKeyMaterial(context);
    saveString(context, "KEY_OLD_MASTER_PASSPHRASE", masterPassphrase);

    KeyHelper.importSaltAndEncryptedKeyMaterial(context, saltAndEncryptedKeyMaterial);

    Optional<byte[]> resultCipherKeyBytes = retrieveBytes(context, "KEY_CIPHER_KEY");
    Optional<byte[]> resultMacKeyBytes    = retrieveBytes(context, "KEY_MAC_KEY");
    Optional<byte[]> resultSaltBytes      = retrieveBytes(context, "KEY_KEY_MATERIAL_SALT");

    assertTrue("KeyHelper can export and import encrypted key material.",
               Arrays.equals(resultCipherKeyBytes.get(), cipherKeyBytes) &&
               Arrays.equals(resultMacKeyBytes.get(),    macKeyBytes)    &&
               Arrays.equals(resultSaltBytes.get(),      saltBytes));
  }
}
