--
-- PostgreSQL database dump
--

-- Dumped from database version 16.3 (Debian 16.3-1.pgdg110+1)
-- Dumped by pg_dump version 16.6 (Ubuntu 16.6-1.pgdg22.04+1)

-- Started on 2024-12-16 19:04:21 EET

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 8 (class 2615 OID 39972)
-- Name: users; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA users;


ALTER SCHEMA users OWNER TO postgres;


CREATE FUNCTION public.tf_bu_update_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
--  RAISE NOTICE 'Calling cs_create_job(%)', NEW;
  IF TG_OP = 'UPDATE' THEN 
--		NEW.updated_at = now();
	NEW.updated_at = date_trunc('second', now());
  END IF;  
  return NEW;
END$$;


ALTER FUNCTION public.tf_bu_update_at() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 225 (class 1259 OID 39998)
-- Name: group; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users."group" (
    id integer NOT NULL,
    group_name character varying(255),
    reg_id integer
);


ALTER TABLE users."group" OWNER TO postgres;

--
-- TOC entry 3575 (class 0 OID 0)
-- Dependencies: 225
-- Name: TABLE "group"; Type: COMMENT; Schema: users; Owner: postgres
--

COMMENT ON TABLE users."group" IS 'Обєднання кілька правил в один набір';


--
-- TOC entry 224 (class 1259 OID 39997)
-- Name: group_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.group_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE users.group_id_seq OWNER TO postgres;

--
-- TOC entry 3576 (class 0 OID 0)
-- Dependencies: 224
-- Name: group_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.group_id_seq OWNED BY users."group".id;


--
-- TOC entry 226 (class 1259 OID 40004)
-- Name: group_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.group_role (
    group_id integer NOT NULL,
    role_id integer NOT NULL
);


ALTER TABLE users.group_role OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 40013)
-- Name: role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.role (
    id integer NOT NULL,
    application character varying(255) NOT NULL,
    role_name character varying(255) NOT NULL,
    role_desc character varying(255),
    role character varying(255) GENERATED ALWAYS AS (upper(((('role_'::text || (application)::text) || '_'::text) || (role_name)::text))) STORED
);


ALTER TABLE users.role OWNER TO postgres;

--
-- TOC entry 227 (class 1259 OID 40012)
-- Name: role_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.role_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE users.role_id_seq OWNER TO postgres;

--
-- TOC entry 3577 (class 0 OID 0)
-- Dependencies: 227
-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.role_id_seq OWNED BY users.role.id;


--
-- TOC entry 229 (class 1259 OID 40040)
-- Name: user_group; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.user_group (
    group_id integer NOT NULL,
    user_id integer NOT NULL
);


ALTER TABLE users.user_group OWNER TO postgres;

--
-- TOC entry 231 (class 1259 OID 40044)
-- Name: user_login; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.user_login (
    id integer NOT NULL,
    login character varying(255) NOT NULL,
    pass character varying(255),
    authority character varying(255),
    login_desc character varying(255),
    contact_id integer
);


ALTER TABLE users.user_login OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 40043)
-- Name: user_login_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.user_login_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE users.user_login_id_seq OWNER TO postgres;

--
-- TOC entry 3578 (class 0 OID 0)
-- Dependencies: 230
-- Name: user_login_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.user_login_id_seq OWNED BY users.user_login.id;


--
-- TOC entry 233 (class 1259 OID 40055)
-- Name: user_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.user_role (
    user_id integer NOT NULL,
    role_id integer NOT NULL,
    id integer NOT NULL
);


ALTER TABLE users.user_role OWNER TO postgres;

--
-- TOC entry 232 (class 1259 OID 40054)
-- Name: user_role_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.user_role_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE users.user_role_id_seq OWNER TO postgres;

--
-- TOC entry 3579 (class 0 OID 0)
-- Dependencies: 232
-- Name: user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.user_role_id_seq OWNED BY users.user_role.id;


--
-- TOC entry 3298 (class 2604 OID 40001)
-- Name: group id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users."group" ALTER COLUMN id SET DEFAULT nextval('users.group_id_seq'::regclass);


--
-- TOC entry 3299 (class 2604 OID 40016)
-- Name: role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role ALTER COLUMN id SET DEFAULT nextval('users.role_id_seq'::regclass);


--
-- TOC entry 3301 (class 2604 OID 40047)
-- Name: user_login id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login ALTER COLUMN id SET DEFAULT nextval('users.user_login_id_seq'::regclass);


--
-- TOC entry 3302 (class 2604 OID 40058)
-- Name: user_role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role ALTER COLUMN id SET DEFAULT nextval('users.user_role_id_seq'::regclass);


--
-- TOC entry 3527 (class 0 OID 39998)
-- Dependencies: 225
-- Data for Name: group; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users."group" (id, group_name, reg_id) FROM stdin;
\.


--
-- TOC entry 3528 (class 0 OID 40004)
-- Dependencies: 226
-- Data for Name: group_role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.group_role (group_id, role_id) FROM stdin;
\.


--
-- TOC entry 3530 (class 0 OID 40013)
-- Dependencies: 228
-- Data for Name: role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.role (id, application, role_name, role_desc) FROM stdin;
1	notification	admin	Повний доступ
\.


--
-- TOC entry 3531 (class 0 OID 40040)
-- Dependencies: 229
-- Data for Name: user_group; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_group (group_id, user_id) FROM stdin;
\.


--
-- TOC entry 3533 (class 0 OID 40044)
-- Dependencies: 231
-- Data for Name: user_login; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_login (id, login, pass, authority, login_desc, contact_id) FROM stdin;
\.


--
-- TOC entry 3535 (class 0 OID 40055)
-- Dependencies: 233
-- Data for Name: user_role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_role (user_id, role_id, id) FROM stdin;
\.


--
-- TOC entry 3580 (class 0 OID 0)
-- Dependencies: 222
-- Name: phone_id_seq; Type: SEQUENCE SET; Schema: contact; Owner: postgres
--

SELECT pg_catalog.setval('contact.phone_id_seq', 3, true);


--
-- TOC entry 3581 (class 0 OID 0)
-- Dependencies: 218
-- Name: phone_type_id_seq; Type: SEQUENCE SET; Schema: contact; Owner: postgres
--

SELECT pg_catalog.setval('contact.phone_type_id_seq', 1, false);


--
-- TOC entry 3582 (class 0 OID 0)
-- Dependencies: 254
-- Name: structure_list_id_seq; Type: SEQUENCE SET; Schema: contact; Owner: postgres
--

SELECT pg_catalog.setval('contact.structure_list_id_seq', 56, true);


--
-- TOC entry 3583 (class 0 OID 0)
-- Dependencies: 220
-- Name: subscriber_id_seq; Type: SEQUENCE SET; Schema: contact; Owner: postgres
--

SELECT pg_catalog.setval('contact.subscriber_id_seq', 2, true);


--
-- TOC entry 3584 (class 0 OID 0)
-- Dependencies: 238
-- Name: global_option_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.global_option_id_seq', 1, false);


--
-- TOC entry 3585 (class 0 OID 0)
-- Dependencies: 249
-- Name: notification_list_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.notification_list_id_seq', 56, true);


--
-- TOC entry 3586 (class 0 OID 0)
-- Dependencies: 251
-- Name: notification_list_subscriber_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.notification_list_subscriber_id_seq', 1, false);


--
-- TOC entry 3587 (class 0 OID 0)
-- Dependencies: 247
-- Name: notification_result_archiv_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.notification_result_archiv_id_seq', 1, false);


--
-- TOC entry 3588 (class 0 OID 0)
-- Dependencies: 243
-- Name: notification_result_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.notification_result_id_seq', 1, false);


--
-- TOC entry 3589 (class 0 OID 0)
-- Dependencies: 245
-- Name: notification_result_list_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.notification_result_list_id_seq', 1, false);


--
-- TOC entry 3590 (class 0 OID 0)
-- Dependencies: 236
-- Name: notification_task_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.notification_task_id_seq', 1, false);


--
-- TOC entry 3591 (class 0 OID 0)
-- Dependencies: 234
-- Name: server_connect_id_seq; Type: SEQUENCE SET; Schema: notification; Owner: postgres
--

SELECT pg_catalog.setval('notification.server_connect_id_seq', 1, false);


--
-- TOC entry 3592 (class 0 OID 0)
-- Dependencies: 224
-- Name: group_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.group_id_seq', 1, false);


--
-- TOC entry 3593 (class 0 OID 0)
-- Dependencies: 227
-- Name: role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.role_id_seq', 1, true);


--
-- TOC entry 3594 (class 0 OID 0)
-- Dependencies: 230
-- Name: user_login_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.user_login_id_seq', 1, false);


--
-- TOC entry 3595 (class 0 OID 0)
-- Dependencies: 232
-- Name: user_role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.user_role_id_seq', 1, false);


--
-- TOC entry 3331 (class 2606 OID 40003)
-- Name: group group_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users."group"
    ADD CONSTRAINT group_pkey PRIMARY KEY (id);


--
-- TOC entry 3333 (class 2606 OID 40021)
-- Name: role right_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role
    ADD CONSTRAINT right_pkey PRIMARY KEY (id);


--
-- TOC entry 3335 (class 2606 OID 40023)
-- Name: role role_role_name_key; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role
    ADD CONSTRAINT role_role_name_key UNIQUE (role_name, application);


--
-- TOC entry 3337 (class 2606 OID 40053)
-- Name: user_login user_login_key; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT user_login_key UNIQUE (login);


--
-- TOC entry 3341 (class 2606 OID 40060)
-- Name: user_role user_right_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT user_right_pkey PRIMARY KEY (id);


--
-- TOC entry 3339 (class 2606 OID 40051)
-- Name: user_login users_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);



--
-- TOC entry 3367 (class 2606 OID 40061)
-- Name: user_role users_rights_right_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_right_id_fkey FOREIGN KEY (role_id) REFERENCES users.role(id);


--
-- TOC entry 3368 (class 2606 OID 40066)
-- Name: user_role users_rights_user_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_user_id_fkey FOREIGN KEY (user_id) REFERENCES users.user_login(id);


-- Completed on 2024-12-16 19:04:21 EET

--
-- PostgreSQL database dump complete
--

