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

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.mq.{KnowledgeRegistration, KnowledgeRegistrationForManual}
import com.ideal.linked.toposoid.common.{IMAGE, SENTENCE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, KnowledgeSentenceSet, PropositionRelation, Reference}
import com.ideal.linked.toposoid.knowledgebase.regist.rdb.model.KnowledgeRegisterHistoryRecord
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.flatspec.AnyFlatSpec
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import io.jvm.uuid.UUID
import play.api.libs.json.Json

class SubscriberJapaneseTest extends AnyFlatSpec with BeforeAndAfter with BeforeAndAfterAll {

  /**
   * TODO:CAUTION!!!!
   * When testing locally, be sure to run 'sbt "runMain com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber"' from the terminal
   */

  val transversalState: TransversalState = TransversalState(userId = "test-user", username = "guest", roleId = 0, csrfToken = "")
  //Unless you change the Json payload somewhere, it will not be subject to MQ.

  before {

  }

  override def beforeAll(): Unit = {
    TestUtils.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    TestUtils.deleteNeo4JAllData(transversalState)
  }


  "the request json" should "be properly registered in the Japanese knowledge database and searchable." in {

    val knowledge1 = Knowledge(sentence = "これはテストの前提1です。", lang = "ja_JP", extentInfoJson = "{}")
    val knowledge2 = Knowledge(sentence = "これはテストの前提2です。", lang = "ja_JP", extentInfoJson = "{}")
    val reference3 = Reference(url = "", surface = "猫が", surfaceIndex = 0, isWholeSentence = false, originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg", metaInformations = List.empty[String])
    val imageReference3 = ImageReference(reference = reference3, x = 27, y = 41, width = 287, height = 435)
    val knowledgeForImages3 = KnowledgeForImage(id = "", imageReference = imageReference3)
    val knowledge3 = Knowledge(sentence = "猫が２匹います。", lang = "ja_JP", extentInfoJson = "{}", knowledgeForImages=List(knowledgeForImages3))

    val knowledge4 = Knowledge(sentence = "これはテストの主張1です。", lang = "ja_JP", extentInfoJson = "{}")
    val knowledge5 = Knowledge(sentence = "これはテストの主張2です。", lang = "ja_JP", extentInfoJson = "{}")
    val reference6 = Reference(url = "", surface = "犬が", surfaceIndex = 0, isWholeSentence = false, originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg", metaInformations = List.empty[String])
    val imageReference6 = ImageReference(reference = reference6, x = 435, y = 227, width = 91, height = 69)
    val knowledgeForImages6 = KnowledgeForImage(id = "", imageReference = imageReference6)
    val knowledge6 = Knowledge(sentence = "犬が1匹います。", lang = "ja_JP", extentInfoJson = "{}", knowledgeForImages = List(knowledgeForImages6))

    val knowledgeSentenceSet = KnowledgeSentenceSet(
      premiseList = List(knowledge1, knowledge2, knowledge3),
      premiseLogicRelation = List(PropositionRelation(operator = "AND", sourceIndex = 0, destinationIndex = 1), PropositionRelation(operator = "AND", sourceIndex = 0, destinationIndex = 2)),
      claimList = List(knowledge4, knowledge5, knowledge6),
      claimLogicRelation = List(PropositionRelation(operator = "OR", sourceIndex = 0, destinationIndex = 1), PropositionRelation(operator = "AND", sourceIndex = 0, destinationIndex = 2))
    )
    val knowledgeRegistrationForManual = KnowledgeRegistrationForManual(knowledgeSentenceSet = knowledgeSentenceSet, transversalState = transversalState)
    val jsonStr = Json.toJson(knowledgeRegistrationForManual).toString()

    ToposoidUtils.publishMessage(jsonStr,conf.getString("TOPOSOID_MQ_HOST"), conf.getString("TOPOSOID_MQ_PORT"), conf.getString("TOPOSOID_MQ_KNOWLEDGE_REGISTER_QUENE"))
    //TestUtils.publishMessage(jsonStr.replace("\n",""))
    Thread.sleep(70000)
    val query = "MATCH x=(:ClaimNode{surface:'主張２です。'})<-[:LocalEdge{logicType:'OR'}]-(:ClaimNode{surface:'主張１です。'})<-[:LocalEdge{logicType:'IMP'}]-(:PremiseNode{surface:'前提１です。'})-[:LocalEdge{logicType:'AND'}]->(:PremiseNode{surface:'前提２です。'}) return x"
    val queryResult: Neo4jRecords = TestUtils.executeQueryAndReturn(query, transversalState)
    assert(queryResult.records.size == 1)
    val result2: Neo4jRecords = TestUtils.executeQueryAndReturn("MATCH (s:ImageNode{source:'http://images.cocodataset.org/val2017/000000039769.jpg'})-[:ImageEdge]->(t:PremiseNode{surface:'猫が'}) RETURN s, t", transversalState)
    assert(result2.records.size == 1)
    val urlCat = result2.records.head.head.value.featureNode.get.url
    val result3: Neo4jRecords = TestUtils.executeQueryAndReturn("MATCH (s:ImageNode{source:'http://images.cocodataset.org/train2017/000000428746.jpg'})-[:ImageEdge]->(t:ClaimNode{surface:'犬が'}) RETURN s, t", transversalState)
    assert(result3.records.size == 1)
    val urlDog = result3.records.head.head.value.featureNode.get.url

    for (knowledge <- knowledgeSentenceSet.premiseList ::: knowledgeSentenceSet.claimList) {
      val vector = FeatureVectorizer.getSentenceVector(Knowledge(knowledge.sentence, "ja_JP", "{}"), transversalState)
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = 1)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
      assert(result.ids.size > 0 && result.similarities.head > 0.999)
      result.ids.map(x => TestUtils.deleteFeatureVector(x, SENTENCE, transversalState))

      knowledge.knowledgeForImages.foreach(x => {
        val url: String = x.imageReference.reference.surface match {
          case "猫が" => urlCat
          case "犬が" => urlDog
          case _ => "BAD URL"
        }
        val vector = TestUtils.getImageVector(url, transversalState)
        val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = 1)).toString()
        val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
        val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
        assert(result.ids.size > 0 && result.similarities.head > 0.999)
        result.ids.map(x => TestUtils.deleteFeatureVector(x, IMAGE, transversalState))
      })

      val propositionIds = result.ids.map(_.superiorId).distinct
      assert(propositionIds.size == 1)

      //Check RDB registration information
      val knowledgeRegisterHistoryRecords = TestUtils.checkRDB(propositionIds.head, transversalState)
      assert(knowledgeRegisterHistoryRecords.size == 1)
      assert(knowledgeRegisterHistoryRecords.head.propositionId.equals(propositionIds.head))
      assert(knowledgeRegisterHistoryRecords.head.stateId == 1)

    }

  }

}


/*
val jsonStr: String =
  s"""{
    |  "transversalState": ${Json.toJson(transversalState).toString()},
    |  "knowledgeSentenceSet": {
    |    "premiseList": [
    |      {
    |        "sentence": "これはテストの前提1です。",
    |        "lang": "ja_JP",
    |        "extentInfoJson": "{}",
    |        "isNegativeSentence": false,
    |        "knowledgeForImages": []
    |      },
    |      {
    |        "sentence": "これはテストの前提2です。",
    |        "lang": "ja_JP",
    |        "extentInfoJson": "{}",
    |        "isNegativeSentence": false,
    |        "knowledgeForImages": []
    |      },
    |      {
    |        "sentence": "猫が２匹います。",
    |        "lang": "ja_JP",
    |        "extentInfoJson": "{}",
    |        "isNegativeSentence": false,
    |        "knowledgeForImages": [
    |          {
    |            "id": "",
    |            "imageReference": {
    |              "reference": {
    |                "url": "",
    |                "surface": "猫が",
    |                "surfaceIndex": 0,
    |                "isWholeSentence": false,
    |                "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg"
    |              },
    |              "x": 27,
    |              "y": 41,
    |              "width": 287,
    |              "height": 435
    |            }
    |          }
    |        ]
    |      }
    |    ],
    |    "premiseLogicRelation": [
    |      {
    |        "operator": "AND",
    |        "sourceIndex": 0,
    |        "destinationIndex": 1
    |      }
    |    ],
    |    "claimList": [
    |      {
    |        "sentence": "これはテストの主張1です。",
    |        "lang": "ja_JP",
    |        "extentInfoJson": "{}",
    |        "isNegativeSentence": false,
    |        "knowledgeForImages": []
    |      },
    |      {
    |        "sentence": "これはテストの主張2です。",
    |        "lang": "ja_JP",
    |        "extentInfoJson": "{}",
    |        "isNegativeSentence": false,
    |        "knowledgeForImages": []
    |      },
    |      {
    |        "sentence": "犬が1匹います。",
    |        "lang": "ja_JP",
    |        "extentInfoJson": "{}",
    |        "isNegativeSentence": false,
    |        "knowledgeForImages": [
    |          {
    |            "id": "",
    |            "imageReference": {
    |              "reference": {
    |                "url": "",
    |                "surface": "犬が",
    |                "surfaceIndex": 0,
    |                "isWholeSentence": false,
    |                "originalUrlOrReference": "http://images.cocodataset.org/train2017/000000428746.jpg"
    |              },
    |              "x": 435,
    |              "y": 227,
    |              "width": 91,
    |              "height": 69
    |            }
    |          }
    |        ]
    |      }
    |    ],
    |    "claimLogicRelation": [
    |      {
    |        "operator": "OR",
    |        "sourceIndex": 0,
    |        "destinationIndex": 1
    |      }
    |    ]
    |  }
    |}""".stripMargin
*/