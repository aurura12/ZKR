FROM paddlepaddle/paddleocr:2.8.1

RUN apt-get update && apt-get install -y --no-install-recommends \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir fastapi uvicorn python-multipart pdf2image python-docx PyPDF2

WORKDIR /app
COPY app.py /app/

EXPOSE 18952

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=120s \
    CMD curl -f http://localhost:18952/health || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "18952", "--timeout-keep-alive", "300"]
