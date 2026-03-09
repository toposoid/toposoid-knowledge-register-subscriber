/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ideal.linked.toposoid.mq

import java.net.URI
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.ToposoidUtils.{assignId, callComponent}
import com.ideal.linked.toposoid.common.mq.{KnowledgeRegistration, KnowledgeRegistrationForManual}
import com.ideal.linked.toposoid.common.{Neo4JUtilsImpl, ToposoidUtils, TransversalState, ActionModeType}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeForImage, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.KnowledgeRegisterHistoryRecord
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObjects, DeductionConfiguration}
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.{AnalyzedPropositionPair, AnalyzedPropositionSet, Sentence2Neo4jTransformer}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
//import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
//import io.jvm.uuid.UUID

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import org.apache.pekko.stream.connectors.awsspi.PekkoHttpClient
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.sqs.SqsSourceSettings
import org.apache.pekko.stream.connectors.sqs.scaladsl.SqsSource
import scala.collection.immutable
import org.apache.pekko.stream.connectors.sqs.MessageAction
import org.apache.pekko.stream.connectors.sqs.scaladsl.SqsAckFlow
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.connectors.sqs.SqsAckResult

object KnowledgeRegisterSubscriber extends App {
//object KnowledgeRegisterSubscriber extends App with LazyLogging{  処理が途中で止まってしまう現象あり
  val endpoint = "http://" + conf.getString("TOPOSOID_MQ_HOST") + ":" + conf.getString("TOPOSOID_MQ_PORT")
  implicit val actorSystem:ActorSystem = ActorSystem.create()
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val credentialsProvider:StaticCredentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x"))
  implicit val sqsClient:SqsAsyncClient = SqsAsyncClient
    .builder()
    .credentialsProvider(credentialsProvider)   
    .endpointOverride(URI.create(endpoint))   
    .region(Region.AP_NORTHEAST_1)
    .httpClient(PekkoHttpClient.builder().withActorSystem(actorSystem).build())
    // Possibility to configure the retry policy
    // see https://pekko.apache.org/docs/pekko-connectors/current/aws-shared-configuration.html
    // .overrideConfiguration(...)
    .build()
  /*
  implicit val sqsClient:SqsAsyncClient = SqsAsyncClient
    .builder()
    .credentialsProvider(
      StaticCredentialsProvider.create(
        AwsBasicCredentials.create(conf.getString("TOPOSOID_MQ_ACCESS_KEY"), conf.getString("TOPOSOID_MQ_SECRET_KEY")) // (1)
      )
    )
    .endpointOverride(URI.create(endpoint)) // (2)
    .region(Region.AP_NORTHEAST_1)
    //.httpClient(AkkaHttpClient.builder()
    .httpClient(PekkoHttpClient.builder().withActorSystem(actorSystem).build())
    .withActorSystem(actorSystem).build())
    .build()
  */
  val queueUrl = endpoint + "/" + conf.getString("TOPOSOID_MQ_KNOWLEDGE_REGISTER_QUENE")
  val settings = SqsSourceSettings().withCloseOnEmptyReceive(false)
  //private val langPatternJP: Regex = "^ja_.*".r
  //private val langPatternEN: Regex = "^en_.*".r
  //private val langPatternSpecialSymbol1: Regex = "^@@_#[0-9]+".r

  

  val messages = SqsSource(queueUrl, settings)
    .map(MessageAction.Delete(_))
    .via(SqsAckFlow(queueUrl))
    .runWith(Sink.foreach { (res: SqsAckResult) => {
      val logger = Logger(LoggerFactory.getLogger(this.getClass))
      val body = res.messageAction.message.body
      val knowledgeRegistrationForManual: KnowledgeRegistrationForManual = Json.parse(body).as[KnowledgeRegistrationForManual]
      val (knowledgeSentenceSetForParser, propositionId) = assignId(knowledgeRegistrationForManual.knowledgeSentenceSet)
      val transversalState = knowledgeRegistrationForManual.transversalState
      
      def classifyKnowledgeBySentenceType(premiseList: List[AnalyzedPropositionPair], premiseLogicRelation: List[PropositionRelation],
                                                  claimList: List[AnalyzedPropositionPair], claimLogicRelation: List[PropositionRelation]): AnalyzedPropositionSet = {
        //TODO:マイクロサービス化
        //Claim側の情報から、Premiseの情報を追加する。        
        AnalyzedPropositionSet(premiseList = premiseList, premiseLogicRelation = premiseLogicRelation, claimList = claimList, claimLogicRelation = claimLogicRelation)
      }

      def getAnalyzedPropositionPairs(knowledgeForParsers:List[KnowledgeForParser], transversalState:TransversalState):List[AnalyzedPropositionPair] = {

        knowledgeForParsers.foldLeft(List.empty[AnalyzedPropositionPair]) {
          (acc, x) => {
            //SentenceParserで解析
            val knowledgeForParser: KnowledgeForParser = x
            val inputSentenceForParser = InputSentenceForParser(List.empty[KnowledgeForParser], List(knowledgeForParser), ActionModeType.REGISTRATION_MODE.index)
            val json: String = Json.toJson(inputSentenceForParser).toString()
            val analyzedSentenceObjects:AnalyzedSentenceObjects = knowledgeForParser.knowledge.lang match{
              case ToposoidUtils.langPatternJP() => {
                val host = conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST")
                val port = conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT")
                val parseResult: String = ToposoidUtils.callComponent(json, host, port, "analyze", transversalState)
                Json.parse(parseResult).as[AnalyzedSentenceObjects]
              }
              case ToposoidUtils.langPatternEN() => {
                val host = conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST")
                val port = conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT")
                val parseResult: String = ToposoidUtils.callComponent(json, host, port, "analyze", transversalState)
                Json.parse(parseResult).as[AnalyzedSentenceObjects]
              }
              case ToposoidUtils.langPatternSpecialSymbol1() => {
                val aso = ToposoidUtils.parseSpecialSymbol(knowledgeForParser)
                val deductionCofiguration = DeductionConfiguration(inputSentenceForParser.actionModeType, "", Map.empty[String,String]) 
                AnalyzedSentenceObjects(List(aso), deductionCofiguration)
              }
              case _ => throw new Exception("It is an invalid locale or an unsupported locale.")
            }

            val analyzedPropositionPair: AnalyzedPropositionPair = AnalyzedPropositionPair(analyzedSentenceObjects, knowledgeForParser)
            acc :+ analyzedPropositionPair
          }
        }
      }

      def registerKnowledge(knowledgeSentenceSetForParser:KnowledgeSentenceSetForParser, transversalState:TransversalState) = Try {
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

      def registKnowledgeImages(knowledgeForParsers: List[KnowledgeForParser], transversalState: TransversalState): List[KnowledgeForParser] = Try {

        knowledgeForParsers.foldLeft(List.empty[KnowledgeForParser]) {
          (acc, x) => {
            val knowledgeForImages: List[KnowledgeForImage] = x.knowledge.knowledgeForImages.map(y => {
              val imageFeatureId = java.util.UUID.randomUUID().toString
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

      def deleteObject(knowledgeForParser: KnowledgeForParser, transversalState:TransversalState) = {
        //Delete relationships
        val query = s"MATCH (n)-[r]-() WHERE n.propositionId = '${knowledgeForParser.propositionId}' DELETE n,r"
        val neo4JUtils = new Neo4JUtilsImpl()
        neo4JUtils.executeQuery(query, transversalState)
        //Delete orphan nodes
        val query2 = s"MATCH (n) WHERE n.propositionId = '${knowledgeForParser.propositionId}' DELETE n"
        neo4JUtils.executeQuery(query2, transversalState)
        FeatureVectorizer.removeVectorByPropositionId(knowledgeForParser, transversalState)
      }

      def rollback(knowledgeSentenceSetForParser:KnowledgeSentenceSetForParser, transversalState:TransversalState)= {
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

      def getSentence(knowledgeSentenceSet:KnowledgeSentenceSet):String = {
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
      def add(stateId:Int, propositionId:String,  knowledgeRegistrationForManual: KnowledgeRegistrationForManual ):Unit = Try {
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
      
      
      try {
        logger.info(ToposoidUtils.formatMessageForLogger(body, transversalState.userId))
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
    }})
    Await.result(messages, Duration.Inf)    
    
    messages.onComplete(_ => actorSystem.terminate())
}

