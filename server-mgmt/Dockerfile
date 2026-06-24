FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    SERVER_MGMT_DB_PATH=/app/data/server_mgmt.db \
    PORT=17000 \
    PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    PIP_TRUSTED_HOST=pypi.tuna.tsinghua.edu.cn \
    ANSIBLE_GALAXY_SERVER=https://galaxy.apps.aliyun.com

# Debian apt 国内源 + 运行依赖
RUN set -eux; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's|deb.debian.org|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list.d/debian.sources; \
        sed -i 's|security.debian.org|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list.d/debian.sources; \
    elif [ -f /etc/apt/sources.list ]; then \
        sed -i 's|deb.debian.org|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list; \
        sed -i 's|security.debian.org|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list; \
    fi; \
    apt-get update; \
    apt-get install -y --no-install-recommends openssh-client sshpass; \
    rm -rf /var/lib/apt/lists/*

COPY docker/pip.conf /etc/pip.conf
COPY requirements-docker.txt requirements.yml ./
RUN pip install --no-cache-dir -r requirements-docker.txt \
    && ansible-galaxy collection install -r requirements.yml

COPY ansible.cfg playbook-ping.yml playbook-users.yml ./
COPY group_vars/ group_vars/
COPY api/ api/
COPY db/ db/
COPY scripts/ scripts/
COPY docker/entrypoint.sh /entrypoint.sh

RUN mkdir -p /app/data \
    && chmod +x /entrypoint.sh

EXPOSE 17000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:17000/api/health')" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
CMD ["api"]
