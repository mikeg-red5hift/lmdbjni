/**
 * Copyright (C) 2013, RedHat, Inc.
 *
 *    http://www.redhat.com/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.lmdbjni;


import org.fusesource.lmdbjni.EntryIterator.IteratorType;

import static org.fusesource.lmdbjni.JNI.*;
import static org.fusesource.lmdbjni.Util.checkArgNotNull;
import static org.fusesource.lmdbjni.Util.checkErrorCode;

/**
 * A database handle.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class Database extends NativeObject implements AutoCloseable {

  private final Env env;

  Database(Env env, long self) {
    super(self);
    this.env = env;
  }

  /**
   * <p>
   *  Close a database handle. Normally unnecessary.
   * </p>
   *
   * Use with care:
   *
   * This call is not mutex protected. Handles should only be closed by
   * a single thread, and only if no other threads are going to reference
   * the database handle or one of its cursors any further. Do not close
   * a handle if an existing transaction has modified its database.
   * Doing so can cause misbehavior from database corruption to errors
   * like MDB_BAD_VALSIZE (since the DB name is gone).
   */
  @Override
  public void close() {
    if (self != 0) {
      mdb_dbi_close(env.pointer(), self);
      self = 0;
    }
  }

  /**
   * @return Statistics for a database.
   */
  public MDB_stat stat() {
    Transaction tx = env.createTransaction(true);
    try {
      return stat(tx);
    } finally {
      tx.commit();
    }
  }

  public MDB_stat stat(Transaction tx) {
    checkArgNotNull(tx, "tx");
    MDB_stat rc = new MDB_stat();
    mdb_stat(tx.pointer(), pointer(), rc);
    return rc;
  }

  /**
   * @see org.fusesource.lmdbjni.Database#drop(Transaction, boolean)
   */
  public void drop(boolean delete) {
    Transaction tx = env.createTransaction();
    try {
      drop(tx, delete);
    } finally {
      tx.commit();
    }
  }

  /**
   * <p>
   *    Empty or delete+close a database.
   * </p>
   * @param tx transaction handle
   * @param delete false to empty the DB, true to delete it from the
   * environment and close the DB handle.
   */
  public void drop(Transaction tx, boolean delete) {
    checkArgNotNull(tx, "tx");
    mdb_drop(tx.pointer(), pointer(), delete ? 1 : 0);
    if (delete) {
      self = 0;
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#get(Transaction, byte[])
   */
  public int get(DirectBuffer key, DirectBuffer value) {
    checkArgNotNull(key, "key");
    Transaction tx = env.createTransaction(true);
    try {
      return get(tx, key, value);
    } finally {
      tx.commit();
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#get(Transaction, byte[])
   */
  public int get(Transaction tx, DirectBuffer key, DirectBuffer value) {
    checkArgNotNull(key, "key");
    checkArgNotNull(value, "value");
    long address = tx.getBufferAddress();
    Unsafe.putLong(address, 0, key.capacity());
    Unsafe.putLong(address, 1, key.addressOffset());

    int rc = mdb_get_address(tx.pointer(), pointer(), address, address + 2 * Unsafe.ADDRESS_SIZE);
    checkErrorCode(rc);
    int valSize = (int) Unsafe.getLong(address, 2);
    long valAddress = Unsafe.getAddress(address, 3);
    value.wrap(valAddress, valSize);
    return rc;
  }

  /**
   * @see org.fusesource.lmdbjni.Database#get(Transaction, byte[])
   */
  public byte[] get(byte[] key) {
    checkArgNotNull(key, "key");
    Transaction tx = env.createTransaction(true);
    try {
      return get(tx, key);
    } finally {
      tx.commit();
    }
  }

  /**
   * <p>
   *   Get items from a database.
   * </p>
   *
   * This function retrieves key/data pairs from the database. The address
   * and length of the data associated with the specified \b key are returned
   * in the structure to which \b data refers.
   * If the database supports duplicate keys (#MDB_DUPSORT) then the
   * first data item for the key will be returned. Retrieval of other
   * items requires the use of #mdb_cursor_get().
   *
   * @param tx transaction handle
   * @param key The key to search for in the database
   * @return The data corresponding to the key or null if not found
   */
  public byte[] get(Transaction tx, byte[] key) {
    checkArgNotNull(tx, "tx");
    checkArgNotNull(key, "key");
    NativeBuffer keyBuffer = NativeBuffer.create(key);
    try {
      return get(tx, keyBuffer);
    } finally {
      keyBuffer.delete();
    }
  }

  private byte[] get(Transaction tx, NativeBuffer keyBuffer) {
    return get(tx, new Value(keyBuffer));
  }

  private byte[] get(Transaction tx, Value key) {
    Value value = new Value();
    int rc = mdb_get(tx.pointer(), pointer(), key, value);
    if (rc == MDB_NOTFOUND) {
      return null;
    }
    checkErrorCode(rc);
    return value.toByteArray();
  }

  /**
   * <p>
   *   Creates a forward sequential iterator and a transaction
   *   starting at first key greater than or equal to specified key.
   * </p>
   *
   * The transaction is closed along with the iterator.
   *
   * @return a closable iterator handle.
   */
  public EntryIterator seek(byte[] key) {
    return iterate(key, IteratorType.FORWARD);
  }

  /**
   * <p>
   *   Creates a backward sequential iterator and a transaction
   *   starting at first key greater than or equal to specified key.
   * </p>
   *
   * The transaction is closed along with the iterator.
   *
   * @return a closable iterator handle.
   */
  public EntryIterator seekBackward(byte[] key) {
    return iterate(key, IteratorType.BACKWARD);
  }

  /**
   * <p>
   *   Creates a forward sequential iterator and a read transaction.
   * </p>
   *
   * The transaction is closed along with the iterator.
   *
   * @return a closable iterator handle.
   */
  public EntryIterator iterate() {
    return iterate(null, IteratorType.FORWARD);
  }
  /**
   * <p>
   *   Creates a backward sequential iterator and a read transaction.
   * </p>
   *
   * The transaction is closed along with the iterator.
   *
   * @return a closable iterator handle.
   */
  public EntryIterator iterateBackward() {
    return iterate(null, IteratorType.BACKWARD);
  }

  private EntryIterator iterate(byte[] key, IteratorType type) {
    Transaction tx = env.createTransaction(true);
    Cursor cursor = openCursor(tx);
    return new EntryIterator(cursor, tx, key, type);
  }

  /**
   * <p>
   *   Creates a cursor and a read only transaction for doing zero copy seeking.
   * </p>
   *
   * Key and value buffers are updated as the cursor moves. The transaction
   * is closed along with the cursor.
   *
   * @param key A DirectBuffer must be backed by a direct ByteBuffer.
   * @param value A DirectBuffer must be backed by a direct ByteBuffer.
   * @return a closable cursor handle.
   */
  public BufferCursor bufferCursor(DirectBuffer key, DirectBuffer value) {
    Transaction tx = env.createTransaction(true);
    Cursor cursor = openCursor(tx);
    return new BufferCursor(cursor, tx, key, value);
  }

  /**
   * <p>
   *   Creates a cursor and a read only transaction for doing zero copy seeking.
   * </p>
   *
   * Key and value buffers are updated as the cursor moves. The transaction
   * is closed along with the cursor.
   *
   * @return a closable cursor handle.
   */
  public BufferCursor bufferCursor() {
    Transaction tx = env.createTransaction(true);
    Cursor cursor = openCursor(tx);
    return new BufferCursor(cursor, tx, 1024);
  }

  /**
   * <p>
   *   Creates a cursor and a write transaction for doing zero copy seeking
   *   and writing.
   * </p>
   *
   * Key and value buffers are updated as the cursor moves. The transaction
   * is closed along with the cursor.
   *
   * @param key A DirectBuffer must be backed by a direct ByteBuffer.
   * @param value A DirectBuffer must be backed by a direct ByteBuffer.
   * @return a closable cursor handle.
   */
  public BufferCursor bufferCursorWriter(DirectBuffer key, DirectBuffer value) {
    Transaction tx = env.createTransaction(false);
    Cursor cursor = openCursor(tx);
    return new BufferCursor(cursor, tx, key, value);
  }

  /**
   * <p>
   *   Creates a cursor and a write transaction for doing zero copy seeking
   *   and writing.
   * </p>
   *
   * Key and value buffers are updated as the cursor moves. The transaction
   * is closed along with the cursor.
   *
   * @return a closable cursor handle.
   */
  public BufferCursor bufferCursorWriter() {
    Transaction tx = env.createTransaction(false);
    Cursor cursor = openCursor(tx);
    return new BufferCursor(cursor, tx, 1024);
  }

  /**
   * @see org.fusesource.lmdbjni.Database#put(Transaction, byte[], byte[], int)
   */
  public int put(DirectBuffer key, DirectBuffer value) {
    return put(key, value, 0);
  }

  /**
   * @see org.fusesource.lmdbjni.Database#put(Transaction, byte[], byte[], int)
   */
  public int put(DirectBuffer key, DirectBuffer value, int flags) {
    checkArgNotNull(key, "key");
    Transaction tx = env.createTransaction();
    try {
      return put(tx, key, value, flags);
    } finally {
      tx.commit();
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#put(Transaction, byte[], byte[], int)
   */
  public void put(Transaction tx, DirectBuffer key, DirectBuffer value) {
    put(tx, key, value, 0);
  }

  /**
   * @see org.fusesource.lmdbjni.Database#put(Transaction, byte[], byte[], int)
   */
  public int put(Transaction tx, DirectBuffer key, DirectBuffer value, int flags) {
    checkArgNotNull(key, "key");
    checkArgNotNull(value, "value");
    long address = tx.getBufferAddress();
    Unsafe.putLong(address, 0, key.capacity());
    Unsafe.putLong(address, 1, key.addressOffset());
    Unsafe.putLong(address, 2, value.capacity());
    Unsafe.putLong(address, 3, value.addressOffset());

    int rc = mdb_put_address(tx.pointer(), pointer(), address, address + 2 * Unsafe.ADDRESS_SIZE, flags);
    checkErrorCode(rc);
    return rc;
  }

  /**
   * @see org.fusesource.lmdbjni.Database#put(Transaction, byte[], byte[], int)
   */
  public byte[] put(byte[] key, byte[] value) {
    return put(key, value, 0);
  }

  /**
   * @see org.fusesource.lmdbjni.Database#put(Transaction, byte[], byte[], int)
   */
  public byte[] put(byte[] key, byte[] value, int flags) {
    checkArgNotNull(key, "key");
    Transaction tx = env.createTransaction();
    try {
      return put(tx, key, value, flags);
    } finally {
      tx.commit();
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#put(Transaction, byte[], byte[], int)
   */
  public byte[] put(Transaction tx, byte[] key, byte[] value) {
    return put(tx, key, value, 0);
  }

  /**
   * <p>
   * Store items into a database.
   * </p>
   *
   * This function stores key/data pairs in the database. The default behavior
   * is to enter the new key/data pair, replacing any previously existing key
   * if duplicates are disallowed, or adding a duplicate data item if
   * duplicates are allowed (#MDB_DUPSORT).
   *
   * @param tx transaction handle
   * @param key The key to store in the database
   * @param value The value to store in the database
   * @param flags Special options for this operation. This parameter
   * must be set to 0 or by bitwise OR'ing together one or more of the
   * values described here.
   * <ul>
   *	<li>#MDB_NODUPDATA - enter the new key/data pair only if it does not
   *		already appear in the database. This flag may only be specified
   *		if the database was opened with #MDB_DUPSORT. The function will
   *		return #MDB_KEYEXIST if the key/data pair already appears in the
   *		database.
   *	<li>#MDB_NOOVERWRITE - enter the new key/data pair only if the key
   *		does not already appear in the database. The function will return
   *		#MDB_KEYEXIST if the key already appears in the database, even if
   *		the database supports duplicates (#MDB_DUPSORT). The \b data
   *		parameter will be set to point to the existing item.
   *	<li>#MDB_RESERVE - reserve space for data of the given size, but
   *		don't copy the given data. Instead, return a pointer to the
   *		reserved space, which the caller can fill in later - before
   *		the next update operation or the transaction ends. This saves
   *		an extra memcpy if the data is being generated later.
   *		LMDB does nothing else with this memory, the caller is expected
   *		to modify all of the space requested.
   *	<li>#MDB_APPEND - append the given key/data pair to the end of the
   *		database. No key comparisons are performed. This option allows
   *		fast bulk loading when keys are already known to be in the
   *		correct order. Loading unsorted keys with this flag will cause
   *		data corruption.
   *	<li>#MDB_APPENDDUP - as above, but for sorted dup data.
   * </ul>
   *
   * @return the existing value if it was a dup insert attempt.
   */
  public byte[] put(Transaction tx, byte[] key, byte[] value, int flags) {
    checkArgNotNull(tx, "tx");
    checkArgNotNull(key, "key");
    checkArgNotNull(value, "value");
    NativeBuffer keyBuffer = NativeBuffer.create(key);
    try {
      NativeBuffer valueBuffer = NativeBuffer.create(value);
      try {
        return put(tx, keyBuffer, valueBuffer, flags);
      } finally {
        valueBuffer.delete();
      }
    } finally {
      keyBuffer.delete();
    }
  }

  private byte[] put(Transaction tx, NativeBuffer keyBuffer, NativeBuffer valueBuffer, int flags) {
    return put(tx, new Value(keyBuffer), new Value(valueBuffer), flags);
  }

  private byte[] put(Transaction tx, Value keySlice, Value valueSlice, int flags) {
    int rc = mdb_put(tx.pointer(), pointer(), keySlice, valueSlice, flags);
    if ((flags & MDB_NOOVERWRITE) != 0 && rc == MDB_KEYEXIST) {
      // Return the existing value if it was a dup insert attempt.
      return valueSlice.toByteArray();
    } else {
      // If the put failed, throw an exception..
      checkErrorCode(rc);
      return null;
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#delete(Transaction, byte[], byte[])
   */
  public boolean delete(DirectBuffer key) {
    return delete(key, null);
  }

  /**
   * @see org.fusesource.lmdbjni.Database#delete(Transaction, byte[], byte[])
   */
  public boolean delete(Transaction tx, DirectBuffer key) {
    checkArgNotNull(key, "key");
    try {
      return delete(tx, key, null);
    } finally {
      tx.commit();
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#delete(Transaction, byte[], byte[])
   */
  public boolean delete(DirectBuffer key, DirectBuffer value) {
    checkArgNotNull(key, "key");
    Transaction tx = env.createTransaction();
    try {
      return delete(tx, key, value);
    } finally {
      tx.commit();
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#delete(Transaction, byte[], byte[])
   */
  public boolean delete(Transaction tx, DirectBuffer key, DirectBuffer value) {
    byte[] keyBytes = new byte[key.capacity()];
    byte[] valueBytes = null;
    key.getBytes(0, keyBytes);
    if (value != null) {
      valueBytes = new byte[value.capacity()];
      value.getBytes(0, valueBytes);
    }
    return delete(tx, keyBytes, valueBytes);
  }

  /**
   * @see org.fusesource.lmdbjni.Database#delete(Transaction, byte[], byte[])
   */
  public boolean delete(byte[] key) {
    return delete(key, null);
  }

  /**
   * @see org.fusesource.lmdbjni.Database#delete(Transaction, byte[], byte[])
   */
  public boolean delete(byte[] key, byte[] value) {
    checkArgNotNull(key, "key");
    Transaction tx = env.createTransaction();
    try {
      return delete(tx, key, value);
    } finally {
      tx.commit();
    }
  }

  /**
   * @see org.fusesource.lmdbjni.Database#delete(Transaction, byte[], byte[])
   */
  public boolean delete(Transaction tx, byte[] key) {
    checkArgNotNull(key, "key");
    return delete(tx, key, null);
  }

  /**
   * <p>
   * Removes key/data pairs from the database.
   * </p>
   * If the database does not support sorted duplicate data items
   * (#MDB_DUPSORT) the value parameter is ignored.
   * If the database supports sorted duplicates and the data parameter
   * is NULL, all of the duplicate data items for the key will be
   * deleted. Otherwise, if the data parameter is non-NULL
   * only the matching data item will be deleted.
   * This function will return false if the specified key/data
   * pair is not in the database.
   *
   * @param tx Transaction handle.
   * @param key The key to delete from the database.
   * @param value The value to delete from the database
   * @return true if the key/value was deleted.
   */
  public boolean delete(Transaction tx, byte[] key, byte[] value) {
    checkArgNotNull(tx, "tx");
    checkArgNotNull(key, "key");
    NativeBuffer keyBuffer = NativeBuffer.create(key);
    try {
      NativeBuffer valueBuffer = NativeBuffer.create(value);
      try {
        return delete(tx, keyBuffer, valueBuffer);
      } finally {
        if (valueBuffer != null) {
          valueBuffer.delete();
        }
      }
    } finally {
      keyBuffer.delete();
    }
  }

  private boolean delete(Transaction tx, NativeBuffer keyBuffer, NativeBuffer valueBuffer) {
    return delete(tx, new Value(keyBuffer), Value.create(valueBuffer));
  }

  private boolean delete(Transaction tx, Value keySlice, Value valueSlice) {
    int rc = mdb_del(tx.pointer(), pointer(), keySlice, valueSlice);
    if (rc == MDB_NOTFOUND) {
      return false;
    }
    checkErrorCode(rc);
    return true;
  }

  /**
   * <p>
   *  Create a cursor handle.
   * </p>
   *
   * A cursor is associated with a specific transaction and database.
   * A cursor cannot be used when its database handle is closed.  Nor
   * when its transaction has ended, except with #mdb_cursor_renew().
   * It can be discarded with #mdb_cursor_close().
   * A cursor in a write-transaction can be closed before its transaction
   * ends, and will otherwise be closed when its transaction ends.
   * A cursor in a read-only transaction must be closed explicitly, before
   * or after its transaction ends. It can be reused with
   * #mdb_cursor_renew() before finally closing it.
   * @note Earlier documentation said that cursors in every transaction
   * were closed when the transaction committed or aborted.
   *
   * @param tx transaction handle
   * @return Address where the new #MDB_cursor handle will be stored
   * @return cursor handle
   */
  public Cursor openCursor(Transaction tx) {
    long cursor[] = new long[1];
    checkErrorCode(mdb_cursor_open(tx.pointer(), pointer(), cursor));
    return new Cursor(cursor[0]);
  }

}
