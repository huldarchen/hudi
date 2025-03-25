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

package org.apache.hudi.common.engine;

import org.apache.hudi.common.config.RecordMergeMode;
import org.apache.hudi.common.model.HoodieRecord;
import org.apache.hudi.common.model.HoodieRecordMerger;
import org.apache.hudi.common.table.read.FileGroupReaderSchemaHandler;
import org.apache.hudi.common.util.LocalAvroSchemaCache;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.common.util.collection.ClosableIterator;
import org.apache.hudi.storage.HoodieStorage;
import org.apache.hudi.storage.StoragePath;
import org.apache.hudi.storage.StoragePathInfo;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;

import javax.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.apache.hudi.common.model.HoodieRecord.DEFAULT_ORDERING_VALUE;
import static org.apache.hudi.common.model.HoodieRecord.RECORD_KEY_METADATA_FIELD;

/**
 * An abstract reader context class for {@code HoodieFileGroupReader} to use, containing APIs for
 * engine-specific implementation on reading data files, getting field values from a record,
 * transforming a record, etc.
 * <p>
 * For each query engine, this class should be extended and plugged into {@code HoodieFileGroupReader}
 * to realize the file group reading.
 *
 * @param <T> The type of engine-specific record representation, e.g.,{@code InternalRow} in Spark
 *            and {@code RowData} in Flink.
 */
public abstract class HoodieReaderContext<T> implements Closeable {

  private FileGroupReaderSchemaHandler<T> schemaHandler = null;
  private String tablePath = null;
  private String latestCommitTime = null;
  private Option<HoodieRecordMerger> recordMerger = null;
  private Boolean hasLogFiles = null;
  private Boolean hasBootstrapBaseFile = null;
  private Boolean needsBootstrapMerge = null;
  private Boolean shouldMergeUseRecordPosition = null;

  // for encoding and decoding schemas to the spillable map
  private final LocalAvroSchemaCache localAvroSchemaCache = LocalAvroSchemaCache.getInstance();

  // Getter and Setter for schemaHandler
  public FileGroupReaderSchemaHandler<T> getSchemaHandler() {
    return schemaHandler;
  }

  public void setSchemaHandler(FileGroupReaderSchemaHandler<T> schemaHandler) {
    this.schemaHandler = schemaHandler;
  }

  public String getTablePath() {
    if (tablePath == null) {
      throw new IllegalStateException("Table path not set in reader context.");
    }
    return tablePath;
  }

  public void setTablePath(String tablePath) {
    this.tablePath = tablePath;
  }

  public String getLatestCommitTime() {
    return latestCommitTime;
  }

  public void setLatestCommitTime(String latestCommitTime) {
    this.latestCommitTime = latestCommitTime;
  }

  public Option<HoodieRecordMerger> getRecordMerger() {
    return recordMerger;
  }

  public void setRecordMerger(Option<HoodieRecordMerger> recordMerger) {
    this.recordMerger = recordMerger;
  }

  // Getter and Setter for hasLogFiles
  public boolean getHasLogFiles() {
    return hasLogFiles;
  }

  public void setHasLogFiles(boolean hasLogFiles) {
    this.hasLogFiles = hasLogFiles;
  }

  // Getter and Setter for hasBootstrapBaseFile
  public boolean getHasBootstrapBaseFile() {
    return hasBootstrapBaseFile;
  }

  public void setHasBootstrapBaseFile(boolean hasBootstrapBaseFile) {
    this.hasBootstrapBaseFile = hasBootstrapBaseFile;
  }

  // Getter and Setter for needsBootstrapMerge
  public boolean getNeedsBootstrapMerge() {
    return needsBootstrapMerge;
  }

  public void setNeedsBootstrapMerge(boolean needsBootstrapMerge) {
    this.needsBootstrapMerge = needsBootstrapMerge;
  }

  // Getter and Setter for useRecordPosition
  public boolean getShouldMergeUseRecordPosition() {
    return shouldMergeUseRecordPosition;
  }

  public void setShouldMergeUseRecordPosition(boolean shouldMergeUseRecordPosition) {
    this.shouldMergeUseRecordPosition = shouldMergeUseRecordPosition;
  }

  // These internal key names are only used in memory for record metadata and merging,
  // and should not be persisted to storage.
  public static final String INTERNAL_META_RECORD_KEY = "_0";
  public static final String INTERNAL_META_PARTITION_PATH = "_1";
  public static final String INTERNAL_META_ORDERING_FIELD = "_2";
  public static final String INTERNAL_META_OPERATION = "_3";
  public static final String INTERNAL_META_INSTANT_TIME = "_4";
  public static final String INTERNAL_META_SCHEMA_ID = "_5";

  /**
   * Gets the record iterator based on the type of engine-specific record representation from the
   * file.
   *
   * @param filePath       {@link StoragePath} instance of a file.
   * @param start          Starting byte to start reading.
   * @param length         Bytes to read.
   * @param dataSchema     Schema of records in the file in {@link Schema}.
   * @param requiredSchema Schema containing required fields to read in {@link Schema} for projection.
   * @param storage        {@link HoodieStorage} for reading records.
   * @return {@link ClosableIterator<T>} that can return all records through iteration.
   */
  public abstract ClosableIterator<T> getFileRecordIterator(
      StoragePath filePath, long start, long length, Schema dataSchema, Schema requiredSchema,
      HoodieStorage storage) throws IOException;

  /**
   * Gets the record iterator based on the type of engine-specific record representation from the
   * file.
   *
   * @param storagePathInfo {@link StoragePathInfo} instance of a file.
   * @param start           Starting byte to start reading.
   * @param length          Bytes to read.
   * @param dataSchema      Schema of records in the file in {@link Schema}.
   * @param requiredSchema  Schema containing required fields to read in {@link Schema} for projection.
   * @param storage         {@link HoodieStorage} for reading records.
   * @return {@link ClosableIterator<T>} that can return all records through iteration.
   */
  public ClosableIterator<T> getFileRecordIterator(
      StoragePathInfo storagePathInfo, long start, long length, Schema dataSchema, Schema requiredSchema,
      HoodieStorage storage) throws IOException {
    return getFileRecordIterator(storagePathInfo.getPath(), start, length, dataSchema, requiredSchema, storage);
  }

  /**
   * Converts an Avro record, e.g., serialized in the log files, to an engine-specific record.
   *
   * @param avroRecord The Avro record.
   * @return An engine-specific record in Type {@link T}.
   */
  public abstract T convertAvroRecord(IndexedRecord avroRecord);

  public abstract GenericRecord convertToAvroRecord(T record, Schema schema);
  
  /**
   * @param mergeMode        record merge mode
   * @param mergeStrategyId  record merge strategy ID
   * @param mergeImplClasses custom implementation classes for record merging
   *
   * @return {@link HoodieRecordMerger} to use.
   */
  public abstract Option<HoodieRecordMerger> getRecordMerger(RecordMergeMode mergeMode, String mergeStrategyId, String mergeImplClasses);

  /**
   * Gets the field value.
   *
   * @param record    The record in engine-specific type.
   * @param schema    The Avro schema of the record.
   * @param fieldName The field name.
   * @return The field value.
   */
  public abstract Object getValue(T record, Schema schema, String fieldName);

  /**
   * Cast to Java boolean value.
   * If the object is not compatible with boolean type, throws.
   */
  public boolean castToBoolean(Object value) {
    if (value instanceof Boolean) {
      return (boolean) value;
    } else {
      throw new IllegalArgumentException(
          "Input value type " + value.getClass() + ", cannot be cast to boolean");
    }
  }

  /**
   * Gets the record key in String.
   *
   * @param record The record in engine-specific type.
   * @param schema The Avro schema of the record.
   * @return The record key in String.
   */
  public String getRecordKey(T record, Schema schema) {
    Object val = getValue(record, schema, RECORD_KEY_METADATA_FIELD);
    return val.toString();
  }

  /**
   * Gets the ordering value in particular type.
   *
   * @param recordOption An option of record.
   * @param metadataMap  A map containing the record metadata.
   * @param schema       The Avro schema of the record.
   * @param orderingFieldName name of the ordering field
   * @return The ordering value.
   */
  public Comparable getOrderingValue(Option<T> recordOption,
                                     Map<String, Object> metadataMap,
                                     Schema schema,
                                     Option<String> orderingFieldName) {
    if (metadataMap.containsKey(INTERNAL_META_ORDERING_FIELD)) {
      return (Comparable) metadataMap.get(INTERNAL_META_ORDERING_FIELD);
    }

    if (!recordOption.isPresent() || orderingFieldName.isEmpty()) {
      return DEFAULT_ORDERING_VALUE;
    }

    Object value = getValue(recordOption.get(), schema, orderingFieldName.get());
    Comparable finalOrderingVal = value != null ? convertValueToEngineType((Comparable) value) : DEFAULT_ORDERING_VALUE;
    metadataMap.put(INTERNAL_META_ORDERING_FIELD, finalOrderingVal);
    return finalOrderingVal;
  }

  /**
   * Constructs a new {@link HoodieRecord} based on the record of engine-specific type and metadata for merging.
   *
   * @param recordOption An option of the record in engine-specific type if exists.
   * @param metadataMap  The record metadata.
   * @return A new instance of {@link HoodieRecord}.
   */
  public abstract HoodieRecord<T> constructHoodieRecord(Option<T> recordOption,
                                                        Map<String, Object> metadataMap);

  /**
   * Seals the engine-specific record to make sure the data referenced in memory do not change.
   *
   * @param record The record.
   * @return The record containing the same data that do not change in memory over time.
   */
  public abstract T seal(T record);

  /**
   * Generates metadata map based on the information.
   *
   * @param recordKey     Record key in String.
   * @param partitionPath Partition path in String.
   * @param orderingVal   Ordering value in String.
   * @return A mapping containing the metadata.
   */
  public Map<String, Object> generateMetadataForRecord(
      String recordKey, String partitionPath, Comparable orderingVal) {
    Map<String, Object> meta = new HashMap<>();
    meta.put(INTERNAL_META_RECORD_KEY, recordKey);
    meta.put(INTERNAL_META_PARTITION_PATH, partitionPath);
    meta.put(INTERNAL_META_ORDERING_FIELD, orderingVal);
    return meta;
  }

  /**
   * Generates metadata of the record. Only fetches record key that is necessary for merging.
   *
   * @param record The record.
   * @param schema The Avro schema of the record.
   * @return A mapping containing the metadata.
   */
  public Map<String, Object> generateMetadataForRecord(T record, Schema schema) {
    Map<String, Object> meta = new HashMap<>();
    meta.put(INTERNAL_META_RECORD_KEY, getRecordKey(record, schema));
    meta.put(INTERNAL_META_SCHEMA_ID, encodeAvroSchema(schema));
    return meta;
  }

  /**
   * Gets the schema encoded in the metadata map
   *
   * @param infoMap The record metadata
   * @return the avro schema if it is encoded in the metadata map, else null
   */
  public Schema getSchemaFromMetadata(Map<String, Object> infoMap) {
    return decodeAvroSchema(infoMap.get(INTERNAL_META_SCHEMA_ID));
  }

  /**
   * Updates the schema and reset the ordering value in existing metadata mapping of a record.
   *
   * @param meta   Metadata in a mapping.
   * @param schema New schema to set.
   * @return The input metadata mapping.
   */
  public Map<String, Object> updateSchemaAndResetOrderingValInMetadata(Map<String, Object> meta,
                                                                       Schema schema) {
    meta.remove(INTERNAL_META_ORDERING_FIELD);
    meta.put(INTERNAL_META_SCHEMA_ID, encodeAvroSchema(schema));
    return meta;
  }

  /**
   * Merge the skeleton file and data file iterators into a single iterator that will produce rows that contain all columns from the
   * skeleton file iterator, followed by all columns in the data file iterator
   *
   * @param skeletonFileIterator iterator over bootstrap skeleton files that contain hudi metadata columns
   * @param dataFileIterator     iterator over data files that were bootstrapped into the hudi table
   * @return iterator that concatenates the skeletonFileIterator and dataFileIterator
   */
  public abstract ClosableIterator<T> mergeBootstrapReaders(ClosableIterator<T> skeletonFileIterator,
                                                            Schema skeletonRequiredSchema,
                                                            ClosableIterator<T> dataFileIterator,
                                                            Schema dataRequiredSchema);

  /**
   * Creates a function that will reorder records of schema "from" to schema of "to"
   * all fields in "to" must be in "from", but not all fields in "from" must be in "to"
   *
   * @param from           the schema of records to be passed into UnaryOperator
   * @param to             the schema of records produced by UnaryOperator
   * @param renamedColumns map of renamed columns where the key is the new name from the query and
   *                       the value is the old name that exists in the file
   * @return a function that takes in a record and returns the record with reordered columns
   */
  public abstract UnaryOperator<T> projectRecord(Schema from, Schema to, Map<String, String> renamedColumns);

  public final UnaryOperator<T> projectRecord(Schema from, Schema to) {
    return projectRecord(from, to, Collections.emptyMap());
  }

  /**
   * Returns the value to a type representation in a specific engine.
   * <p>
   * This can be overridden by the reader context implementation on a specific engine to handle
   * engine-specific field type system.  For example, Spark uses {@code UTF8String} to represent
   * {@link String} field values, so we need to convert the values to {@code UTF8String} type
   * in Spark for proper value comparison.
   *
   * @param value {@link Comparable} value to be converted.
   *
   * @return the converted value in a type representation in a specific engine.
   */
  public Comparable convertValueToEngineType(Comparable value) {
    return value;
  }

  /**
   * Extracts the record position value from the record itself.
   *
   * @return the record position in the base file.
   */
  public long extractRecordPosition(T record, Schema schema, String fieldName, long providedPositionIfNeeded) {
    if (supportsParquetRowIndex()) {
      Object position = getValue(record, schema, fieldName);
      if (position != null) {
        return (long) position;
      } else {
        throw new IllegalStateException("Record position extraction failed");
      }
    }
    return providedPositionIfNeeded;
  }

  public boolean supportsParquetRowIndex() {
    return false;
  }

  /**
   * Encodes the given avro schema for efficient serialization.
   */
  private Integer encodeAvroSchema(Schema schema) {
    return this.localAvroSchemaCache.cacheSchema(schema);
  }

  /**
   * Decodes the avro schema with given version ID.
   */
  @Nullable
  private Schema decodeAvroSchema(Object versionId) {
    return this.localAvroSchemaCache.getSchema((Integer) versionId).orElse(null);
  }

  @Override
  public void close() {
    if (this.localAvroSchemaCache != null) {
      this.localAvroSchemaCache.close();
    }
  }
}
