/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hudi.table;

import org.apache.hudi.client.WriteStatus;
import org.apache.hudi.common.engine.HoodieReaderContext;
import org.apache.hudi.common.model.CompactionOperation;
import org.apache.hudi.common.model.HoodieBaseFile;
import org.apache.hudi.common.model.HoodieRecord;
import org.apache.hudi.common.table.log.block.HoodieLogBlock;
import org.apache.hudi.config.HoodieWriteConfig;
import org.apache.hudi.exception.HoodieNotSupportedException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Interface for insert and update operations in compaction.
 *
 * @param <T> HoodieRecordPayload type.
 */
public interface HoodieCompactionHandler<T> {
  Iterator<List<WriteStatus>> handleUpdate(String instantTime, String partitionPath, String fileId,
                                           Map<String, HoodieRecord<T>> keyToNewRecords, HoodieBaseFile oldDataFile) throws IOException;

  Iterator<List<WriteStatus>> handleInsert(String instantTime, String partitionPath, String fileId,
                                           Map<String, HoodieRecord<?>> recordMap);

  default List<WriteStatus> compactUsingFileGroupReader(String instantTime,
                                                        CompactionOperation operation,
                                                        HoodieWriteConfig writeConfig,
                                                        HoodieReaderContext readerContext) {
    throw new HoodieNotSupportedException("This engine does not support file group reader based compaction.");
  }

  default Iterator<List<WriteStatus>> handleInsertsForLogCompaction(String instantTime, String partitionPath, String fileId,
                                                           Map<String, HoodieRecord<?>> recordMap,
                                                           Map<HoodieLogBlock.HeaderMetadataType, String> header) {
    throw new HoodieNotSupportedException("Operation is not yet supported");
  }
}
