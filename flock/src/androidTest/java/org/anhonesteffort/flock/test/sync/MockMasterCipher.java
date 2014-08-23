package org.anhonesteffort.flock.test.sync;

import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.util.Base64;

import java.io.IOException;

/**
 * Programmer: rhodey
 * Date: 3/21/14
 */

// This class is meant to assist in the testing of Hiding*Store and Hiding*Collection.
public class MockMasterCipher extends MasterCipher {

  public MockMasterCipher() {
    super(false, null, null);
  }

  @Override
  public byte[] encryptAndEncode(byte[] data) {
    return Base64.encodeBytes(data).getBytes();
  }

  @Override
  public String encryptAndEncode(String data) {
    return Base64.encodeBytes(data.getBytes());
  }

  @Override
  public byte[] decodeAndDecrypt(byte[] encodedVersionIvCiphertextAndMac) {
    return Base64.decode(encodedVersionIvCiphertextAndMac);
  }

  public String decodeAndDecrypt(String data) throws IOException {
    return new String(Base64.decode(data));
  }
}
