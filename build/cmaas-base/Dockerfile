# Dockerfile for running ConfD examples
#
# We use the standard ubuntu base image.
FROM adoptopenjdk/openjdk11:jdk-11.0.8_10
LABEL description=”ConfdContainer.”
LABEL maintainer=”jojohans@cisco.com”
# Install extra packages needed to run ConfD. We also need wget in order tobe
# able to install dump-init.
RUN apt-get update && apt-get install -y libssl-dev vim net-tools netcat-openbsd openssl
# Alternative package installation command which, in addition to libssl install
# usefule network, debug and trace tools.
#RUN apt-get update && apt-get install -y \
# iperf3 \
# libssl-dev \
# net-tools \
# netcat-openbsd \
# python-paramiko \
# strace \
# tcpdump \
# vim \
# wget
# Set up ConfD environment variables, this is what is normally done when
# sourcing the confdrc script.
ENV CONFD_DIR=/confd
ENV LD_LIBRARY_PATH=/confd/lib
ENV PATH=/confd/bin:$PATH
# Install ConfD in the container. This is not a regular target installation,
# instead it’s only the files required for a minimal target installation as
# described in section 28.3. Installing ConfD on a target system in theConfD
# User Guide.
COPY confd-target.tgz /tmp
RUN mkdir -p ${CONFD_DIR}
RUN tar xzf /tmp/confd-target.tgz -C /
# Add data model store for user defined models. We will mount the example
# loadpath directory here, when we run the container from within a ConfD
# example.
RUN mkdir -p /confd/apps
VOLUME /confd/apps
# Add a simple init service. Not strictly necessary since we can have
# directly start ConfD but never the less useful. If we don’t have an
# init-daemon ConfD will become pid 1 and since the kernel treats pid 1
# specially in terms of signal delivery and orphaned zombie processes
# there might be issues when we shut down the container.
#RUN wget -O /usr/local/sbin/dumb-init -q https://github.com/Yelp/dumb-init/releases/download/v1.2.0/dumb-init_1.2.0_amd64
#RUN chmod +x /usr/local/sbin/dumb-init
WORKDIR /confd/apps
COPY tmaas/fxs/* /confd/etc/confd/
# We expose all ConfD ports. This allows us to run all ConfD examples
# without rebuilding the container. In production, we should only expose
# the ports for the protocol we use, e.g. NETCONF (over ssh) and the
# IPC port. Of course, when we instantiate a container we only forward
# the ports needed to run the example.
EXPOSE 2022
EXPOSE 2023
EXPOSE 2024
EXPOSE 4565
EXPOSE 8008
EXPOSE 8088


# Cleanup
RUN apt-get autoremove -y && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
# Start init daemon and ConfD
#ENTRYPOINT ["/bin/start.sh"]
