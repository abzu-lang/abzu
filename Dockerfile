FROM oracle/graalvm-ce:21.1.0-java16

MAINTAINER Adam Kovari <kovariadam@gmail.com>

RUN yum install -y git
#RUN gu install native-image

ARG MAVEN_VERSION=3.6.3
ARG SHA=c35a1803a6e70a126e80b2b3ae33eed961f83ed74d18fcd16909b2d44d7dada3203f1ffe726c17ef8dcca2dcaa9fca676987befeadc9b9f759967a8cb77181c0
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

# 5- Create the directories, download maven, validate the download, install it, remove downloaded file and set links
RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && echo "Downlaoding maven" \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  \
  && echo "Checking download hash" \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  \
  && echo "Unziping maven" \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  \
  && echo "Cleaning and setting links" \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

RUN git clone https://github.com/yona-lang/yona.git /yona
RUN cd yona/; mvn -B dependency:resolve
RUN cd /yona/; mvn -B package -DskipTests
RUN gu install -L /yona/component/yona-component.jar

RUN alternatives --remove yona /opt/graalvm-ce-java16-21.1.0//bin/yona

WORKDIR /sources

ENTRYPOINT ["/opt/graalvm-ce-java16-21.1.0/bin/yona"]
