FROM openjdk:8-alpine

RUN apk update && apk upgrade
RUN apk add \
  net-tools \
  sudo \
  iptables \
  logrotate \
  gnuplot \
  vim \
  openssh \
  maven

# configure ssh configs
RUN mkdir -p /var/run/sshd && \
    sed -i "s/UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config && \
    sed -i 's/PermitRootLogin without-password/PermitRootLogin yes/' /etc/ssh/sshd_config && \
    touch /root/.Xauthority true

RUN chmod 755 /var/run/sshd
ARG PASSWORD=root
RUN echo 'root:'${PASSWORD} | chpasswd