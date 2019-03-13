FROM openjdk:8-alpine

ENV LEIN_ROOT true

RUN apk update && apk upgrade
RUN apk add \
  net-tools \
  sudo \
  iptables \
  logrotate \
  gnuplot \
  vim \
  openssh \
  bash \
  maven

# configure ssh configs
RUN mkdir -p /var/run/sshd && \
    sed -i "s/UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config && \
    sed -i 's/PermitRootLogin without-password/PermitRootLogin yes/' /etc/ssh/sshd_config && \
    touch /root/.Xauthority true

RUN chmod 755 /var/run/sshd
ARG PASSWORD=root
RUN echo 'root:'${PASSWORD} | chpasswd

RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    mv lein /usr/bin && \
    chmod +x /usr/bin/lein && \

# You need to locate jepsen in this directory (up.sh does that automatically)
ADD jepsen /jepsen
RUN cd /jepsen && lein install

ADD ./bashrc /root/.bashrc
ADD ./init.sh /init.sh
RUN chmod +x /init.sh

CMD /init.sh
