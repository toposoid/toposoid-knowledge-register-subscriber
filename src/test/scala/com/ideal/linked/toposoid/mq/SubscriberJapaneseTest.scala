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
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.flatspec.AnyFlatSpec
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import io.jvm.uuid.UUID
import play.api.libs.json.Json

class SubscriberJapaneseTest extends AnyFlatSpec with BeforeAndAfter with BeforeAndAfterAll {

  /**
   * TODO:CAUTION!!!!
   * When testing locally, be sure to run sbt "runMain com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber" from the terminal
   */

  val transversalState: TransversalState = TransversalState(userId = "test-user", username = "guest", roleId = 0, csrfToken = UUID.random.toString)
  //Unless you change the Json payload somewhere, it will not be subject to MQ.

  before {

  }

  override def beforeAll(): Unit = {
    //TestUtils.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    //TestUtils.deleteNeo4JAllData(transversalState)
  }


  "the request json" should "be properly registered in the Japanese knowledge database and searchable." in {

    val jsonStr: String =
      s"""{
        |"transversalState": ${Json.toJson(transversalState).toString()},
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

    TestUtils.publishMessage(jsonStr.replace("\n",""))
    Thread.sleep(60000)
    val query = "MATCH x=(:ClaimNode{surface:'主張２です。'})<-[:LocalEdge{logicType:'OR'}]-(:ClaimNode{surface:'主張１です。'})<-[:LocalEdge{logicType:'IMP'}]-(:PremiseNode{surface:'前提１です。'})-[:LocalEdge{logicType:'AND'}]->(:PremiseNode{surface:'前提２です。'}) return x"
    val queryResult: Neo4jRecords = TestUtils.executeQueryAndReturn(query, transversalState)
    assert(queryResult.records.size == 1)
    val result2: Neo4jRecords = TestUtils.executeQueryAndReturn("MATCH (s:ImageNode{source:'http://images.cocodataset.org/val2017/000000039769.jpg'})-[:ImageEdge]->(t:PremiseNode{surface:'猫が'}) RETURN s, t", transversalState)
    assert(result2.records.size == 1)
    val urlCat = result2.records.head.head.value.featureNode.get.url
    val result3: Neo4jRecords = TestUtils.executeQueryAndReturn("MATCH (s:ImageNode{source:'http://images.cocodataset.org/train2017/000000428746.jpg'})-[:ImageEdge]->(t:ClaimNode{surface:'犬が'}) RETURN s, t", transversalState)
    assert(result3.records.size == 1)
    val urlDog = result3.records.head.head.value.featureNode.get.url

    val knowledgeRegistration: KnowledgeRegistration = Json.parse(jsonStr.replace("\n","")).as[KnowledgeRegistration]
    val knowledgeSentenceSet = knowledgeRegistration.knowledgeSentenceSet

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
    }

  }

}
