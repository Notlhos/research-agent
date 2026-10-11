FROM python:3.12-slim

ARG EMBEDDING_MODEL=BAAI/bge-small-en-v1.5

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    FASTEMBED_CACHE_PATH=/opt/fastembed \
    RA_EMBEDDING_MODEL=${EMBEDDING_MODEL}

WORKDIR /app

COPY pyproject.toml ./
COPY src ./src
RUN pip install .

RUN python -c "import os; from fastembed import TextEmbedding; TextEmbedding(os.environ['RA_EMBEDDING_MODEL'])" \
    && useradd --create-home --uid 10001 app \
    && chown -R app:app /opt/fastembed

USER app
EXPOSE 9100

ENTRYPOINT ["research-agents"]
