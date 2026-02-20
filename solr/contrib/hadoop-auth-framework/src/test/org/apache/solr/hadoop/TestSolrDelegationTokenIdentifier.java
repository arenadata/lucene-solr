/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.hadoop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.apache.hadoop.io.Text;
import org.apache.solr.SolrTestCase;
import org.junit.Test;

public class TestSolrDelegationTokenIdentifier extends SolrTestCase {

  @Test
  public void testGetKind() {
    SolrDelegationTokenIdentifier id = new SolrDelegationTokenIdentifier();
    assertEquals(new Text("solr-dt"), id.getKind());
  }

  @Test
  public void testNoArgConstructor() {
    SolrDelegationTokenIdentifier id = new SolrDelegationTokenIdentifier();
    assertNotNull(id);
    assertEquals(new Text(""), id.getOwner());
    // empty owner => getUser() returns null
    assertNull(id.getUser());
  }

  @Test
  public void testFullConstructor() {
    SolrDelegationTokenIdentifier id = new SolrDelegationTokenIdentifier(
        new Text("testuser"), new Text("renewer"), new Text("realUser"));
    assertEquals(new Text("testuser"), id.getOwner());
    assertEquals(new Text("renewer"), id.getRenewer());
    assertEquals(new Text("realUser"), id.getRealUser());
    assertNotNull(id.getUser());
    assertEquals("testuser", id.getUser().getShortUserName());
  }

  @Test
  public void testSerializationRoundTrip() throws Exception {
    SolrDelegationTokenIdentifier original = new SolrDelegationTokenIdentifier(
        new Text("alice"), new Text("bob"), new Text());

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(buf)) {
      original.write(out);
    }

    SolrDelegationTokenIdentifier deserialized = new SolrDelegationTokenIdentifier();
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(buf.toByteArray()))) {
      deserialized.readFields(in);
    }

    assertEquals(original.getKind(), deserialized.getKind());
    assertEquals(original.getOwner(), deserialized.getOwner());
    assertEquals(original.getRenewer(), deserialized.getRenewer());
    assertEquals(original, deserialized);
    assertEquals(original.hashCode(), deserialized.hashCode());
  }

  @Test
  public void testEmptyOwnerRoundTrip() throws Exception {
    SolrDelegationTokenIdentifier original = new SolrDelegationTokenIdentifier();

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(buf)) {
      original.write(out);
    }

    SolrDelegationTokenIdentifier deserialized = new SolrDelegationTokenIdentifier();
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(buf.toByteArray()))) {
      deserialized.readFields(in);
    }

    assertEquals(original, deserialized);
    assertNull(deserialized.getUser());
  }

  @Test
  public void testEquals() {
    SolrDelegationTokenIdentifier id1 = new SolrDelegationTokenIdentifier(
        new Text("user1"), new Text(), new Text());
    SolrDelegationTokenIdentifier id2 = new SolrDelegationTokenIdentifier(
        new Text("user1"), new Text(), new Text());
    SolrDelegationTokenIdentifier id3 = new SolrDelegationTokenIdentifier(
        new Text("user2"), new Text(), new Text());

    assertEquals(id1, id2);
    assertNotEquals(id1, id3);
  }
}
