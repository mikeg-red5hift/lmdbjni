package org.fusesource.lmdbjni;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

public class Unsafe {
  public static final sun.misc.Unsafe UNSAFE;
  public static final int ADDRESS_SIZE;
  public static final long ARRAY_BASE_OFFSET;
  static {
    try {
      final PrivilegedExceptionAction<sun.misc.Unsafe> action = new PrivilegedExceptionAction<sun.misc.Unsafe>() {
        public sun.misc.Unsafe run() throws Exception {
          final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
          field.setAccessible(true);
          return (sun.misc.Unsafe) field.get(null);
        }
      };

      UNSAFE = AccessController.doPrivileged(action);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
    ADDRESS_SIZE = UNSAFE.addressSize();
    ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
  }


  public static void getBytes(long address, int index, byte[] key) {
    UNSAFE.copyMemory(null, address + index, key, ARRAY_BASE_OFFSET, key.length);
  }
}