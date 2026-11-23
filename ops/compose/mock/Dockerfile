# API mock server (Prism) for the "mock" compose profile.
# The stoplight/prism image is published for linux/amd64 only, so on Apple Silicon it does not
# run natively (#45). The same CLI from npm on the multi-arch Node image runs on amd64 and arm64.
FROM node:26.13.1-alpine3.24@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5

RUN npm install --global --no-audit --no-fund @stoplight/prism-cli@5.16.0 \
    && npm cache clean --force

USER node
EXPOSE 4010
ENTRYPOINT ["prism"]
