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

import org.anhonesteffort.flock.crypto.HidingUtil;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.test.FlockInstrumentationTestCase;

import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * rhodey
 */
public class HidingUtilTest extends FlockInstrumentationTestCase {

  private final byte[] PLAINTEXT_STUFF = "plaintext stuff".getBytes();

  private MasterCipher masterCipher;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    masterCipher = mock(MasterCipher.class);

    when(masterCipher.encryptAndEncode(any(byte[].class))).thenReturn(PLAINTEXT_STUFF);
    when(masterCipher.decodeAndDecrypt(any(byte[].class))).thenReturn(PLAINTEXT_STUFF);
  }

  public void testEncryptAndDecrypt() throws Exception {
    final byte[] ciphertext  = HidingUtil.encryptEncodeAndPrefix(masterCipher, PLAINTEXT_STUFF);
    final byte[] plaintext   = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, ciphertext);

    assertTrue(Arrays.equals(PLAINTEXT_STUFF, plaintext));
  }

  public void testDecryptNonEncrypted() throws Exception {
    final byte[] plaintext = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, PLAINTEXT_STUFF);

    assertTrue(Arrays.equals(PLAINTEXT_STUFF, plaintext));
  }

}
