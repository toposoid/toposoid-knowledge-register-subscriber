FROM toposoid/toposoid-scala-lib:0.6-SNAPSHOT

WORKDIR /app
ARG TARGET_BRANCH
ARG JAVA_OPT_XMX
ENV DEPLOYMENT=local
ENV _JAVA_OPTIONS="-Xms512m -Xmx"${JAVA_OPT_XMX}

RUN apt-get update \
&& apt-get -y install git \
&& git clone https://github.com/toposoid/toposoid-knowledge-register-subscriber.git \
&& cd toposoid-knowledge-register-subscriber \
&& git pull \
&& git fetch origin ${TARGET_BRANCH} \
&& git checkout ${TARGET_BRANCH}

COPY ./docker-entrypoint.sh /app/
ENTRYPOINT ["/app/docker-entrypoint.sh"]
