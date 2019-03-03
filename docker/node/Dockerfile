FROM openjdk:8:alpine

# install initial deps
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && apt-get install -y apt-utils
RUN apt-get update && apt-get -y upgrade && \
  apt-get install -y --no-install-recommends \
  libjna-java \
  net-tools \
  iproute \
  man-db \
  rsyslog \
  ntpdate \
  sudo \
  ssh \
  faketime \
  iptables \
  iputils-ping \
  logrotate \
  gnuplot \
  vim \
  maven \
  netcat \
  software-properties-common && \
  apt-get clean && \
  rm -rf /var/lib/apt/lists/*

# configure ssh configs
RUN mkdir -p /var/run/sshd && \
    sed -i "s/UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config && \
    sed -i 's/PermitRootLogin without-password/PermitRootLogin yes/' /etc/ssh/sshd_config && \
    touch /root/.Xauthority true

RUN chmod 755 /var/run/sshd
ARG PASSWORD=root
RUN echo 'root:'${PASSWORD} | chpasswd

EXPOSE 22
