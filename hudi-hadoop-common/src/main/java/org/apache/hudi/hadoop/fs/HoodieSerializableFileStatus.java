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

package org.apache.hudi.hadoop.fs;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A serializable file status implementation
 * <p>
 * Use `HoodieFileStatus` generated by Avro instead this class if possible
 * This class is needed because `hudi-hadoop-mr-bundle` relies on Avro 1.8.2,
 * and won't work well with `HoodieFileStatus`
 */
public class HoodieSerializableFileStatus implements Serializable {

  private final Path path;
  private final long length;
  private final Boolean isDir;
  private final short blockReplication;
  private final long blockSize;
  private final long modificationTime;
  private final long accessTime;
  private final FsPermission permission;
  private final String owner;
  private final String group;
  private final Path symlink;

  HoodieSerializableFileStatus(Path path, long length, boolean isDir, short blockReplication,
                               long blockSize, long modificationTime, long accessTime,
                               FsPermission permission, String owner, String group, Path symlink) {
    this.path = path;
    this.length = length;
    this.isDir = isDir;
    this.blockReplication = blockReplication;
    this.blockSize = blockSize;
    this.modificationTime = modificationTime;
    this.accessTime = accessTime;
    this.permission = permission;
    this.owner = owner;
    this.group = group;
    this.symlink = symlink;
  }

  public Path getPath() {
    return path;
  }

  public long getLen() {
    return length;
  }

  public Boolean isDirectory() {
    return isDir;
  }

  public short getReplication() {
    return blockReplication;
  }

  public long getBlockSize() {
    return blockSize;
  }

  public long getModificationTime() {
    return modificationTime;
  }

  public long getAccessTime() {
    return accessTime;
  }

  public FsPermission getPermission() {
    return permission;
  }

  public String getOwner() {
    return owner;
  }

  public String getGroup() {
    return group;
  }

  public Path getSymlink() {
    return symlink;
  }

  public static HoodieSerializableFileStatus fromFileStatus(FileStatus status) {
    Path symlink;
    try {
      symlink = status.getSymlink();
    } catch (IOException ioe) {
      // status is not symlink
      symlink = null;
    }

    return new HoodieSerializableFileStatus(status.getPath(), status.getLen(), status.isDir(),
        status.getReplication(), status.getBlockSize(), status.getModificationTime(),
        status.getAccessTime(), status.getPermission(), status.getOwner(), status.getGroup(), symlink);
  }

  public static HoodieSerializableFileStatus[] fromFileStatuses(FileStatus[] statuses) {
    return Arrays.stream(statuses)
        .map(status -> HoodieSerializableFileStatus.fromFileStatus(status))
        .collect(Collectors.toList())
        .toArray(new HoodieSerializableFileStatus[statuses.length]);
  }

  public static FileStatus toFileStatus(HoodieSerializableFileStatus status) {
    return new FileStatus(status.getLen(), status.isDirectory(), status.getReplication(),
        status.getBlockSize(), status.getModificationTime(), status.getAccessTime(), status.getPermission(),
        status.getOwner(), status.getGroup(), status.getSymlink(), status.getPath());
  }

  public static FileStatus[] toFileStatuses(HoodieSerializableFileStatus[] statuses) {
    return Arrays.stream(statuses)
        .map(status -> HoodieSerializableFileStatus.toFileStatus(status))
        .collect(Collectors.toList())
        .toArray(new FileStatus[statuses.length]);
  }
}
