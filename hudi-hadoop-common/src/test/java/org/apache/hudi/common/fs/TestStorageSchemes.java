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

package org.apache.hudi.common.fs;

import org.apache.hudi.hadoop.fs.HoodieWrapperFileSystem;
import org.apache.hudi.storage.StorageSchemes;

import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link StorageSchemes}.
 */
public class TestStorageSchemes {

  @Test
  public void testStorageSchemes() {
    assertTrue(StorageSchemes.isSchemeSupported("hdfs"));
    assertTrue(StorageSchemes.isSchemeSupported("afs"));
    assertFalse(StorageSchemes.isSchemeSupported("s2"));

    for (StorageSchemes scheme : StorageSchemes.values()) {
      String schemeName = scheme.getScheme();
      if (scheme.getScheme().startsWith("s3") || scheme.getScheme().startsWith("gs")) {
        assertTrue(StorageSchemes.getStorageLockImplementationIfExists(schemeName).isPresent());
      } else {
        assertFalse(StorageSchemes.getStorageLockImplementationIfExists(schemeName).isPresent());
      }
    }

    assertTrue(StorageSchemes.isAtomicCreationSupported("file"));
    assertTrue(StorageSchemes.isAtomicCreationSupported("hdfs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("afs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("s3a"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("gs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("wasb"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("adl"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("abfs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("oss"));
    assertTrue(StorageSchemes.isAtomicCreationSupported("viewfs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("alluxio"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("cosn"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("dbfs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("cos"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("jfs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("bos"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("ks3"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("nos"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("ofs"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("oci"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("tos"));
    assertFalse(StorageSchemes.isAtomicCreationSupported("cfs"));
    assertTrue(StorageSchemes.isAtomicCreationSupported("hopsfs"));
  }

  @Test
  public void testConversionToNewSchema() {
    Path s3TablePath1 = new Path("s3://test.1234/table1");
    assertEquals(s3TablePath1, HoodieWrapperFileSystem.convertPathWithScheme(s3TablePath1, "s3"));

    Path s3TablePath2 = new Path("s3://1234.test/table1");
    assertEquals(s3TablePath2, HoodieWrapperFileSystem.convertPathWithScheme(s3TablePath2, "s3"));

    Path s3TablePath3 = new Path("s3://test1234/table1");
    assertEquals(s3TablePath3, HoodieWrapperFileSystem.convertPathWithScheme(s3TablePath3, "s3"));

    Path hdfsTablePath = new Path("hdfs://sandbox.foo.com:8020/test.1234/table1");
    assertEquals(hdfsTablePath, HoodieWrapperFileSystem.convertPathWithScheme(hdfsTablePath, "hdfs"));

    Path localTablePath = new Path("file:/var/table1");
    Path localTablePathNoPrefix = new Path("/var/table1");
    assertEquals(localTablePath, HoodieWrapperFileSystem.convertPathWithScheme(localTablePath, "file"));
    assertEquals(localTablePath, HoodieWrapperFileSystem.convertPathWithScheme(localTablePathNoPrefix, "file"));
  }
}
