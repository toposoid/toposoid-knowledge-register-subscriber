/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ideal.linked.toposoid.mq

import akka.actor.ActorSystem
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Neo4JUtilsImpl
import play.api.libs.json.Json

import java.net.URI
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, DefaultCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import software.amazon.awssdk.services.sqs.model.SendMessageRequest



object TestUtils {
  def deleteNeo4JAllData(transversalState:TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    val neo4JUtils = new Neo4JUtilsImpl()
    neo4JUtils.executeQuery(query, transversalState)
  }

  def executeQueryAndReturn(query:String, transversalState:TransversalState): Neo4jRecords = {
    val convertQuery = ToposoidUtils.encodeJsonInJson(query)
    val hoge = ToposoidUtils.decodeJsonInJson(convertQuery)
    val json = s"""{ "query":"$convertQuery", "target": "" }"""
    val jsonResult = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_GRAPHDB_WEB_HOST"), conf.getString("TOPOSOID_GRAPHDB_WEB_PORT"), "getQueryFormattedResult", transversalState)
    Json.parse(jsonResult).as[Neo4jRecords]
  }

  def publishMessage(json:String): Unit = {

    implicit val actorSystem = ActorSystem("example")

    val queueUrl = "http://192.168.33.10:9324/queue/test-queue.fifo"

    val sqs = SqsAsyncClient
      .builder()
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create("AK", "SK") // (1)
        )
      )
      .endpointOverride(URI.create("http://192.168.33.10:9324")) // (2)
      .region(Region.AP_NORTHEAST_1)
      .httpClient(AkkaHttpClient.builder()
        .withActorSystem(actorSystem).build())
      .build()
    /*
    val sqs:SqsClient = SqsAsyncClient.builder()
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create("AK", "SK") // (1)
        )
      )
      .endpointOverride(new URI("http://192.168.33.10:9324")) // ElasticMQコンテナ向けのURL
      .httpClient(AkkaHttpClient.builder()
        .withActorSystem(actorSystem).build())
      .build();
     */
    sqs.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageGroupId("x")
        .messageBody(json)
        .build()
    ).join()

  }

}
