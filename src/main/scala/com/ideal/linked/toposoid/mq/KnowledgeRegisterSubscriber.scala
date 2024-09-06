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
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import io.jvm.uuid.UUID

import scala.util.{Failure, Success, Try}


object KnowledgeRegisterSubscriber extends App with LazyLogging {
  val endpoint = "http://192.168.33.10:9324"
  implicit val actorSystem = ActorSystem("example")

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


  private def registerKnowledge(json:String): Unit = {
    try {
      val knowledgeRegistration: KnowledgeRegistration = Json.parse(json.toString).as[KnowledgeRegistration]
      val knowledgeSentenceSet = knowledgeRegistration.knowledgeSentenceSet
      val transversalState = knowledgeRegistration.transversalState
      val propositionId = UUID.random.toString
      val knowledgeForParserPremise: List[KnowledgeForParser] = knowledgeSentenceSet.premiseList.map(KnowledgeForParser(propositionId, UUID.random.toString, _))
      val knowledgeForParserClaim: List[KnowledgeForParser] = knowledgeSentenceSet.claimList.map(KnowledgeForParser(propositionId, UUID.random.toString, _))
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        registKnowledgeImages(knowledgeForParserPremise, transversalState),
        knowledgeSentenceSet.premiseLogicRelation,
        registKnowledgeImages(knowledgeForParserClaim, transversalState),
        knowledgeSentenceSet.claimLogicRelation)

      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, transversalState)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)
    } catch {
      case e: Exception => {
        logger.error(e.toString, e)
      }
    }
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

  SqsSource(queueUrl, settings)
    .map(MessageAction.Delete(_))
    .via(SqsAckFlow(queueUrl))
    .runWith(Sink.foreach { res: SqsAckResult =>
      val body = res.messageAction.message.body
      println(s"received: ${body}")
      registerKnowledge(body)

    })

}

