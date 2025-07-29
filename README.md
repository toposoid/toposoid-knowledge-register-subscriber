# toposoid-knowledge-register-subscriber
This is a subscriber that works as a microservice within the Toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice provides the functionality for manual registration.


[![Test And Build](https://github.com/toposoid/toposoid-knowledge-register-subscriber/actions/workflows/action.yml/badge.svg)](https://github.com/toposoid/toposoid-knowledge-register-subscriber/actions/workflows/action.yml)

## Requirements
Scala version 2.13.x,   
Sbt version 1.4.9.

## Recommended Environment For Standalone
* Required: at least 8GB of RAM.
* Required: at least 1.33G of HDD(Docker Image Size)

## Setup
```
sbt　publishLocal
sbt "runMain com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber"
```

## Usage
```bash
echo 'MessageBody=
{
  "knowledgeSentenceSet": {
    "premiseList": [],
    "premiseLogicRelation": [],
    "claimList": [
      {
        "sentence": "This is a Test.",
        "lang": "en_US",
        "extentInfoJson": "{}",
        "isNegativeSentence": false,
        "knowledgeForImages": [],
        "knowledgeForTables": [],
        "knowledgeForDocument": {
          "id": "",
          "filename": "",
          "url": "",
          "titleOfTopPage": ""
        },
        "documentPageReference": {
          "pageNo": -1,
          "references": [],
          "tableOfContents": [],
          "headlines": []
        }
      }
    ],
    "claimLogicRelation": []
  },
  "transversalState": {
    "userId": "test-user",
    "username": "guest",
    "roleId": 0,
    "csrfToken": ""
  }
}' |  sed 's/ /%20/g' > /tmp/test.json && curl -X POST -d@/tmp/test.json "http://localhost:9324?Action=SendMessage&QueueUrl=http://localhost:9324/toposoid-knowledge-register-queue.fifo&MessageGroupId=x"
```

## Note
* When executing with curl, it does not work in Japanese.

## License
This program is offered under a commercial and under the AGPL license.
For commercial licensing, contact us at https://toposoid.com/contact.  For AGPL licensing, see below.

AGPL licensing:
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!