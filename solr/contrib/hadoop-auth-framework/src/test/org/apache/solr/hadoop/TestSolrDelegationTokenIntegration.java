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

import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.token.Token;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.DelegationTokenRequest;
import org.apache.solr.client.solrj.response.DelegationTokenResponse;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.security.HttpParamDelegationTokenPlugin;
import org.apache.solr.security.KerberosPlugin;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.solr.security.HttpParamDelegationTokenPlugin.USER_PARAM;

/**
 * Integration test for the Hadoop Token Framework bridge.
 * Uses {@link HttpParamDelegationTokenPlugin} (the same test plugin used by
 * {@code TestSolrCloudWithDelegationTokens}) to avoid requiring a real KDC.
 */
@LuceneTestCase.Slow
public class TestSolrDelegationTokenIntegration extends SolrTestCaseJ4 {
  private static final int NUM_SERVERS = 2;
  private static MiniSolrCloudCluster miniCluster;
  private static HttpSolrClient solrClient;
  private static String solrUrl;

  @BeforeClass
  public static void startup() throws Exception {
    System.setProperty("authenticationPlugin", HttpParamDelegationTokenPlugin.class.getName());
    System.setProperty(KerberosPlugin.DELEGATION_TOKEN_ENABLED, "true");
    System.setProperty("solr.kerberos.cookie.domain", "127.0.0.1");

    miniCluster = new MiniSolrCloudCluster(NUM_SERVERS, createTempDir(), buildJettyConfig("/solr"));
    JettySolrRunner runner = miniCluster.getJettySolrRunners().get(0);
    solrUrl = runner.getBaseUrl().toString();
    solrClient = new HttpSolrClient.Builder(solrUrl).build();
  }

  @AfterClass
  public static void shutdown() throws Exception {
    if (miniCluster != null) {
      miniCluster.shutdown();
      miniCluster = null;
    }
    if (solrClient != null) {
      solrClient.close();
      solrClient = null;
    }
    System.clearProperty("authenticationPlugin");
    System.clearProperty(KerberosPlugin.DELEGATION_TOKEN_ENABLED);
    System.clearProperty("solr.kerberos.cookie.domain");
  }

  private String getDelegationToken(String renewer, String user) throws Exception {
    DelegationTokenRequest.Get get = new DelegationTokenRequest.Get(renewer) {
      @Override
      public SolrParams getParams() {
        ModifiableSolrParams params = new ModifiableSolrParams(super.getParams());
        params.set(USER_PARAM, user);
        return params;
      }
    };
    DelegationTokenResponse.Get getResponse = get.process(solrClient);
    return getResponse.getDelegationToken();
  }

  /**
   * Test that a delegation token obtained from Solr can be wrapped into a
   * Hadoop Token, stored in Credentials, retrieved, and used to authenticate.
   */
  @Test
  public void testObtainWrapAndUse() throws Exception {
    // 1. Obtain a delegation token via DelegationTokenRequest
    String dtString = getDelegationToken("renewer", "testUser");
    assertNotNull("delegation token should not be null", dtString);

    // 2. Wrap into a Hadoop Token
    Token<SolrDelegationTokenIdentifier> hadoopToken =
        SolrDelegationTokenUtil.createHadoopToken(dtString, solrUrl);

    assertEquals(SolrDelegationTokenIdentifier.SOLR_DELEGATION_KIND, hadoopToken.getKind());
    assertEquals(new Text(solrUrl), hadoopToken.getService());

    // 3. Store in Credentials and retrieve
    Credentials creds = new Credentials();
    creds.addToken(hadoopToken.getService(), hadoopToken);

    String retrieved = SolrDelegationTokenUtil.getTokenFromCredentials(creds, solrUrl);
    assertEquals(dtString, retrieved);

    // 4. Use the retrieved token to authenticate
    try (HttpSolrClient dtClient = new HttpSolrClient.Builder(solrUrl)
        .withKerberosDelegationToken(retrieved)
        .build()) {
      CollectionAdminRequest.List listRequest = new CollectionAdminRequest.List();
      dtClient.request(listRequest);
    }
  }

  /**
   * Test that Hadoop Token identifier can be deserialized from a created token.
   */
  @Test
  public void testTokenIdentifierDeserialization() throws Exception {
    String dtString = getDelegationToken(null, "identUser");
    assertNotNull(dtString);

    Token<SolrDelegationTokenIdentifier> hadoopToken =
        SolrDelegationTokenUtil.createHadoopToken(dtString, solrUrl);

    // The identifier bytes should deserialize back to a valid SolrDelegationTokenIdentifier
    SolrDelegationTokenIdentifier identifier = new SolrDelegationTokenIdentifier();
    java.io.DataInputStream in = new java.io.DataInputStream(
        new java.io.ByteArrayInputStream(hadoopToken.getIdentifier()));
    identifier.readFields(in);
    in.close();

    assertEquals(SolrDelegationTokenIdentifier.SOLR_DELEGATION_KIND, identifier.getKind());
    assertNotNull(identifier.getOwner());
  }

  /**
   * Test that authentication fails without a token.
   */
  @Test
  public void testFailWithoutToken() throws Exception {
    HttpSolrClient noAuthClient = new HttpSolrClient.Builder(solrUrl).build();
    try {
      CollectionAdminRequest.List listRequest = new CollectionAdminRequest.List();
      noAuthClient.request(listRequest);
      fail("Expected request without authentication to fail");
    } catch (HttpSolrClient.RemoteSolrException e) {
      assertEquals(401, e.code());
    } finally {
      noAuthClient.close();
    }
  }
}
