-- Table: repository

-- DROP TABLE repository;

CREATE TABLE repository
(
  id character varying NOT NULL,
  metadata xml,
  CONSTRAINT repository_pkey PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE repository
  OWNER TO postgres;







-- Table: entity

-- DROP TABLE entity;

CREATE TABLE entity
(
  "repositoryId" character varying NOT NULL,
  name character varying NOT NULL,
  data jsonb,
  id character varying NOT NULL,
  "$ref" jsonb,
  CONSTRAINT entity_pkey PRIMARY KEY ("repositoryId", name, id),
  CONSTRAINT "entity_repositoryId_fkey" FOREIGN KEY ("repositoryId")
      REFERENCES repository (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
)
WITH (
  OIDS=FALSE
);
ALTER TABLE entity
  OWNER TO postgres;
