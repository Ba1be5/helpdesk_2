FROM postgres:17

ENV POSTGRES_DB=helpdesk_db
ENV POSTGRES_USER=helpdesk_app


COPY sql/ /docker-entrypoint-initdb.d/