FROM python:3.13-alpine
RUN apk add --no-cache openssl && addgroup -g 10001 app && adduser -D -H -u 10001 -G app app
WORKDIR /app
COPY docker/uat-auth/server.py /app/server.py
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=3s --timeout=3s --retries=20 CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["python","/app/server.py"]
