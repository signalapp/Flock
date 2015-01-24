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

import org.anhonesteffort.flock.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * rhodey
 *
 * NOTE: THIS WAS A MISTAKE, I BROUGHT THIS UPON US AND NOW WE HAVE TO LIVE WITH IT D;
 */
public class HidingUtil {

  private static final byte[] PREFIX_ENCRYPTED_DATA = new byte[] {0x23, 0x23, 0x23, 0x24, 0x24, 0x24};

  private static boolean hasEncryptedDataPrefix(byte[] data) {
    if (data.length <= PREFIX_ENCRYPTED_DATA.length)
      return false;

    boolean matches = true;
    for (int i = 0; i < PREFIX_ENCRYPTED_DATA.length; i++) {
      if (data[i] != PREFIX_ENCRYPTED_DATA[i])
        matches = false;
    }

    return matches;
  }

  public static byte[] encryptEncodeAndPrefix(MasterCipher masterCipher, byte[] data)
      throws IOException, GeneralSecurityException
  {
    return Util.combine(PREFIX_ENCRYPTED_DATA, masterCipher.encryptAndEncode(data));
  }

  public static String encryptEncodeAndPrefix(MasterCipher masterCipher, String data)
      throws IOException, GeneralSecurityException
  {
    return new String(encryptEncodeAndPrefix(masterCipher, data.getBytes()));
  }

  public static byte[] decodeAndDecryptIfNecessary(MasterCipher masterCipher, byte[] data)
      throws InvalidMacException, IOException, GeneralSecurityException
  {
    if (!hasEncryptedDataPrefix(data))
      return data;

    byte[] encodedIvCiphertextAndMac = Arrays.copyOfRange(data, PREFIX_ENCRYPTED_DATA.length, data.length);

    return masterCipher.decodeAndDecrypt(encodedIvCiphertextAndMac);
  }

  public static String decodeAndDecryptIfNecessary(MasterCipher masterCipher, String data)
      throws InvalidMacException, IOException, GeneralSecurityException
  {
    return new String(decodeAndDecryptIfNecessary(masterCipher, data.getBytes()));
  }

}
