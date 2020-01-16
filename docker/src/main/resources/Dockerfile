FROM ${docker.pull.registry}${jre.base.image}

MAINTAINER Luca Gasparini <lgasparini@inventage.com>

ENV CLASSPATH=./jars/*
ENV com.inventage.airmock.waf.AirmockHandler.config-path=/opt/airmock/mapping/airmock.mappings.json

WORKDIR /opt/airmock

ADD ./server-${project.version}-all-jars.tar ./jars/
ADD airmock.mappings.json ./mapping/

EXPOSE 28080
CMD ["java", "-Dfile.encoding=UTF-8", "-Dlogback.configurationFile=/opt/airmock/logback/logback.xml", "-Dvertx.disableDnsResolver=true", "com.inventage.airmock.AirmockLauncher"]
