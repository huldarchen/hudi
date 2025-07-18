/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.sink.utils;

import org.apache.hudi.common.util.Option;
import org.apache.hudi.common.util.collection.Pair;
import org.apache.hudi.configuration.FlinkOptions;
import org.apache.hudi.configuration.OptionsResolver;
import org.apache.hudi.sink.event.WriteMetadataEvent;

import org.apache.flink.configuration.Configuration;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utilities for coordinator event buffer.
 */
public class EventBuffers implements Serializable {
  private static final long serialVersionUID = 1L;

  // {checkpointId -> (instant, events)}
  private final Map<Long, Pair<String, WriteMetadataEvent[]>> eventBuffers;
  private final Option<CommitGuard> commitGuardOption;

  private EventBuffers(Map<Long, Pair<String, WriteMetadataEvent[]>> eventBuffers, Option<CommitGuard> commitGuardOption) {
    this.eventBuffers = eventBuffers;
    this.commitGuardOption = commitGuardOption;
  }

  public static EventBuffers getInstance(Configuration conf) {
    final Option<CommitGuard> commitGuardOpt = OptionsResolver.isBlockingInstantGeneration(conf)
        ? Option.of(CommitGuard.create(conf.get(FlinkOptions.WRITE_COMMIT_ACK_TIMEOUT))) : Option.empty();
    return new EventBuffers(new ConcurrentSkipListMap<>(), commitGuardOpt);
  }

  /**
   * Checks the buffer is ready to commit.
   */
  public static boolean allEventsReceived(WriteMetadataEvent[] eventBuffer) {
    return Arrays.stream(eventBuffer)
        // we do not use event.isReady to check the instant
        // because the write task may send an event eagerly for empty
        // data set, the even may have a timestamp of last committed instant.
        .allMatch(event -> event != null && event.isLastBatch());
  }

  public WriteMetadataEvent[] addEventToBuffer(WriteMetadataEvent event) {
    WriteMetadataEvent[] eventBuffer = this.eventBuffers.get(event.getCheckpointId()).getRight();
    if (eventBuffer[event.getTaskID()] != null
        && eventBuffer[event.getTaskID()].getInstantTime().equals(event.getInstantTime())) {
      eventBuffer[event.getTaskID()].mergeWith(event);
    } else {
      eventBuffer[event.getTaskID()] = event;
    }
    return eventBuffer;
  }

  public void addEventsToBuffer(Map<Long, Pair<String, WriteMetadataEvent[]>> events) {
    this.eventBuffers.putAll(events);
  }

  /**
   * Returns existing bootstrap buffer or creates a new one.
   */
  public WriteMetadataEvent[] getOrCreateBootstrapBuffer(WriteMetadataEvent event, int parallelism) {
    return this.eventBuffers.computeIfAbsent(event.getCheckpointId(),
        ckpId -> Pair.of(event.getInstantTime(), new WriteMetadataEvent[parallelism])).getRight();
  }

  /**
   * Cleans the task events triggered after or on the give event checkpoint ID.
   */
  public void cleanLegacyEvents(WriteMetadataEvent event) {
    this.eventBuffers.entrySet().stream()
        .filter(entry -> entry.getKey().compareTo(event.getCheckpointId()) >= 0)
        .map(entry -> entry.getValue().getRight())
        .forEach(eventBuffer -> resetBufferAt(eventBuffer, event.getTaskID()));
  }

  private static void resetBufferAt(WriteMetadataEvent[] eventBuffer, int idx) {
    if (eventBuffer.length > idx) {
      eventBuffer[idx] = null;
    }
  }

  /**
   * Returns the pair of instant time and event buffer.
   */
  public Pair<String, WriteMetadataEvent[]> getInstantAndEventBuffer(long checkpointId) {
    return this.eventBuffers.get(checkpointId);
  }

  public WriteMetadataEvent[] getLatestEventBuffer(String instantTime) {
    return eventBuffers.values().stream().filter(val -> val.getLeft().equals(instantTime)).findFirst().map(Pair::getRight).orElse(null);
  }

  public WriteMetadataEvent[] getEventBuffer(long checkpointId) {
    return Option.ofNullable(this.eventBuffers.get(checkpointId)).map(Pair::getRight).orElse(null);
  }

  public Stream<Map.Entry<Long, Pair<String, WriteMetadataEvent[]>>> getEventBufferStream() {
    return this.eventBuffers.entrySet().stream();
  }

  public void initNewEventBuffer(long checkpointId, String instantTime, int parallelism) {
    this.eventBuffers.put(checkpointId, Pair.of(instantTime, new WriteMetadataEvent[parallelism]));
  }

  public void awaitAllInstantsToCompleteIfNecessary() {
    if (this.commitGuardOption.isPresent() && nonEmpty()) {
      this.commitGuardOption.get().blockFor(getPendingInstants());
    }
  }

  public void reset(long checkpointId) {
    this.eventBuffers.remove(checkpointId);
    this.commitGuardOption.ifPresent(CommitGuard::unblock);
  }

  public boolean nonEmpty() {
    return this.eventBuffers.values().stream()
        .map(Pair::getValue)
        .flatMap(Arrays::stream).anyMatch(Objects::nonNull);
  }

  public String getPendingInstants() {
    return this.eventBuffers.values().stream().map(Pair::getKey).collect(Collectors.joining(","));
  }

  /**
   * Get write metadata events where there exists no event sent by eager flushing from writers.
   */
  public Map<Long, Pair<String, WriteMetadataEvent[]>> getAllCompletedEvents() {
    return this.eventBuffers.entrySet().stream()
        .filter(entry -> Arrays.stream(entry.getValue().getRight()).allMatch(event -> event == null || event.isLastBatch()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
