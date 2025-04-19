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
import com.ideal.linked.toposoid.common.mq.{KnowledgeRegistration, KnowledgeRegistrationForManual}
import com.ideal.linked.toposoid.common.{ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeForImage, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.KnowledgeRegisterHistoryRecord
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.{AnalyzedPropositionPair, AnalyzedPropositionSet, Neo4JUtilsImpl, Sentence2Neo4jTransformer}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import io.jvm.uuid.UUID

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}


object KnowledgeRegisterSubscriber extends App with LazyLogging {
  val endpoint = "http://" + conf.getString("TOPOSOID_MQ_HOST") + ":" + conf.getString("TOPOSOID_MQ_PORT")
  implicit val actorSystem = ActorSystem("example")

  implicit val sqsClient = SqsAsyncClient
    .builder()
    .credentialsProvider(
      StaticCredentialsProvider.create(
        AwsBasicCredentials.create(conf.getString("TOPOSOID_MQ_ACCESS_KEY"), conf.getString("TOPOSOID_MQ_SECRET_KEY")) // (1)
      )
    )
    .endpointOverride(URI.create(endpoint)) // (2)
    .region(Region.AP_NORTHEAST_1)
    .httpClient(AkkaHttpClient.builder()
      .withActorSystem(actorSystem).build())
    .build()

  val queueUrl = endpoint + "/" + conf.getString("TOPOSOID_MQ_KNOWLEDGE_REGISTER_QUENE")
  val settings = SqsSourceSettings()
  private val langPatternJP: Regex = "^ja_.*".r
  private val langPatternEN: Regex = "^en_.*".r

  private def classifyKnowledgeBySentenceType(premiseList: List[AnalyzedPropositionPair], premiseLogicRelation: List[PropositionRelation],
                                              claimList: List[AnalyzedPropositionPair], claimLogicRelation: List[PropositionRelation]): AnalyzedPropositionSet = {
    //TODO:マイクロサービス化
    //Claim側の情報から、Premiseの情報を追加する。
    AnalyzedPropositionSet(premiseList = premiseList, premiseLogicRelation = premiseLogicRelation, claimList = claimList, claimLogicRelation = claimLogicRelation)
  }

  private def getAnalyzedPropositionPairs(knowledgeForParsers:List[KnowledgeForParser], transversalState:TransversalState):List[AnalyzedPropositionPair] = {

    knowledgeForParsers.foldLeft(List.empty[AnalyzedPropositionPair]) {
      (acc, x) => {
        //SentenceParserで解析
        val knowledgeForParser: KnowledgeForParser = x
        val inputSentenceForParser = InputSentenceForParser(List.empty[KnowledgeForParser], List(knowledgeForParser))
        val json: String = Json.toJson(inputSentenceForParser).toString()
        val parserInfo: (String, String) = knowledgeForParser.knowledge.lang match {
          case langPatternJP() => (conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"))
          case langPatternEN() => (conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"))
          case _ => throw new Exception("It is an invalid locale or an unsupported locale.")
        }
        val parseResult: String = ToposoidUtils.callComponent(json, parserInfo._1, parserInfo._2, "analyze", transversalState)
        val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(parseResult).as[AnalyzedSentenceObjects]

        val analyzedPropositionPair: AnalyzedPropositionPair = AnalyzedPropositionPair(analyzedSentenceObjects, knowledgeForParser)
        acc :+ analyzedPropositionPair
      }
    }
  }

  private def registerKnowledge(knowledgeSentenceSetForParser:KnowledgeSentenceSetForParser, transversalState:TransversalState) = Try {
    val knowledgeSentenceSetForParserWithImage = KnowledgeSentenceSetForParser(
      registKnowledgeImages(knowledgeSentenceSetForParser.premiseList, transversalState),
      knowledgeSentenceSetForParser.premiseLogicRelation,
      registKnowledgeImages(knowledgeSentenceSetForParser.claimList, transversalState),
      knowledgeSentenceSetForParser.claimLogicRelation)

    val premiseAnalyzedPropositionPairs = getAnalyzedPropositionPairs(knowledgeSentenceSetForParserWithImage.premiseList, transversalState)
    val claimAnalyzedPropositionPairs = getAnalyzedPropositionPairs(knowledgeSentenceSetForParserWithImage.claimList, transversalState)

    val classifiedKnowledgeBySentenceType = classifyKnowledgeBySentenceType(
      premiseList = premiseAnalyzedPropositionPairs,
      premiseLogicRelation = knowledgeSentenceSetForParser.premiseLogicRelation,
      claimList = claimAnalyzedPropositionPairs,
      claimLogicRelation = knowledgeSentenceSetForParser.claimLogicRelation
    )
    Sentence2Neo4jTransformer.createGraph(classifiedKnowledgeBySentenceType, transversalState)
    FeatureVectorizer.createVector(knowledgeSentenceSetForParserWithImage, transversalState)
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
    //Delete relationships
    val query = s"MATCH (n)-[r]-() WHERE n.propositionId = '${knowledgeForParser.propositionId}' DELETE n,r"
    val neo4JUtils = new Neo4JUtilsImpl()
    neo4JUtils.executeQuery(query, transversalState)
    //Delete orphan nodes
    val query2 = s"MATCH (n) WHERE n.propositionId = '${knowledgeForParser.propositionId}' DELETE n"
    neo4JUtils.executeQuery(query2, transversalState)
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
  private def assignId(knowledgeSentenceSet:KnowledgeSentenceSet):(KnowledgeSentenceSetForParser, String) = {
    val propositionId = UUID.random.toString
    val knowledgeForParserPremise: List[KnowledgeForParser] = knowledgeSentenceSet.premiseList.map(x => KnowledgeForParser(propositionId, UUID.random.toString, convertKnowledge(x)))
    val knowledgeForParserClaim: List[KnowledgeForParser] = knowledgeSentenceSet.claimList.map(x => KnowledgeForParser(propositionId, UUID.random.toString, convertKnowledge(x)))

    (KnowledgeSentenceSetForParser(
      premiseList = knowledgeForParserPremise,
      premiseLogicRelation = knowledgeSentenceSet.premiseLogicRelation,
      claimList = knowledgeForParserClaim,
      claimLogicRelation = knowledgeSentenceSet.claimLogicRelation
    ), propositionId)
  }

  private def getSentence(knowledgeSentenceSet:KnowledgeSentenceSet):String = {
    val premiseSentence = knowledgeSentenceSet.premiseList.foldLeft(""){
      (acc, x) => {
        acc + x.sentence
      }
    }
    val claimSentence = knowledgeSentenceSet.claimList.foldLeft("") {
      (acc, x) => {
        acc + x.sentence
      }
    }
    premiseSentence + claimSentence
  }
  private def add(stateId:Int, propositionId:String,  knowledgeRegistrationForManual: KnowledgeRegistrationForManual ):Unit = Try {
    val knowledgeRegisterHistoryRecord = KnowledgeRegisterHistoryRecord(
      stateId = stateId,
      documentId = "",
      sequentialNumber = 1,
      propositionId = propositionId,
      sentences = getSentence(knowledgeRegistrationForManual.knowledgeSentenceSet),
      //json = ""
      json = Json.toJson(knowledgeRegistrationForManual).toString()
    )
    val json = Json.toJson(knowledgeRegisterHistoryRecord).toString()
    val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "addKnowledgeRegisterHistory", knowledgeRegistrationForManual.transversalState)
    if (result.contains("Error")) throw new Exception(result)
  } match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  SqsSource(queueUrl, settings)
    .map(MessageAction.Delete(_))
    .via(SqsAckFlow(queueUrl))
    .runWith(Sink.foreach { res: SqsAckResult =>
      val body = res.messageAction.message.body
      val knowledgeRegistrationForManual: KnowledgeRegistrationForManual = Json.parse(body).as[KnowledgeRegistrationForManual]
      val (knowledgeSentenceSetForParser, propositionId) = assignId(knowledgeRegistrationForManual.knowledgeSentenceSet)
      val transversalState = knowledgeRegistrationForManual.transversalState
      try {
        registerKnowledge(knowledgeSentenceSetForParser, transversalState)
        add(1, propositionId, knowledgeRegistrationForManual)
        logger.info(ToposoidUtils.formatMessageForLogger("Registration completed", transversalState.userId))
      } catch {
        case e: Exception => {
          logger.error(ToposoidUtils.formatMessageForLogger(e.toString(), transversalState.userId), e)
          rollback(knowledgeSentenceSetForParser, transversalState)
          add(2, propositionId, knowledgeRegistrationForManual)
        }
      }
    })


  /*
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
        add(1, knowledgeRegistration, knowledgeSentenceSetForParser)
        logger.info(ToposoidUtils.formatMessageForLogger("Registration completed", transversalState.userId))
      }catch {
        case e: Exception => {
          logger.error(ToposoidUtils.formatMessageForLogger(e.toString(), transversalState.userId), e)
          rollback(knowledgeSentenceSetForParser, transversalState)
          add(2, knowledgeRegistration, knowledgeSentenceSetForParser)
        }
      }
    })
  */
}

