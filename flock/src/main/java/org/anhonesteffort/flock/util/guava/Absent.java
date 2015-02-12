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

package org.anhonesteffort.flock.util.guava;

import static org.anhonesteffort.flock.util.guava.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Set;

/**
 * Implementation of an {@link Optional} not containing a reference.
 */

final class Absent extends Optional<Object> {
  static final Absent INSTANCE = new Absent();

  @Override public boolean isPresent() {
    return false;
  }

  @Override public Object get() {
    throw new IllegalStateException("value is absent");
  }

  @Override public Object or(Object defaultValue) {
    return checkNotNull(defaultValue, "use orNull() instead of or(null)");
  }

  @SuppressWarnings("unchecked") // safe covariant cast
  @Override public Optional<Object> or(Optional<?> secondChoice) {
    return (Optional) checkNotNull(secondChoice);
  }

  @Override public Object or(Supplier<?> supplier) {
    return checkNotNull(supplier.get(),
        "use orNull() instead of a Supplier that returns null");
  }

  @Override public Object orNull() {
    return null;
  }

  @Override public Set<Object> asSet() {
    return Collections.emptySet();
  }

  @Override
  public <V> Optional<V> transform(Function<? super Object, V> function) {
    checkNotNull(function);
    return Optional.absent();
  }

  @Override public boolean equals(Object object) {
    return object == this;
  }

  @Override public int hashCode() {
    return 0x598df91c;
  }

  @Override public String toString() {
    return "Optional.absent()";
  }

  private Object readResolve() {
    return INSTANCE;
  }

  private static final long serialVersionUID = 0;
}
