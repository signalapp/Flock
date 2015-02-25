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

package org.anhonesteffort.flock.sync;

import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.OperationApplicationException;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

/**
 * rhodey
 */
public class ContentProviderOperationQueue {

  private static final Integer MAX_QUEUE_SIZE       = 100;
  private static final Long    MAX_QUEUE_SIZE_BYTES = 512000L;

  private final ContentProviderClient               client;
  private final ArrayList<ContentProviderOperation> operations;
  private       Long                                queueSizeBytes;

  public ContentProviderOperationQueue(ContentProviderClient client) {
    this.client    = client;
    operations     = new ArrayList<>();
    queueSizeBytes = 0L;
  }

  public int size() {
    return operations.size();
  }

  public boolean hasSpace() {
    return size() < MAX_QUEUE_SIZE && queueSizeBytes < MAX_QUEUE_SIZE_BYTES;
  }

  public void queue(ContentProviderOperation operation, Integer operationSizeBytes) {
    operations.add(operation);
    queueSizeBytes += operationSizeBytes;
  }

  public void queue(ContentProviderOperation operation) {
    queue(operation, 0);
  }

  public void queueAll(List<ContentProviderOperation> operationList, Integer operationsSizeBytes) {
    operations.addAll(operationList);
    queueSizeBytes += operationsSizeBytes;
  }

  public void queueAll(List<ContentProviderOperation> operationList) {
    queueAll(operationList, 0);
  }

  public int commit() throws OperationApplicationException, RemoteException {
    ContentProviderResult[] result = new ContentProviderResult[0];

    try {

      if (!operations.isEmpty())
        result = client.applyBatch(operations);

    } finally {
      operations.clear();
      queueSizeBytes = 0L;
    }

    return result.length;
  }

}
