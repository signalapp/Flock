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

import org.anhonesteffort.flock.util.Base64;
import org.anhonesteffort.flock.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Programmer: rhodey
 */
public class MasterCipher {

  protected static final int MAC_LENGTH_BYTES = 32;
  protected static final int IV_LENGTH_BYTES  = 16;

  private final SecretKey cipherKey;
  private final SecretKey macKey;

  protected MasterCipher(SecretKey cipherKey, SecretKey macKey) {
    this.cipherKey = cipherKey;
    this.macKey    = macKey;
  }

  public byte[] encryptAndEncode(byte[] data)
      throws IOException, GeneralSecurityException
  {
    Cipher    encryptingCipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
    encryptingCipher.init(Cipher.ENCRYPT_MODE, cipherKey);

    Mac       hmac   = Mac.getInstance("HmacSHA256");
    hmac.init(macKey);

    byte[] iv         = encryptingCipher.getIV();
    byte[] ciphertext = encryptingCipher.doFinal(data);
    byte[] mac        = hmac.doFinal(Util.combine(iv, ciphertext));

    return Base64.encodeBytesToBytes(Util.combine(iv, ciphertext, mac));
  }

  public String encryptAndEncode(String data)
      throws IOException, GeneralSecurityException
  {
    return new String(encryptAndEncode(data.getBytes()));
  }

  public byte[] decodeAndDecrypt(byte[] encodedIvCiphertextAndMac)
      throws InvalidMacException, IOException, GeneralSecurityException
  {
    byte[] ivCiphertextAndMac = Base64.decode(encodedIvCiphertextAndMac);
    if (ivCiphertextAndMac.length <= (IV_LENGTH_BYTES + MAC_LENGTH_BYTES))
      throw new GeneralSecurityException("invalid length on decoded iv, ciphertext and mac");

    byte[] iv         = Arrays.copyOfRange(ivCiphertextAndMac, 0, IV_LENGTH_BYTES);
    byte[] ciphertext = Arrays.copyOfRange(ivCiphertextAndMac,
                                           IV_LENGTH_BYTES,
                                           ivCiphertextAndMac.length - MAC_LENGTH_BYTES);
    byte[] mac        = Arrays.copyOfRange(ivCiphertextAndMac,
                                           ivCiphertextAndMac.length - MAC_LENGTH_BYTES,
                                           ivCiphertextAndMac.length);

    Cipher          decryptingCipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
    IvParameterSpec ivSpec           = new IvParameterSpec(iv);
    decryptingCipher.init(Cipher.DECRYPT_MODE, cipherKey, ivSpec);

    Mac       hmac   = Mac.getInstance("HmacSHA256");
    hmac.init(macKey);

    verifyMac(hmac, Util.combine(iv, ciphertext), mac);

    return decryptingCipher.doFinal(ciphertext);
  }

  public String decodeAndDecrypt(String data)
      throws InvalidMacException, IOException, GeneralSecurityException
  {
    return new String(decodeAndDecrypt(data.getBytes()));
  }

  protected static void verifyMac(Mac hmac, byte[] theirData, byte[] theirMac)
      throws InvalidMacException
  {
    byte[] ourMac = hmac.doFinal(theirData);

    if (!MessageDigest.isEqual(theirMac, ourMac))
      throw new InvalidMacException("INVALID MAC");
  }

}
