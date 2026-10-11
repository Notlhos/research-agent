FROM node:24-slim AS node

FROM research-agents:local

USER root
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
    && npm install -g promptfoo@0.123.1

USER app
ENV PROMPTFOO_PYTHON=python \
    PROMPTFOO_DISABLE_TELEMETRY=1 \
    PROMPTFOO_DISABLE_UPDATE=1
WORKDIR /repo/evals/promptfoo

ENTRYPOINT ["promptfoo"]
CMD ["eval", "-c", "promptfooconfig.yaml", "--no-cache"]
