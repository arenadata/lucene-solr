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
import java.io.DataInputStream;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.token.Token;
import org.apache.solr.SolrTestCase;
import org.junit.Test;

public class TestSolrDelegationTokenUtil extends SolrTestCase {

  @Test
  public void testCreateHadoopToken() throws Exception {
    String dtString = "SolrDelegationToken_abc123";
    String solrUrl = "http://localhost:8983/solr";

    Token<SolrDelegationTokenIdentifier> token =
        SolrDelegationTokenUtil.createHadoopToken(dtString, solrUrl);

    assertNotNull(token);
    assertEquals(SolrDelegationTokenIdentifier.SOLR_DELEGATION_KIND, token.getKind());
    assertEquals(new Text(solrUrl), token.getService());
    assertEquals(dtString, new String(token.getPassword(), "UTF-8"));
  }

  @Test
  public void testCreateHadoopTokenIdentifier() throws Exception {
    String dtString = "SolrDelegationToken_abc123";
    String solrUrl = "http://localhost:8983/solr";

    Token<SolrDelegationTokenIdentifier> token =
        SolrDelegationTokenUtil.createHadoopToken(dtString, solrUrl);

    // Deserialize identifier — should round-trip via AbstractDelegationTokenIdentifier format
    SolrDelegationTokenIdentifier identifier = new SolrDelegationTokenIdentifier();
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(token.getIdentifier()))) {
      identifier.readFields(in);
    }
    assertEquals(SolrDelegationTokenIdentifier.SOLR_DELEGATION_KIND, identifier.getKind());
  }

  @Test
  public void testRoundTripThroughCredentials() throws Exception {
    String dtString = "SolrDelegationToken_xyz789";
    String solrUrl = "http://solr-host:8983/solr";

    Token<SolrDelegationTokenIdentifier> token =
        SolrDelegationTokenUtil.createHadoopToken(dtString, solrUrl);

    Credentials creds = new Credentials();
    creds.addToken(token.getService(), token);

    String retrieved = SolrDelegationTokenUtil.getTokenFromCredentials(creds, solrUrl);
    assertEquals(dtString, retrieved);
  }

  @Test
  public void testGetTokenFromCredentialsNotFound() {
    Credentials creds = new Credentials();
    String result = SolrDelegationTokenUtil.getTokenFromCredentials(creds, "http://nonexistent:8983/solr");
    assertNull(result);
  }

  @Test
  public void testMultipleTokensInCredentials() throws Exception {
    String dt1 = "token_for_cluster1";
    String dt2 = "token_for_cluster2";
    String url1 = "http://solr1:8983/solr";
    String url2 = "http://solr2:8983/solr";

    Token<SolrDelegationTokenIdentifier> token1 =
        SolrDelegationTokenUtil.createHadoopToken(dt1, url1);
    Token<SolrDelegationTokenIdentifier> token2 =
        SolrDelegationTokenUtil.createHadoopToken(dt2, url2);

    Credentials creds = new Credentials();
    creds.addToken(token1.getService(), token1);
    creds.addToken(token2.getService(), token2);

    assertEquals(dt1, SolrDelegationTokenUtil.getTokenFromCredentials(creds, url1));
    assertEquals(dt2, SolrDelegationTokenUtil.getTokenFromCredentials(creds, url2));
  }

}
