#!/bin/bash

cd /app/toposoid-knowledge-register-web
sbt "runMain com.ideal.linked.toposoid.mq.KnowledgeRegisterSubscriber"

