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

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.mq.{KnowledgeRegistration, KnowledgeRegistrationForManual, MqUtils}
import com.ideal.linked.toposoid.common.{FeatureType, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, KnowledgeSentenceSet, PropositionRelation, Reference}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
//import com.typesafe.sslconfig.ssl.AlgorithmConstraintsParser.operator
//import io.jvm.uuid.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import play.api.libs.json.Json
import com.ideal.linked.toposoid.mq.TestUtilsEx.uploadImage
import com.ideal.linked.toposoid.mq.TestUtilsEx.uploadTable
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable
import com.ideal.linked.toposoid.knowledgebase.regist.model.TableReference

class SubscriberEnglishTest extends AnyFlatSpec with BeforeAndAfter with BeforeAndAfterAll {

  /**
   * TODO:CAUTION!!!!
   * When testing locally, be sure to run sbt "runMain com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber" from the terminal
   */

  val transversalState: TransversalState = TransversalState(userId = "test-user", username = "guest", roleId = 0, csrfToken = "")
  //Unless you change the Json payload somewhere, it will not be subject to MQ.


  override def beforeAll(): Unit = {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    //TestUtilsEx.deleteNeo4JAllData(transversalState)
  }

  "the request json" should "be properly registered in the English knowledge database and searchable." in {


    val knowledge1 = Knowledge(sentence = "This is premise-1.", lang = "en_US", extentInfoJson = "{}")
    val knowledge2 = Knowledge(sentence = "This is premise-2.", lang = "en_US", extentInfoJson = "{}")
    val reference3 = Reference(url = "", surface = "cats", surfaceIndex = 3, isWholeSentence = false, originalUrlOrReference = "src/test/resources/IMG_TEST.jpg", metaInformations = List.empty[String])
    val imageReference3 = ImageReference(reference = reference3, x = 27, y = 41, width = 287, height = 435)
    val knowledgeForImages3 = uploadImage(KnowledgeForImage(id = "", imageReference = imageReference3), transversalState)
    val knowledge3 = Knowledge(sentence = "There are two cats.", lang = "en_US", extentInfoJson = "{}", knowledgeForImages = List(knowledgeForImages3))

    val reference3a = Reference(url = "", surface = "data", surfaceIndex = 3, isWholeSentence = false, originalUrlOrReference = "src/test/resources/ENGLISH_TEST_TABLE.tsv", metaInformations = List.empty[String])
    val tableReference3a = TableReference(reference = reference3a)
    val knowledgeForTable3a = uploadTable(KnowledgeForTable(id = "", tableReference = tableReference3a), transversalState)
    val knowledge3a = Knowledge(sentence = "There is a data.", lang = "en_US", extentInfoJson = "{}", knowledgeForTables=List(knowledgeForTable3a))


    val knowledge4 = Knowledge(sentence = "This is claim-1.", lang = "en_US", extentInfoJson = "{}")
    val knowledge5 = Knowledge(sentence = "This is claim-2.", lang = "en_US", extentInfoJson = "{}")
    val reference6 = Reference(url = "", surface = "dog", surfaceIndex = 3, isWholeSentence = false, originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg", metaInformations = List.empty[String])
    val reference6a = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true, originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg", metaInformations = List.empty[String])
    val imageReference6 = ImageReference(reference = reference6, x = 435, y = 227, width = 91, height = 69)
    val knowledgeForImages6 = uploadImage(KnowledgeForImage(id = "", imageReference = imageReference6), transversalState)
    val knowledge6 = Knowledge(sentence = "There is a dog", lang = "en_US", extentInfoJson = "{}", knowledgeForImages = List(knowledgeForImages6))
    val imageReference6a = ImageReference(reference = reference6a, x = 23, y = 25, width = 601, height = 341)
    val knowledgeForImages6a = uploadImage(KnowledgeForImage(id = "", imageReference = imageReference6a), transversalState)
    val knowledge6a = Knowledge(sentence = "NO_REFERENCE_5d9afee2-4c10-11f0-9f26-acde48001122_10", lang = "@@_#1", extentInfoJson = "{}", knowledgeForImages = List(knowledgeForImages6a))

    val knowledgeSentenceSet = KnowledgeSentenceSet(
      premiseList = List(knowledge1, knowledge2, knowledge3, knowledge3a),
      premiseLogicRelation = List(PropositionRelation(operator = "AND", sourceIndex = 0, destinationIndex = 1), PropositionRelation(operator = "AND", sourceIndex = 0, destinationIndex = 2)),
      claimList = List(knowledge4, knowledge5, knowledge6, knowledge6a),
      claimLogicRelation = List(PropositionRelation(operator = "OR", sourceIndex = 0, destinationIndex = 1), PropositionRelation(operator = "AND", sourceIndex = 0, destinationIndex = 2))
    )
    val knowledgeRegistrationForManual = KnowledgeRegistrationForManual(knowledgeSentenceSet = knowledgeSentenceSet, transversalState = transversalState)
    val jsonStr = Json.toJson(knowledgeRegistrationForManual).toString()


    MqUtils.publishMessage(jsonStr,conf.getString("TOPOSOID_MQ_HOST"), conf.getString("TOPOSOID_MQ_PORT"), conf.getString("TOPOSOID_MQ_KNOWLEDGE_REGISTER_QUENE"))
    Thread.sleep(70000)
    val query = "MATCH x=(:ClaimNode{surface:'claim-1'})-[:LocalEdge]-(:ClaimNode)-[:LocalEdge{logicType:'OR'}]-(:ClaimNode)-[:LocalEdge]-(:ClaimNode{surface:'claim-2'}) return x"
    val queryResult: Neo4jRecords = TestUtilsEx.executeQueryAndReturn(query, transversalState)
    assert(queryResult.records.size == 1)
    val query2 = "MATCH x=(:PremiseNode{surface:'premise-1'})-[:LocalEdge]-(:PremiseNode)-[:LocalEdge{logicType:'AND'}]-(:PremiseNode)-[:LocalEdge]-(:PremiseNode{surface:'premise-2'}) return x"
    val queryResult2: Neo4jRecords = TestUtilsEx.executeQueryAndReturn(query2, transversalState)
    assert(queryResult2.records.size == 1)
    val query3 = "MATCH x=(:PremiseNode{surface:'premise-1'})-[:LocalEdge]-(:PremiseNode)-[:LocalEdge{logicType:'IMP'}]-(:ClaimNode)-[:LocalEdge]-(:ClaimNode{surface:'claim-1'}) return x"
    val queryResult3: Neo4jRecords = TestUtilsEx.executeQueryAndReturn(query3, transversalState)
    assert(queryResult3.records.size == 1)
    val result4: Neo4jRecords = TestUtilsEx.executeQueryAndReturn("MATCH (n:ClaimNode{surface: 'NO_REFERENCE_5d9afee2-4c10-11f0-9f26-acde48001122_10'}) RETURN n", transversalState)
    assert(result4.records.size == 1)

    val result5: Neo4jRecords = TestUtilsEx.executeQueryAndReturn("MATCH (s:TableNode{source:'src/test/resources/ENGLISH_TEST_TABLE.tsv'})-[:TableEdge]->(t:PremiseNode{surface:'data'}) RETURN s, t", transversalState)
    val urlTable1 = result5.records.head.head.value.featureNode.get.url
    assert(result5.records.size == 1)


    val queryResult4: Neo4jRecords = TestUtilsEx.executeQueryAndReturn("MATCH (s:ImageNode{source:'src/test/resources/IMG_TEST.jpg'})-[:ImageEdge]->(t:PremiseNode{surface:'cats'}) RETURN s, t", transversalState)
    assert(queryResult4.records.size == 1)
    val urlCat = queryResult4.records.head.head.value.featureNode.get.url

    val queryResult5: Neo4jRecords = TestUtilsEx.executeQueryAndReturn("MATCH (s:ImageNode{source:'http://images.cocodataset.org/train2017/000000428746.jpg'})-[:ImageEdge]->(t:ClaimNode{surface:'dog'}) RETURN s, t", transversalState)
    assert(queryResult5.records.size == 1)
    val urlDog = queryResult5.records.head.head.value.featureNode.get.url

    val queryResult6: Neo4jRecords = TestUtilsEx.executeQueryAndReturn("MATCH (s:ImageNode{source:'https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg'})-[:ImageEdge]->(t:SemiGlobalClaimNode) RETURN s, t", transversalState)
    assert(queryResult6.records.size == 1)
    val urlTrack = queryResult6.records.head.head.value.featureNode.get.url


    for (knowledge <- knowledgeSentenceSet.premiseList ::: knowledgeSentenceSet.claimList) {
      val vector = FeatureVectorizer.getSentenceVector(Knowledge(knowledge.sentence, "en_US", "{}"), transversalState)
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = 1)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
      assert(result.ids.size > 0 && result.similarities.head > 0.999)
      result.ids.map(x => TestUtilsEx.deleteFeatureVector(x, FeatureType.SENTENCE, transversalState))

      knowledge.knowledgeForImages.foreach(x => {
        val url: String = x.imageReference.reference.surface match {
          case "cats" => urlCat
          case "dog" => urlDog
          case "" => urlTrack
          case _ => "BAD URL"
        }
        val vector = TestUtilsEx.getImageVector(url, transversalState)
        val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = 1)).toString()
        val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
        val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
        assert(result.ids.size > 0 && result.similarities.head > 0.999)
        result.ids.map(x => TestUtilsEx.deleteFeatureVector(x, FeatureType.IMAGE, transversalState))
      })

      knowledge.knowledgeForTables.foreach(x => {
        val url: String = x.tableReference.reference.surface match {
          case "data" => urlTable1
          case _ => "BAD URL"
        }
        val vector = TestUtilsEx.getTableVector(url, transversalState)
        val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = 1)).toString()
        val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
        val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
        assert(result.ids.size > 0 && result.similarities.head > 0.999)
        result.ids.map(x => TestUtilsEx.deleteFeatureVector(x, FeatureType.TABLE, transversalState))
      })


      val propositionIds = result.ids.map(_.superiorId).distinct
      assert(propositionIds.size == 1)

      //Check RDB registration information
      val knowledgeRegisterHistoryRecords = TestUtilsEx.checkRDB(propositionIds.head, transversalState)
      assert(knowledgeRegisterHistoryRecords.size == 1)
      assert(knowledgeRegisterHistoryRecords.head.propositionId.equals(propositionIds.head))
      assert(knowledgeRegisterHistoryRecords.head.stateId == 1)

    }


  }

}
