-- Empty schema derived from the original DDL; no historical users, routes or data.
CREATE SCHEMA users;

CREATE TABLE users.filter (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    args jsonb
);

CREATE SEQUENCE users.filter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE users.filter_id_seq OWNED BY users.filter.id;

CREATE TABLE users."group" (
    id integer NOT NULL,
    group_name character varying(255),
    reg_id integer
);

COMMENT ON TABLE users."group" IS 'Обєднання кілька правил в один набір';

CREATE SEQUENCE users.group_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE users.group_id_seq OWNED BY users."group".id;

CREATE TABLE users.group_role (
    group_id integer NOT NULL,
    role_id integer NOT NULL
);

CREATE TABLE users.predicate (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    args jsonb
);

CREATE SEQUENCE users.predicate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE users.predicate_id_seq OWNED BY users.predicate.id;

CREATE TABLE users.role (
    id integer NOT NULL,
    application character varying(255) NOT NULL,
    role_name character varying(255) NOT NULL,
    role_desc character varying(255),
    role character varying(255) GENERATED ALWAYS AS (upper(((('role_'::text || (application)::text) || '_'::text) || (role_name)::text))) STORED
);

CREATE SEQUENCE users.role_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE users.role_id_seq OWNED BY users.role.id;

CREATE TABLE users.route (
    id integer NOT NULL,
    route character varying NOT NULL,
    route_desc character varying,
    service_id integer NOT NULL,
    is_internal boolean DEFAULT false NOT NULL,
    "order" integer
);

CREATE TABLE users.route_filter (
    id integer NOT NULL,
    route_id integer NOT NULL,
    filter_id integer
);

CREATE SEQUENCE users.route_filter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE users.route_filter_id_seq OWNED BY users.route_filter.id;

CREATE SEQUENCE users.route_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE users.route_id_seq OWNED BY users.route.id;

CREATE TABLE users.route_predicate (
    id integer NOT NULL,
    route_id integer NOT NULL,
    predicate_id integer NOT NULL
);

CREATE SEQUENCE users.route_predicate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE users.route_predicate_id_seq OWNED BY users.route_predicate.id;

CREATE TABLE users.route_role (
    id integer NOT NULL,
    route_id integer NOT NULL,
    role_id integer NOT NULL
);

CREATE SEQUENCE users.route_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE users.route_role_id_seq OWNED BY users.route_role.id;

CREATE TABLE users.service (
    id integer NOT NULL,
    service_name character varying(255) NOT NULL,
    service_desc character varying(255)
);

CREATE SEQUENCE users.service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;

ALTER SEQUENCE users.service_id_seq OWNED BY users.service.id;

CREATE TABLE users.user_group (
    group_id integer NOT NULL,
    user_id integer NOT NULL
);

CREATE TABLE users.user_login (
    id integer NOT NULL,
    login character varying(255) NOT NULL,
    pass character varying(255),
    authority character varying(255),
    login_desc character varying(255),
    contact_id integer
);

CREATE SEQUENCE users.user_login_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE users.user_login_id_seq OWNED BY users.user_login.id;

CREATE TABLE users.user_role (
    user_id integer NOT NULL,
    role_id integer NOT NULL,
    id integer NOT NULL
);

CREATE SEQUENCE users.user_role_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE users.user_role_id_seq OWNED BY users.user_role.id;

ALTER TABLE ONLY users.filter ALTER COLUMN id SET DEFAULT nextval('users.filter_id_seq'::regclass);

ALTER TABLE ONLY users."group" ALTER COLUMN id SET DEFAULT nextval('users.group_id_seq'::regclass);

ALTER TABLE ONLY users.predicate ALTER COLUMN id SET DEFAULT nextval('users.predicate_id_seq'::regclass);

ALTER TABLE ONLY users.role ALTER COLUMN id SET DEFAULT nextval('users.role_id_seq'::regclass);

ALTER TABLE ONLY users.route ALTER COLUMN id SET DEFAULT nextval('users.route_id_seq'::regclass);

ALTER TABLE ONLY users.route_filter ALTER COLUMN id SET DEFAULT nextval('users.route_filter_id_seq'::regclass);

ALTER TABLE ONLY users.route_predicate ALTER COLUMN id SET DEFAULT nextval('users.route_predicate_id_seq'::regclass);

ALTER TABLE ONLY users.route_role ALTER COLUMN id SET DEFAULT nextval('users.route_role_id_seq'::regclass);

ALTER TABLE ONLY users.service ALTER COLUMN id SET DEFAULT nextval('users.service_id_seq'::regclass);

ALTER TABLE ONLY users.user_login ALTER COLUMN id SET DEFAULT nextval('users.user_login_id_seq'::regclass);

ALTER TABLE ONLY users.user_role ALTER COLUMN id SET DEFAULT nextval('users.user_role_id_seq'::regclass);

ALTER TABLE ONLY users.filter
    ADD CONSTRAINT filter_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users."group"
    ADD CONSTRAINT group_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.predicate
    ADD CONSTRAINT predicate_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.role
    ADD CONSTRAINT right_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.role
    ADD CONSTRAINT role_role_name_key UNIQUE (role_name, application);

ALTER TABLE ONLY users.route_filter
    ADD CONSTRAINT route_filter_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.route
    ADD CONSTRAINT route_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.route_predicate
    ADD CONSTRAINT route_predicate_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.service
    ADD CONSTRAINT service_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT user_login_key UNIQUE (login);

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT user_right_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users.route_filter
    ADD CONSTRAINT route_filter_filter_id_fkey FOREIGN KEY (filter_id) REFERENCES users.filter(id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY users.route_filter
    ADD CONSTRAINT route_filter_route_id_fkey FOREIGN KEY (route_id) REFERENCES users.route(id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY users.route_predicate
    ADD CONSTRAINT route_predicate_predicate_id_fkey FOREIGN KEY (predicate_id) REFERENCES users.predicate(id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY users.route_predicate
    ADD CONSTRAINT route_predicate_route_id_fkey FOREIGN KEY (route_id) REFERENCES users.route(id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES users.role(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_route_id_fkey FOREIGN KEY (route_id) REFERENCES users.route(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;

ALTER TABLE ONLY users.route
    ADD CONSTRAINT route_service_id_fkey FOREIGN KEY (service_id) REFERENCES users.service(id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_right_id_fkey FOREIGN KEY (role_id) REFERENCES users.role(id);

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_user_id_fkey FOREIGN KEY (user_id) REFERENCES users.user_login(id);
