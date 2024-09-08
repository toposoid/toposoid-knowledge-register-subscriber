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

import java.net.URI
import akka.actor.ActorSystem
import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.alpakka.sqs.scaladsl.{SqsAckFlow, SqsSource}
import akka.stream.scaladsl.Sink
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.SqsAckResult
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.mq.KnowledgeRegistration
import com.ideal.linked.toposoid.common.{ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeForImage, KnowledgeSentenceSet}
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.{Neo4JUtilsImpl, Sentence2Neo4jTransformer}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import io.jvm.uuid.UUID

import scala.util.{Failure, Success, Try}


object KnowledgeRegisterSubscriber extends App with LazyLogging {
  val endpoint = "http://" + conf.getString("TOPOSOID_MQ_HOST") + ":" + conf.getString("TOPOSOID_MQ_PORT")
  implicit val actorSystem = ActorSystem("example")

  //TODO:Credentialsは、BIZ環境にも対応できるようにしておく
  implicit val sqsClient = SqsAsyncClient
    .builder()
    .credentialsProvider(
      StaticCredentialsProvider.create(
        AwsBasicCredentials.create("AK", "SK") // (1)
      )
    )
    .endpointOverride(URI.create(endpoint)) // (2)
    .region(Region.AP_NORTHEAST_1)
    .httpClient(AkkaHttpClient.builder()
      .withActorSystem(actorSystem).build())
    .build()

  val queueUrl = endpoint + "/queue/test-queue.fifo"
  val settings = SqsSourceSettings()


  private def registerKnowledge(knowledgeSentenceSetForParser:KnowledgeSentenceSetForParser, transversalState:TransversalState) = Try {
    val knowledgeSentenceSetForParserWithImage = KnowledgeSentenceSetForParser(
      registKnowledgeImages(knowledgeSentenceSetForParser.premiseList, transversalState),
      knowledgeSentenceSetForParser.premiseLogicRelation,
      registKnowledgeImages(knowledgeSentenceSetForParser.claimList, transversalState),
      knowledgeSentenceSetForParser.claimLogicRelation)
    Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParserWithImage, transversalState)
    FeatureVectorizer.createVector(knowledgeSentenceSetForParserWithImage, transversalState)
    //val a = 1/0
  } match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  private def registKnowledgeImages(knowledgeForParsers: List[KnowledgeForParser], transversalState: TransversalState): List[KnowledgeForParser] = Try {

    knowledgeForParsers.foldLeft(List.empty[KnowledgeForParser]) {
      (acc, x) => {
        val knowledgeForImages: List[KnowledgeForImage] = x.knowledge.knowledgeForImages.map(y => {
          val imageFeatureId = UUID.random.toString
          val json: String = Json.toJson(KnowledgeForImage(imageFeatureId, y.imageReference)).toString()
          val knowledgeForImageJson: String = ToposoidUtils.callComponent(json,
            conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
            conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
            "registImage", transversalState)
          val registContentResult: RegistContentResult = Json.parse(knowledgeForImageJson).as[RegistContentResult]
          if (registContentResult.statusInfo.status.equals("ERROR")) throw new Exception(registContentResult.statusInfo.message)
          registContentResult.knowledgeForImage
        })
        val knowledge = Knowledge(sentence = x.knowledge.sentence,
          lang = x.knowledge.lang, extentInfoJson = x.knowledge.extentInfoJson,
          isNegativeSentence = x.knowledge.isNegativeSentence, knowledgeForImages)
        acc :+ KnowledgeForParser(x.propositionId, x.sentenceId, knowledge)
      }
    }
  } match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  private def deleteObject(knowledgeForParser: KnowledgeForParser, transversalState:TransversalState) = {
    val query = s"MATCH (n)-[r]-() WHERE n.propositionId = '${knowledgeForParser.propositionId}' DELETE n,r"
    val neo4JUtils = new Neo4JUtilsImpl()
    neo4JUtils.executeQuery(query, transversalState)
    FeatureVectorizer.removeVector(knowledgeForParser,transversalState)
  }

  private def rollback(knowledgeSentenceSetForParser:KnowledgeSentenceSetForParser, transversalState:TransversalState)= {
    try {
      knowledgeSentenceSetForParser.premiseList.map(deleteObject(_, transversalState))
      knowledgeSentenceSetForParser.claimList.map(deleteObject(_, transversalState))
      logger.info(ToposoidUtils.formatMessageForLogger("RollBack completed", transversalState.userId))
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger("RollBack failed: " + Json.toJson(knowledgeSentenceSetForParser).toString(), transversalState.userId), e)
      }
    }
  }

  private def convertKnowledge(knowledge:Knowledge):Knowledge = {
    val knowledgeForImages: List[KnowledgeForImage] = knowledge.knowledgeForImages.map(y => {
      val imageFeatureId = UUID.random.toString
      KnowledgeForImage(imageFeatureId, y.imageReference)
    })
    Knowledge(knowledge.sentence, knowledge.lang, knowledge.extentInfoJson, knowledge.isNegativeSentence, knowledgeForImages)
  }
  private def assignId(knowledgeSentenceSet:KnowledgeSentenceSet):KnowledgeSentenceSetForParser = {
    val propositionId = UUID.random.toString
    val knowledgeForParserPremise: List[KnowledgeForParser] = knowledgeSentenceSet.premiseList.map(x => KnowledgeForParser(propositionId, UUID.random.toString, convertKnowledge(x)))
    val knowledgeForParserClaim: List[KnowledgeForParser] = knowledgeSentenceSet.claimList.map(x => KnowledgeForParser(propositionId, UUID.random.toString, convertKnowledge(x)))

    KnowledgeSentenceSetForParser(
      premiseList = knowledgeForParserPremise,
      premiseLogicRelation = knowledgeSentenceSet.premiseLogicRelation,
      claimList = knowledgeForParserClaim,
      claimLogicRelation = knowledgeSentenceSet.claimLogicRelation
    )
  }

  SqsSource(queueUrl, settings)
    .map(MessageAction.Delete(_))
    .via(SqsAckFlow(queueUrl))
    .runWith(Sink.foreach { res: SqsAckResult =>
      val body = res.messageAction.message.body
      val knowledgeRegistration: KnowledgeRegistration = Json.parse(body).as[KnowledgeRegistration]
      val knowledgeSentenceSetForParser = assignId(knowledgeRegistration.knowledgeSentenceSet)
      val transversalState = knowledgeRegistration.transversalState
      try {
        registerKnowledge(knowledgeSentenceSetForParser, transversalState)
        logger.info(ToposoidUtils.formatMessageForLogger("Registration completed", transversalState.userId))
      }catch {
        case e: Exception => {
          logger.error(ToposoidUtils.formatMessageForLogger(e.toString(), transversalState.userId), e)
          rollback(knowledgeSentenceSetForParser, transversalState)
        }
      }
    })

}

