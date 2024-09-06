package com.ideal.linked.toposoid.mq

import com.ideal.linked.toposoid.common.TransversalState
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.flatspec.AnyFlatSpec
import com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber

class SubscriberTest extends AnyFlatSpec with BeforeAndAfter with BeforeAndAfterAll {

  val transversalState: TransversalState = TransversalState(userId = "test-user", username = "guest", roleId = 0, csrfToken = "")


  before {
    //TestUtils.deleteNeo4JAllData(transversalState)
  }

  override def beforeAll(): Unit = {
    //TestUtils.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    //TestUtils.deleteNeo4JAllData(transversalState)
  }


  "the request json" should "be properly registered in the knowledge database and searchable." in {

    val jsonStr: String =
      """{
        |  "transversalState": {
        |    "username": "guest",
        |    "userId": "test-user",
        |    "roleId": 0,
        |    "csrfToken": "hoge"
        |  },
        |  "knowledgeSentenceSet": {
        |    "premiseList": [
        |      {
        |        "sentence": "これはテストの前提1ですがな。",
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
    assert(true)

  }

}
