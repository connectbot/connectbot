FROM ubuntu:24.04

RUN apt-get update && apt-get install -y \
    openssh-server \
    sudo \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir /run/sshd

# Copy SSH configuration and test keys
COPY sshd_config /etc/ssh/sshd_config
COPY test_keys/id_ed25519.pub /tmp/authorized_keys
COPY entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

EXPOSE 22

ENTRYPOINT ["/entrypoint.sh"]
CMD ["/usr/sbin/sshd", "-D", "-e"]
