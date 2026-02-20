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

import java.nio.charset.StandardCharsets;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.token.Token;

public final class SolrDelegationTokenUtil {

  private SolrDelegationTokenUtil() {}

  public static Token<SolrDelegationTokenIdentifier> createHadoopToken(String dtString, String solrUrl) {
    SolrDelegationTokenIdentifier identifier = new SolrDelegationTokenIdentifier();
    return new Token<>(identifier.getBytes(), dtString.getBytes(StandardCharsets.UTF_8),
        SolrDelegationTokenIdentifier.SOLR_DELEGATION_KIND, new Text(solrUrl));
  }

  public static String getTokenFromCredentials(Credentials creds, String solrUrl) {
    Token<?> token = creds.getToken(new Text(solrUrl));
    if (token == null) {
      return null;
    }
    return new String(token.getPassword(), StandardCharsets.UTF_8);
  }

  public static String findTokenInCredentials(Credentials creds) {
    for (Token<?> token : creds.getAllTokens()) {
      if (SolrDelegationTokenIdentifier.SOLR_DELEGATION_KIND.equals(token.getKind())) {
        return new String(token.getPassword(), StandardCharsets.UTF_8);
      }
    }
    return null;
  }
}
