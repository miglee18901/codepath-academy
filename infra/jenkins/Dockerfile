FROM docker:29-cli AS dockercli
FROM jenkins/jenkins:lts-jdk21

USER root
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=dockercli /usr/local/libexec/docker/cli-plugins/ /usr/local/libexec/docker/cli-plugins/
USER jenkins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY jenkins.yaml jobs.groovy /usr/share/jenkins/config/
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/config/jenkins.yaml
