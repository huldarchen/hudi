/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.table.catalog;

import org.apache.hudi.avro.HoodieAvroUtils;
import org.apache.hudi.common.model.HoodieRecord;
import org.apache.hudi.common.util.StringUtils;
import org.apache.hudi.common.util.collection.Pair;
import org.apache.hudi.configuration.FlinkOptions;
import org.apache.hudi.util.DataTypeUtils;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.serde2.typeinfo.CharTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.ListTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.MapTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.StructTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.hive.serde2.typeinfo.VarcharTypeInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Utilities for Hive field schema.
 */
public class HiveSchemaUtils {
  /**
   * Get field names from field schemas.
   */
  public static List<String> getFieldNames(List<FieldSchema> fieldSchemas) {
    return fieldSchemas.stream().map(FieldSchema::getName).collect(Collectors.toList());
  }

  public static org.apache.flink.table.api.Schema convertTableSchema(Table hiveTable) {
    List<FieldSchema> allCols = hiveTable.getSd().getCols().stream()
        // filter out the metadata columns
        .filter(s -> !HoodieAvroUtils.isMetadataField(s.getName()))
        .collect(Collectors.toList());
    // need to refactor the partition key field positions: they are not always in the last
    allCols.addAll(hiveTable.getPartitionKeys());

    String pkConstraintName = hiveTable.getParameters().get(TableOptionProperties.PK_CONSTRAINT_NAME);
    String pkColumnStr = hiveTable.getParameters().get(FlinkOptions.RECORD_KEY_FIELD.key());
    List<String> pkColumns = pkColumnStr == null ? new ArrayList<>() : StringUtils.split(pkColumnStr, ",");

    String[] colNames = new String[allCols.size()];
    DataType[] colTypes = new DataType[allCols.size()];

    for (int i = 0; i < allCols.size(); i++) {
      FieldSchema fs = allCols.get(i);

      colNames[i] = fs.getName();
      colTypes[i] =
          toFlinkType(TypeInfoUtils.getTypeInfoFromTypeString(fs.getType()));
      if (pkColumns.contains(colNames[i])) {
        colTypes[i] = colTypes[i].notNull();
      }
    }

    org.apache.flink.table.api.Schema.Builder builder = org.apache.flink.table.api.Schema.newBuilder().fromFields(colNames, colTypes);
    if (!StringUtils.isNullOrEmpty(pkConstraintName)) {
      builder.primaryKeyNamed(pkConstraintName, pkColumns);
    } else if (!pkColumns.isEmpty()) {
      builder.primaryKey(pkColumns);
    }

    return builder.build();
  }

  /**
   * Convert Hive data type to a Flink data type.
   *
   * @param hiveType a Hive data type
   * @return the corresponding Flink data type
   */
  public static DataType toFlinkType(TypeInfo hiveType) {
    checkNotNull(hiveType, "hiveType cannot be null");

    switch (hiveType.getCategory()) {
      case PRIMITIVE:
        return toFlinkPrimitiveType((PrimitiveTypeInfo) hiveType);
      case LIST:
        ListTypeInfo listTypeInfo = (ListTypeInfo) hiveType;
        return DataTypes.ARRAY(toFlinkType(listTypeInfo.getListElementTypeInfo()));
      case MAP:
        MapTypeInfo mapTypeInfo = (MapTypeInfo) hiveType;
        return DataTypes.MAP(
            toFlinkType(mapTypeInfo.getMapKeyTypeInfo()),
            toFlinkType(mapTypeInfo.getMapValueTypeInfo()));
      case STRUCT:
        StructTypeInfo structTypeInfo = (StructTypeInfo) hiveType;

        List<String> names = structTypeInfo.getAllStructFieldNames();
        List<TypeInfo> typeInfos = structTypeInfo.getAllStructFieldTypeInfos();

        DataTypes.Field[] fields = new DataTypes.Field[names.size()];

        for (int i = 0; i < fields.length; i++) {
          fields[i] = DataTypes.FIELD(names.get(i), toFlinkType(typeInfos.get(i)));
        }

        return DataTypes.ROW(fields);
      default:
        throw new UnsupportedOperationException(
            String.format("Flink doesn't support Hive data type %s yet.", hiveType));
    }
  }

  private static DataType toFlinkPrimitiveType(PrimitiveTypeInfo hiveType) {
    checkNotNull(hiveType, "hiveType cannot be null");

    switch (hiveType.getPrimitiveCategory()) {
      case CHAR:
        return DataTypes.CHAR(((CharTypeInfo) hiveType).getLength());
      case VARCHAR:
        return DataTypes.VARCHAR(((VarcharTypeInfo) hiveType).getLength());
      case STRING:
        return DataTypes.STRING();
      case BOOLEAN:
        return DataTypes.BOOLEAN();
      case BYTE:
        return DataTypes.TINYINT();
      case SHORT:
        return DataTypes.SMALLINT();
      case INT:
        return DataTypes.INT();
      case LONG:
        return DataTypes.BIGINT();
      case FLOAT:
        return DataTypes.FLOAT();
      case DOUBLE:
        return DataTypes.DOUBLE();
      case DATE:
        return DataTypes.DATE();
      case TIMESTAMP:
        // see org.apache.hudi.hive.util.HiveSchemaUtil#convertField for details.
        return DataTypes.TIMESTAMP(6);
      case BINARY:
        return DataTypes.BYTES();
      case DECIMAL:
        DecimalTypeInfo decimalTypeInfo = (DecimalTypeInfo) hiveType;
        return DataTypes.DECIMAL(
            decimalTypeInfo.getPrecision(), decimalTypeInfo.getScale());
      default:
        throw new UnsupportedOperationException(
            String.format(
                "Flink doesn't support Hive primitive type %s yet", hiveType));
    }
  }

  /**
   * Create Hive field schemas from Flink table schema including the hoodie metadata fields.
   */
  public static List<FieldSchema> toHiveFieldSchema(Schema schema, boolean withOperationField) {
    List<FieldSchema> columns = new ArrayList<>();
    Collection<String> metaFields = new ArrayList<>(HoodieRecord.HOODIE_META_COLUMNS);
    if (withOperationField) {
      metaFields.add(HoodieRecord.OPERATION_METADATA_FIELD);
    }

    for (String metaField : metaFields) {
      columns.add(new FieldSchema(metaField, "string", null));
    }
    columns.addAll(createHiveColumns(schema));
    return columns;
  }

  /**
   * Create Hive columns from Flink table schema.
   */
  private static List<FieldSchema> createHiveColumns(Schema schema) {
    RowType rowType = DataTypeUtils.toRowType(schema);

    List<FieldSchema> columns = new ArrayList<>(rowType.getFieldCount());

    for (RowType.RowField field: rowType.getFields()) {
      columns.add(
          new FieldSchema(
              field.getName(),
              toHiveTypeInfo(field.getType()).getTypeName(),
              null));
    }
    return columns;
  }

  /**
   * Convert Flink DataType to Hive TypeInfo. For types with a precision parameter, e.g.
   * timestamp, the supported precisions in Hive and Flink can be different. Therefore the
   * conversion will fail for those types if the precision is not supported by Hive and
   * checkPrecision is true.
   *
   * @param dataType a Flink LogicalType
   * @return the corresponding Hive data type
   */
  public static TypeInfo toHiveTypeInfo(LogicalType dataType) {
    checkNotNull(dataType, "type cannot be null");
    return dataType.accept(new TypeInfoLogicalTypeVisitor(dataType));
  }

  /**
   * Split the field schemas by given partition keys.
   *
   * @param fieldSchemas  The Hive field schemas.
   * @param partitionKeys The partition keys.
   * @return The pair of (regular columns, partition columns) schema fields
   */
  public static Pair<List<FieldSchema>, List<FieldSchema>> splitSchemaByPartitionKeys(
      List<FieldSchema> fieldSchemas,
      List<String> partitionKeys) {
    List<FieldSchema> regularColumns = new ArrayList<>();
    List<FieldSchema> partitionColumns = new ArrayList<>();
    for (FieldSchema fieldSchema : fieldSchemas) {
      if (partitionKeys.contains(fieldSchema.getName())) {
        partitionColumns.add(fieldSchema);
      } else {
        regularColumns.add(fieldSchema);
      }
    }
    return Pair.of(regularColumns, partitionColumns);
  }
}
