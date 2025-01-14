--
-- PostgreSQL database dump
--

-- Dumped from database version 16.6 (Ubuntu 16.6-1.pgdg22.04+1)
-- Dumped by pg_dump version 16.6 (Ubuntu 16.6-1.pgdg22.04+1)

-- Started on 2025-01-14 06:25:29 EET

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
-- TOC entry 6 (class 2615 OID 16469)
-- Name: users; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA users;


ALTER SCHEMA users OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 216 (class 1259 OID 16497)
-- Name: group; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users."group" (
    id integer NOT NULL,
    group_name character varying(255),
    reg_id integer
);


ALTER TABLE users."group" OWNER TO postgres;

--
-- TOC entry 3455 (class 0 OID 0)
-- Dependencies: 216
-- Name: TABLE "group"; Type: COMMENT; Schema: users; Owner: postgres
--

COMMENT ON TABLE users."group" IS 'Обєднання кілька правил в один набір';


--
-- TOC entry 217 (class 1259 OID 16500)
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
-- TOC entry 3456 (class 0 OID 0)
-- Dependencies: 217
-- Name: group_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.group_id_seq OWNED BY users."group".id;


--
-- TOC entry 218 (class 1259 OID 16501)
-- Name: group_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.group_role (
    group_id integer NOT NULL,
    role_id integer NOT NULL
);


ALTER TABLE users.group_role OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 16504)
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
-- TOC entry 220 (class 1259 OID 16510)
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
-- TOC entry 3457 (class 0 OID 0)
-- Dependencies: 220
-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.role_id_seq OWNED BY users.role.id;


--
-- TOC entry 229 (class 1259 OID 16560)
-- Name: route; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.route (
    id integer NOT NULL,
    route character varying NOT NULL,
    route_desc character varying,
    service_id integer NOT NULL
);


ALTER TABLE users.route OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 16559)
-- Name: route_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.route_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER SEQUENCE users.route_id_seq OWNER TO postgres;

--
-- TOC entry 3458 (class 0 OID 0)
-- Dependencies: 228
-- Name: route_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.route_id_seq OWNED BY users.route.id;


--
-- TOC entry 231 (class 1259 OID 16574)
-- Name: route_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.route_role (
    id integer NOT NULL,
    route_id integer NOT NULL,
    role_id integer NOT NULL
);


ALTER TABLE users.route_role OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 16573)
-- Name: route_role_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.route_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER SEQUENCE users.route_role_id_seq OWNER TO postgres;

--
-- TOC entry 3459 (class 0 OID 0)
-- Dependencies: 230
-- Name: route_role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.route_role_id_seq OWNED BY users.route_role.id;


--
-- TOC entry 227 (class 1259 OID 16551)
-- Name: service; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.service (
    id integer NOT NULL,
    service_name character varying(255) NOT NULL,
    service_desc character varying(255)
);


ALTER TABLE users.service OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 16550)
-- Name: service_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER SEQUENCE users.service_id_seq OWNER TO postgres;

--
-- TOC entry 3460 (class 0 OID 0)
-- Dependencies: 226
-- Name: service_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.service_id_seq OWNED BY users.service.id;


--
-- TOC entry 221 (class 1259 OID 16511)
-- Name: user_group; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.user_group (
    group_id integer NOT NULL,
    user_id integer NOT NULL
);


ALTER TABLE users.user_group OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 16514)
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
-- TOC entry 223 (class 1259 OID 16519)
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
-- TOC entry 3461 (class 0 OID 0)
-- Dependencies: 223
-- Name: user_login_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.user_login_id_seq OWNED BY users.user_login.id;


--
-- TOC entry 224 (class 1259 OID 16520)
-- Name: user_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.user_role (
    user_id integer NOT NULL,
    role_id integer NOT NULL,
    id integer NOT NULL
);


ALTER TABLE users.user_role OWNER TO postgres;

--
-- TOC entry 225 (class 1259 OID 16523)
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
-- TOC entry 3462 (class 0 OID 0)
-- Dependencies: 225
-- Name: user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.user_role_id_seq OWNED BY users.user_role.id;


--
-- TOC entry 3260 (class 2604 OID 16524)
-- Name: group id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users."group" ALTER COLUMN id SET DEFAULT nextval('users.group_id_seq'::regclass);


--
-- TOC entry 3261 (class 2604 OID 16525)
-- Name: role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role ALTER COLUMN id SET DEFAULT nextval('users.role_id_seq'::regclass);


--
-- TOC entry 3266 (class 2604 OID 16563)
-- Name: route id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route ALTER COLUMN id SET DEFAULT nextval('users.route_id_seq'::regclass);


--
-- TOC entry 3267 (class 2604 OID 16577)
-- Name: route_role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role ALTER COLUMN id SET DEFAULT nextval('users.route_role_id_seq'::regclass);


--
-- TOC entry 3265 (class 2604 OID 16554)
-- Name: service id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.service ALTER COLUMN id SET DEFAULT nextval('users.service_id_seq'::regclass);


--
-- TOC entry 3263 (class 2604 OID 16526)
-- Name: user_login id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login ALTER COLUMN id SET DEFAULT nextval('users.user_login_id_seq'::regclass);


--
-- TOC entry 3264 (class 2604 OID 16527)
-- Name: user_role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role ALTER COLUMN id SET DEFAULT nextval('users.user_role_id_seq'::regclass);


--
-- TOC entry 3434 (class 0 OID 16497)
-- Dependencies: 216
-- Data for Name: group; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users."group" (id, group_name, reg_id) FROM stdin;
\.


--
-- TOC entry 3436 (class 0 OID 16501)
-- Dependencies: 218
-- Data for Name: group_role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.group_role (group_id, role_id) FROM stdin;
\.


--
-- TOC entry 3437 (class 0 OID 16504)
-- Dependencies: 219
-- Data for Name: role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.role (id, application, role_name, role_desc) FROM stdin;
1	notification	admin	Повний доступ
2	HEAD	ADMIN	\N
3	NOTIFICATION	USER	\N
4	TTS	STAFF	\N
5	TEST	ADMIN	Full access
6	TEST	USER	GET Access
\.


--
-- TOC entry 3447 (class 0 OID 16560)
-- Dependencies: 229
-- Data for Name: route; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.route (id, route, route_desc, service_id) FROM stdin;
1	/test/get	GET Request from Test service	1
2	/auth/signup	register new user	2
\.


--
-- TOC entry 3449 (class 0 OID 16574)
-- Dependencies: 231
-- Data for Name: route_role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.route_role (id, route_id, role_id) FROM stdin;
1	1	5
2	1	6
3	2	5
\.


--
-- TOC entry 3445 (class 0 OID 16551)
-- Dependencies: 227
-- Data for Name: service; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.service (id, service_name, service_desc) FROM stdin;
1	test	testing service
2	auth	authentication service
\.


--
-- TOC entry 3439 (class 0 OID 16511)
-- Dependencies: 221
-- Data for Name: user_group; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_group (group_id, user_id) FROM stdin;
\.


--
-- TOC entry 3440 (class 0 OID 16514)
-- Dependencies: 222
-- Data for Name: user_login; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_login (id, login, pass, authority, login_desc, contact_id) FROM stdin;
5	admin	$2a$10$qsbWM/ZdHdslTBieU8YHu.s9qRKt0RZCHNhR4UEUlWx9ArErayc0G	\N	\N	5
6	testuser	$2a$10$Cm/zdcq/iptRrsNGo5elUOmomCTfdQj/ckEIWXxSQCOp2iF67jzMm	\N	\N	6
7	newuser	$2a$10$DkFnAeU1t2sK1isX23.sL.dylRzM1OWLSUQ11qwqPaQohFYyJ2Fb.	\N	\N	9
8	check	$2a$10$X4y7XGqyHBFX7tEVgVVFee/4LUQVoSbL1GtqDTsxM38uZPlpxu72m	\N	\N	7
\.


--
-- TOC entry 3442 (class 0 OID 16520)
-- Dependencies: 224
-- Data for Name: user_role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_role (user_id, role_id, id) FROM stdin;
5	2	1
5	3	2
5	4	3
5	5	4
6	4	5
7	4	6
8	6	7
\.


--
-- TOC entry 3463 (class 0 OID 0)
-- Dependencies: 217
-- Name: group_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.group_id_seq', 1, false);


--
-- TOC entry 3464 (class 0 OID 0)
-- Dependencies: 220
-- Name: role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.role_id_seq', 6, true);


--
-- TOC entry 3465 (class 0 OID 0)
-- Dependencies: 228
-- Name: route_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.route_id_seq', 2, true);


--
-- TOC entry 3466 (class 0 OID 0)
-- Dependencies: 230
-- Name: route_role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.route_role_id_seq', 3, true);


--
-- TOC entry 3467 (class 0 OID 0)
-- Dependencies: 226
-- Name: service_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.service_id_seq', 2, true);


--
-- TOC entry 3468 (class 0 OID 0)
-- Dependencies: 223
-- Name: user_login_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.user_login_id_seq', 8, true);


--
-- TOC entry 3469 (class 0 OID 0)
-- Dependencies: 225
-- Name: user_role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.user_role_id_seq', 7, true);


--
-- TOC entry 3269 (class 2606 OID 16529)
-- Name: group group_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users."group"
    ADD CONSTRAINT group_pkey PRIMARY KEY (id);


--
-- TOC entry 3271 (class 2606 OID 16531)
-- Name: role right_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role
    ADD CONSTRAINT right_pkey PRIMARY KEY (id);


--
-- TOC entry 3273 (class 2606 OID 16533)
-- Name: role role_role_name_key; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role
    ADD CONSTRAINT role_role_name_key UNIQUE (role_name, application);


--
-- TOC entry 3283 (class 2606 OID 16567)
-- Name: route route_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route
    ADD CONSTRAINT route_pkey PRIMARY KEY (id);


--
-- TOC entry 3285 (class 2606 OID 16579)
-- Name: route_role route_role_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_pkey PRIMARY KEY (id);


--
-- TOC entry 3281 (class 2606 OID 16558)
-- Name: service service_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.service
    ADD CONSTRAINT service_pkey PRIMARY KEY (id);


--
-- TOC entry 3275 (class 2606 OID 16535)
-- Name: user_login user_login_key; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT user_login_key UNIQUE (login);


--
-- TOC entry 3279 (class 2606 OID 16537)
-- Name: user_role user_right_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT user_right_pkey PRIMARY KEY (id);


--
-- TOC entry 3277 (class 2606 OID 16539)
-- Name: user_login users_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- TOC entry 3289 (class 2606 OID 16590)
-- Name: route_role route_role_role_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES users.role(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;


--
-- TOC entry 3290 (class 2606 OID 16595)
-- Name: route_role route_role_route_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_route_id_fkey FOREIGN KEY (route_id) REFERENCES users.route(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;


--
-- TOC entry 3288 (class 2606 OID 16568)
-- Name: route route_service_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route
    ADD CONSTRAINT route_service_id_fkey FOREIGN KEY (service_id) REFERENCES users.service(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 3286 (class 2606 OID 16540)
-- Name: user_role users_rights_right_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_right_id_fkey FOREIGN KEY (role_id) REFERENCES users.role(id);


--
-- TOC entry 3287 (class 2606 OID 16545)
-- Name: user_role users_rights_user_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_user_id_fkey FOREIGN KEY (user_id) REFERENCES users.user_login(id);


-- Completed on 2025-01-14 06:25:29 EET

--
-- PostgreSQL database dump complete
--

