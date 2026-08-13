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

//import akka.actor.ActorSystem
//import com.github.matsluni.akkahttpspi.AkkaHttpClient
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{FeatureType,Neo4JUtilsImpl,ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.FeatureVectorIdentifier
import com.ideal.linked.toposoid.knowledgebase.image.model.SingleImage
import com.ideal.linked.toposoid.knowledgebase.nlp.model.FeatureVector
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.KnowledgeRegisterHistoryRecord
import com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber.endpoint
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import play.api.libs.json.Json

import java.net.URI
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import com.ideal.linked.toposoid.knowledgebase.table.model.SingleTable
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForImage
import sttp.client4._
import sttp.model._
import com.ideal.linked.toposoid.knowledgebase.regist.model.Reference
import com.ideal.linked.toposoid.knowledgebase.regist.model.TableReference
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable
import play.api.libs.json.{Json, OWrites, Reads}
import com.ideal.linked.toposoid.common.TRANSVERSAL_STATE
import java.nio.file.Path
import scala.concurrent.duration.{Duration, DurationInt}
import com.ideal.linked.toposoid.knowledgebase.regist.model.ImageReference

case class UploadResult(id: String, url:String, status:Int)
object UploadResult {
  implicit val jsonWrites: OWrites[UploadResult] = Json.writes[UploadResult]
  implicit val jsonReads: Reads[UploadResult] = Json.reads[UploadResult]
}

object TestUtilsEx {
  val neo4JUtils = new Neo4JUtilsImpl()
  def deleteNeo4JAllData(transversalState:TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    neo4JUtils.executeQuery(query, transversalState)
  }

  def executeQueryAndReturn(query:String, transversalState:TransversalState): Neo4jRecords = {
    neo4JUtils.executeQueryAndReturn(query:String, transversalState:TransversalState)
  }

  def deleteFeatureVector(featureVectorIdentifier: FeatureVectorIdentifier, featureType: FeatureType, transversalState:TransversalState): Unit = {
    val json: String = Json.toJson(featureVectorIdentifier).toString()
    if (featureType.equals(FeatureType.SENTENCE)) {
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "delete", transversalState)
    } else if (featureType.equals(FeatureType.IMAGE)) {
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "delete", transversalState)
    } else if (featureType.equals(FeatureType.TABLE)) {
      ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_TALBE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_PORT"), "delete", transversalState)
    }
  }

  def getImageVector(url: String, transversalState:TransversalState): FeatureVector = {
    val singleImage = SingleImage(url)
    val json: String = Json.toJson(singleImage).toString()
    val featureVectorJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_COMMON_IMAGE_RECOGNITION_HOST"), conf.getString("TOPOSOID_COMMON_IMAGE_RECOGNITION_PORT"), "getFeatureVector", transversalState)
    Json.parse(featureVectorJson).as[FeatureVector]
  }

  def getTableVector(url: String, transversalState:TransversalState): FeatureVector = {
    val singleTable = SingleTable(url)
    val json: String = Json.toJson(singleTable).toString()
    val featureVectorJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_COMMON_TABLE_RECOGNITION_HOST"), conf.getString("TOPOSOID_COMMON_TABLE_RECOGNITION_PORT"), "getFeatureVector", transversalState)
    Json.parse(featureVectorJson).as[FeatureVector]
  }

  def checkRDB(propositionId:String, transversalState:TransversalState):List[KnowledgeRegisterHistoryRecord] = {
    val knowledgeRegisterHistoryRecord = KnowledgeRegisterHistoryRecord(
      stateId = -1,
      documentId = "",
      sequentialNumber = -1,
      propositionId = propositionId,
      sentences = "",
      json = "")
    val json = Json.toJson(knowledgeRegisterHistoryRecord).toString()
    val result = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_RDB_WEB_HOST"), conf.getString("TOPOSOID_RDB_WEB_PORT"), "searchKnowledgeRegisterHistoryByPropositionId", transversalState)
    Json.parse(result).as[List[KnowledgeRegisterHistoryRecord]]
  }

  def uploadImage(knowledgeForImage: KnowledgeForImage, transversalState: TransversalState): KnowledgeForImage = {
    
    val endpoint = "http://" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_HOST") + ":" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_PORT") + "/upload"    
    val backend = DefaultSyncBackend(
      options = BackendOptions.connectionTimeout(1.minute))
    val request = basicRequest
    .header(TRANSVERSAL_STATE.str, Json.toJson(transversalState).toString())      
    .httpVersion(HttpVersion.HTTP_1_1)
    .post(uri"${endpoint}") // Replace with your upload endpoint
    .multipartBody(
        multipart("featureType", FeatureType.IMAGE.index.toString),
        multipart("url", knowledgeForImage.imageReference.reference.originalUrlOrReference), // デフォルト値を明示的に送る場合              
    )
    val response = request.send(backend)
    val responseJson = response.body match {
      case Right(successBody) => s"$successBody"
      case Left(errorBody) => s"Upload failed. Status code: ${response.code}. Error body: $errorBody"
    }

    val uploadResult = Json.parse(responseJson).as[UploadResult]

    val reference = Reference(url = uploadResult.url, surface = "", surfaceIndex = -1, isWholeSentence = false, originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg", metaInformations = List.empty[String])
    val imageReference = ImageReference(reference = reference, x = 0, y = 0, width = 640, height = 480)
    KnowledgeForImage(id = uploadResult.id, imageReference = imageReference)
  }

  def uploadTable(file:Path, transversalState: TransversalState): KnowledgeForTable = {

    val endpoint = "http://" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_HOST") + ":" + conf.getString("TOPOSOID_FILE_UPLOAD_FACADE_PORT") + "/upload"    
    val backend = DefaultSyncBackend(
      options = BackendOptions.connectionTimeout(1.minute))
    val request = basicRequest
    .header(TRANSVERSAL_STATE.str, Json.toJson(transversalState).toString())      
    .httpVersion(HttpVersion.HTTP_1_1)
    .post(uri"${endpoint}") // Replace with your upload endpoint
    .multipartBody(
        multipart("featureType", FeatureType.TABLE.index.toString),
        multipart("url", ""), // デフォルト値を明示的に送る場合     
        multipartFile("uploadfile", file.toFile()).fileName(file.getFileName().toString()).contentType("application/octet-stream") // "file" is the field name on the server         
    )
    val response = request.send(backend)
    val responseJson = response.body match {
      case Right(successBody) => s"$successBody"
      case Left(errorBody) => s"Upload failed. Status code: ${response.code}. Error body: $errorBody"
    }

    val uploadResult = Json.parse(responseJson).as[UploadResult]
    val reference = Reference(url = uploadResult.url, surface = "", surfaceIndex = -1, isWholeSentence = false, originalUrlOrReference = file.getFileName().toString(), metaInformations = List.empty[String])
    val tableReference = TableReference(reference=reference)
    KnowledgeForTable(id = uploadResult.id, tableReference = tableReference)

  }

}
