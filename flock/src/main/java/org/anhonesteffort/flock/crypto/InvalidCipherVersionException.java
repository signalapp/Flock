package org.anhonesteffort.flock.crypto;

import java.security.GeneralSecurityException;

/**
 * rhodey
 */
public class InvalidCipherVersionException extends GeneralSecurityException {

  public InvalidCipherVersionException(String message) {
    super(message);
  }

}
