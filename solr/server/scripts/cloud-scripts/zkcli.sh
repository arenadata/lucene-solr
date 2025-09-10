#!/usr/bin/env bash

# You can override pass the following parameters to this script:
# 

if [ -z "$JAVA_HOME" ] && [ -r /usr/lib/bigtop-utils/bigtop-detect-javahome ]; then
  . /usr/lib/bigtop-utils/bigtop-detect-javahome
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JVM="$JAVA_HOME/bin/java"
else
  JVM="${JAVA:-java}"
fi
# Find location of this script

sdir="`dirname \"$0\"`"

log4j_config="file:$sdir/../../resources/log4j2-console.xml"

# Settings for ZK ACL
#SOLR_ZK_CREDS_AND_ACLS="-DzkACLProvider=org.apache.solr.common.cloud.VMParamsAllAndReadonlyDigestZkACLProvider \
#  -DzkCredentialsProvider=org.apache.solr.common.cloud.VMParamsSingleSetCredentialsDigestZkCredentialsProvider \
#  -DzkDigestUsername=admin-user -DzkDigestPassword=CHANGEME-ADMIN-PASSWORD \
#  -DzkDigestReadonlyUsername=readonly-user -DzkDigestReadonlyPassword=CHANGEME-READONLY-PASSWORD"

PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH" "$JVM" $SOLR_ZK_CREDS_AND_ACLS $ZKCLI_JVM_FLAGS -Dlog4j.configurationFile=$log4j_config \
-classpath "$sdir/../../solr-webapp/webapp/WEB-INF/lib/*:$sdir/../../lib/ext/*:$sdir/../../lib/*" org.apache.solr.cloud.ZkCLI ${1+"$@"}

