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
import com.ideal.linked.toposoid.common.mq.KnowledgeRegistration
import com.ideal.linked.toposoid.common.{IMAGE, SENTENCE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import io.jvm.uuid.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import play.api.libs.json.Json

class SubscriberEnglishTest extends AnyFlatSpec with BeforeAndAfter with BeforeAndAfterAll {

  /**
   * TODO:CAUTION!!!!
   * When testing locally, be sure to run sbt "runMain com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber" from the terminal
   */

  val transversalState: TransversalState = TransversalState(userId = "test-user", username = "guest", roleId = 0, csrfToken = "")
  //Unless you change the Json payload somewhere, it will not be subject to MQ.

  before {
    TestUtils.deleteNeo4JAllData(transversalState)
  }

  override def beforeAll(): Unit = {
    TestUtils.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    TestUtils.deleteNeo4JAllData(transversalState)
  }


  "the request json" should "be properly registered in the English knowledge database and searchable." in {

    val jsonStr: String =
      s"""{
        |  "documentId": "${UUID.random.toString()}",
        |  "sequentialNumber": 0,
        |  "transversalState": ${Json.toJson(transversalState).toString()},
        |  "knowledgeSentenceSet": {
        |    "premiseList": [
        |      {
        |        "sentence": "This is premise-1.",
        |        "lang": "en_US",
        |        "extentInfoJson": "{}",
        |        "isNegativeSentence": false,
        |        "knowledgeForImages": []
        |      },
        |      {
        |        "sentence": "This is premise-2.",
        |        "lang": "en_US",
        |        "extentInfoJson": "{}",
        |        "isNegativeSentence": false,
        |        "knowledgeForImages": []
        |      },
        |      {
        |        "sentence": "There are two cats.",
        |        "lang": "en_US",
        |        "extentInfoJson": "{}",
        |        "isNegativeSentence": false,
        |        "knowledgeForImages": [
        |          {
        |            "id": "",
        |            "imageReference": {
        |              "reference": {
        |                "url": "",
        |                "surface": "cats",
        |                "surfaceIndex": 3,
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
        |        "sentence": "This is claim-1.",
        |        "lang": "en_US",
        |        "extentInfoJson": "{}",
        |        "isNegativeSentence": false,
        |        "knowledgeForImages": []
        |      },
        |      {
        |        "sentence": "This is claim-2.",
        |        "lang": "en_US",
        |        "extentInfoJson": "{}",
        |        "isNegativeSentence": false,
        |        "knowledgeForImages": []
        |      },
        |      {
        |        "sentence": "There is a dog",
        |        "lang": "en_US",
        |        "extentInfoJson": "{}",
        |        "isNegativeSentence": false,
        |        "knowledgeForImages": [
        |          {
        |            "id": "",
        |            "imageReference": {
        |              "reference": {
        |                "url": "",
        |                "surface": "dog",
        |                "surfaceIndex": 3,
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

    TestUtils.publishMessage(jsonStr.replace("\n",""))
    Thread.sleep(60000)
    val query = "MATCH x=(:ClaimNode{surface:'claim-1'})-[:LocalEdge]-(:ClaimNode)-[:LocalEdge{logicType:'OR'}]-(:ClaimNode)-[:LocalEdge]-(:ClaimNode{surface:'claim-2'}) return x"
    val queryResult: Neo4jRecords = TestUtils.executeQueryAndReturn(query, transversalState)
    assert(queryResult.records.size == 1)
    val query2 = "MATCH x=(:PremiseNode{surface:'premise-1'})-[:LocalEdge]-(:PremiseNode)-[:LocalEdge{logicType:'AND'}]-(:PremiseNode)-[:LocalEdge]-(:PremiseNode{surface:'premise-2'}) return x"
    val queryResult2: Neo4jRecords = TestUtils.executeQueryAndReturn(query2, transversalState)
    assert(queryResult2.records.size == 1)
    val query3 = "MATCH x=(:PremiseNode{surface:'premise-1'})-[:LocalEdge]-(:PremiseNode)-[:LocalEdge{logicType:'IMP'}]-(:ClaimNode)-[:LocalEdge]-(:ClaimNode{surface:'claim-1'}) return x"
    val queryResult3: Neo4jRecords = TestUtils.executeQueryAndReturn(query3, transversalState)
    assert(queryResult3.records.size == 1)


    val queryResult4: Neo4jRecords = TestUtils.executeQueryAndReturn("MATCH (s:ImageNode{source:'http://images.cocodataset.org/val2017/000000039769.jpg'})-[:ImageEdge]->(t:PremiseNode{surface:'cats'}) RETURN s, t", transversalState)
    assert(queryResult4.records.size == 1)

    val urlCat = queryResult4.records.head.head.value.featureNode.get.url
    val queryResult5: Neo4jRecords = TestUtils.executeQueryAndReturn("MATCH (s:ImageNode{source:'http://images.cocodataset.org/train2017/000000428746.jpg'})-[:ImageEdge]->(t:ClaimNode{surface:'dog'}) RETURN s, t", transversalState)
    assert(queryResult5.records.size == 1)
    val urlDog = queryResult5.records.head.head.value.featureNode.get.url

    val knowledgeRegistration: KnowledgeRegistration = Json.parse(jsonStr.replace("\n", "")).as[KnowledgeRegistration]
    val knowledgeSentenceSet = knowledgeRegistration.knowledgeSentenceSet

    for (knowledge <- knowledgeSentenceSet.premiseList ::: knowledgeSentenceSet.claimList) {
      val vector = FeatureVectorizer.getSentenceVector(Knowledge(knowledge.sentence, "en_US", "{}"), transversalState)
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = 1)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
      assert(result.ids.size > 0)
      result.ids.map(x => TestUtils.deleteFeatureVector(x, SENTENCE, transversalState))

      knowledge.knowledgeForImages.foreach(x => {
        val url: String = x.imageReference.reference.surface match {
          case "cats" => urlCat
          case "dog" => urlDog
          case _ => "BAD URL"
        }
        val vector = TestUtils.getImageVector(url, transversalState)
        val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = 1)).toString()
        val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
        val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
        assert(result.ids.size > 0 && result.similarities.head > 0.999)
        result.ids.map(x => TestUtils.deleteFeatureVector(x, IMAGE, transversalState))
      })
    }
  }

}
