-- Corrige dos defectos que V1 heredo tal cual del DDL que generaba Hibernate.
-- Ninguno de los dos lo detecta `ddl-auto: validate`: el validador solo compara
-- tablas y columnas, no chequea nullability, largos ni primary keys.

-- ---------------------------------------------------------------------------
-- 1. personas.telefono_numero: varchar(255) NULL -> varchar(15) NOT NULL
--
-- El embeddable Telefono declara @Column(length = 15, nullable = false) para sus
-- dos campos, pero el @AttributeOverride de Persona (que solo queria renombrar la
-- columna a telefono_numero) REEMPLAZA la definicion completa, no la extiende: se
-- perdieron el largo y el not-null. Por eso codigo_area si salio varchar(5) NOT
-- NULL y su hermana no. La entidad se corrigio en el mismo cambio.
-- ---------------------------------------------------------------------------

ALTER TABLE personas
    ALTER COLUMN telefono_numero TYPE character varying(15),
    ALTER COLUMN telefono_numero SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. administrador_vehiculo: PK compuesta
--
-- Hibernate genera las tablas puente de @ManyToMany sin primary key, asi que la
-- tabla aceptaba el mismo par (administrador, vehiculo) repetido. La PK compuesta
-- le da semantica de conjunto, que es lo que la relacion significa, y de paso
-- indexa el lado por el que se navega.
-- ---------------------------------------------------------------------------

ALTER TABLE administrador_vehiculo
    ADD CONSTRAINT administrador_vehiculo_pkey
    PRIMARY KEY (id_administrador, id_vehiculo);
