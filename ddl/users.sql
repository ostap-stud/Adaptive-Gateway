--
-- PostgreSQL database dump
--

\restrict UF4SOfYh8T82QaO0Qo1JMgi6lAbC8PVzGwCjipoDh1I17P6PNesnw6b5QcdPH4a

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

-- Started on 2026-09-28 21:31:10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 6 (class 2615 OID 16389)
-- Name: users; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA users;


ALTER SCHEMA users OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 239 (class 1259 OID 16518)
-- Name: filter; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.filter (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    args jsonb
);


ALTER TABLE users.filter OWNER TO postgres;

--
-- TOC entry 237 (class 1259 OID 16507)
-- Name: filter_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.filter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER SEQUENCE users.filter_id_seq OWNER TO postgres;

--
-- TOC entry 5139 (class 0 OID 0)
-- Dependencies: 237
-- Name: filter_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.filter_id_seq OWNED BY users.filter.id;


--
-- TOC entry 220 (class 1259 OID 16390)
-- Name: group; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users."group" (
    id integer NOT NULL,
    group_name character varying(255),
    reg_id integer
);


ALTER TABLE users."group" OWNER TO postgres;

--
-- TOC entry 5140 (class 0 OID 0)
-- Dependencies: 220
-- Name: TABLE "group"; Type: COMMENT; Schema: users; Owner: postgres
--

COMMENT ON TABLE users."group" IS 'Обєднання кілька правил в один набір';


--
-- TOC entry 221 (class 1259 OID 16394)
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
-- TOC entry 5141 (class 0 OID 0)
-- Dependencies: 221
-- Name: group_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.group_id_seq OWNED BY users."group".id;


--
-- TOC entry 222 (class 1259 OID 16395)
-- Name: group_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.group_role (
    group_id integer NOT NULL,
    role_id integer NOT NULL
);


ALTER TABLE users.group_role OWNER TO postgres;

--
-- TOC entry 238 (class 1259 OID 16508)
-- Name: predicate; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.predicate (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    args jsonb
);


ALTER TABLE users.predicate OWNER TO postgres;

--
-- TOC entry 236 (class 1259 OID 16506)
-- Name: predicate_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.predicate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER SEQUENCE users.predicate_id_seq OWNER TO postgres;

--
-- TOC entry 5142 (class 0 OID 0)
-- Dependencies: 236
-- Name: predicate_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.predicate_id_seq OWNED BY users.predicate.id;


--
-- TOC entry 223 (class 1259 OID 16400)
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
-- TOC entry 224 (class 1259 OID 16409)
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
-- TOC entry 5143 (class 0 OID 0)
-- Dependencies: 224
-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.role_id_seq OWNED BY users.role.id;


--
-- TOC entry 225 (class 1259 OID 16410)
-- Name: route; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.route (
    id integer NOT NULL,
    route character varying NOT NULL,
    route_desc character varying,
    service_id integer NOT NULL,
    is_internal boolean DEFAULT false NOT NULL,
    "order" integer
);


ALTER TABLE users.route OWNER TO postgres;

--
-- TOC entry 243 (class 1259 OID 16549)
-- Name: route_filter; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.route_filter (
    id integer NOT NULL,
    route_id integer NOT NULL,
    filter_id integer
);


ALTER TABLE users.route_filter OWNER TO postgres;

--
-- TOC entry 241 (class 1259 OID 16529)
-- Name: route_filter_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.route_filter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER SEQUENCE users.route_filter_id_seq OWNER TO postgres;

--
-- TOC entry 5144 (class 0 OID 0)
-- Dependencies: 241
-- Name: route_filter_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.route_filter_id_seq OWNED BY users.route_filter.id;


--
-- TOC entry 226 (class 1259 OID 16418)
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
-- TOC entry 5145 (class 0 OID 0)
-- Dependencies: 226
-- Name: route_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.route_id_seq OWNED BY users.route.id;


--
-- TOC entry 242 (class 1259 OID 16530)
-- Name: route_predicate; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.route_predicate (
    id integer NOT NULL,
    route_id integer NOT NULL,
    predicate_id integer NOT NULL
);


ALTER TABLE users.route_predicate OWNER TO postgres;

--
-- TOC entry 240 (class 1259 OID 16528)
-- Name: route_predicate_id_seq; Type: SEQUENCE; Schema: users; Owner: postgres
--

CREATE SEQUENCE users.route_predicate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


ALTER SEQUENCE users.route_predicate_id_seq OWNER TO postgres;

--
-- TOC entry 5146 (class 0 OID 0)
-- Dependencies: 240
-- Name: route_predicate_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.route_predicate_id_seq OWNED BY users.route_predicate.id;


--
-- TOC entry 227 (class 1259 OID 16419)
-- Name: route_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.route_role (
    id integer NOT NULL,
    route_id integer NOT NULL,
    role_id integer NOT NULL
);


ALTER TABLE users.route_role OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 16425)
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
-- TOC entry 5147 (class 0 OID 0)
-- Dependencies: 228
-- Name: route_role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.route_role_id_seq OWNED BY users.route_role.id;


--
-- TOC entry 229 (class 1259 OID 16426)
-- Name: service; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.service (
    id integer NOT NULL,
    service_name character varying(255) NOT NULL,
    service_desc character varying(255)
);


ALTER TABLE users.service OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 16433)
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
-- TOC entry 5148 (class 0 OID 0)
-- Dependencies: 230
-- Name: service_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.service_id_seq OWNED BY users.service.id;


--
-- TOC entry 231 (class 1259 OID 16434)
-- Name: user_group; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.user_group (
    group_id integer NOT NULL,
    user_id integer NOT NULL
);


ALTER TABLE users.user_group OWNER TO postgres;

--
-- TOC entry 232 (class 1259 OID 16439)
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
-- TOC entry 233 (class 1259 OID 16446)
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
-- TOC entry 5149 (class 0 OID 0)
-- Dependencies: 233
-- Name: user_login_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.user_login_id_seq OWNED BY users.user_login.id;


--
-- TOC entry 234 (class 1259 OID 16447)
-- Name: user_role; Type: TABLE; Schema: users; Owner: postgres
--

CREATE TABLE users.user_role (
    user_id integer NOT NULL,
    role_id integer NOT NULL,
    id integer NOT NULL
);


ALTER TABLE users.user_role OWNER TO postgres;

--
-- TOC entry 235 (class 1259 OID 16453)
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
-- TOC entry 5150 (class 0 OID 0)
-- Dependencies: 235
-- Name: user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: users; Owner: postgres
--

ALTER SEQUENCE users.user_role_id_seq OWNED BY users.user_role.id;


--
-- TOC entry 4925 (class 2604 OID 16521)
-- Name: filter id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.filter ALTER COLUMN id SET DEFAULT nextval('users.filter_id_seq'::regclass);


--
-- TOC entry 4915 (class 2604 OID 16454)
-- Name: group id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users."group" ALTER COLUMN id SET DEFAULT nextval('users.group_id_seq'::regclass);


--
-- TOC entry 4924 (class 2604 OID 16511)
-- Name: predicate id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.predicate ALTER COLUMN id SET DEFAULT nextval('users.predicate_id_seq'::regclass);


--
-- TOC entry 4916 (class 2604 OID 16455)
-- Name: role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role ALTER COLUMN id SET DEFAULT nextval('users.role_id_seq'::regclass);


--
-- TOC entry 4918 (class 2604 OID 16456)
-- Name: route id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route ALTER COLUMN id SET DEFAULT nextval('users.route_id_seq'::regclass);


--
-- TOC entry 4927 (class 2604 OID 16552)
-- Name: route_filter id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_filter ALTER COLUMN id SET DEFAULT nextval('users.route_filter_id_seq'::regclass);


--
-- TOC entry 4926 (class 2604 OID 16533)
-- Name: route_predicate id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_predicate ALTER COLUMN id SET DEFAULT nextval('users.route_predicate_id_seq'::regclass);


--
-- TOC entry 4920 (class 2604 OID 16457)
-- Name: route_role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role ALTER COLUMN id SET DEFAULT nextval('users.route_role_id_seq'::regclass);


--
-- TOC entry 4921 (class 2604 OID 16458)
-- Name: service id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.service ALTER COLUMN id SET DEFAULT nextval('users.service_id_seq'::regclass);


--
-- TOC entry 4922 (class 2604 OID 16459)
-- Name: user_login id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login ALTER COLUMN id SET DEFAULT nextval('users.user_login_id_seq'::regclass);


--
-- TOC entry 4923 (class 2604 OID 16460)
-- Name: user_role id; Type: DEFAULT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role ALTER COLUMN id SET DEFAULT nextval('users.user_role_id_seq'::regclass);


--
-- TOC entry 5129 (class 0 OID 16518)
-- Dependencies: 239
-- Data for Name: filter; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.filter (id, name, args) FROM stdin;
1	Authentication	{}
2	AddResponseHeader	{"name": "X-Response-Test", "value": "Test"}
\.


--
-- TOC entry 5110 (class 0 OID 16390)
-- Dependencies: 220
-- Data for Name: group; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users."group" (id, group_name, reg_id) FROM stdin;
\.


--
-- TOC entry 5112 (class 0 OID 16395)
-- Dependencies: 222
-- Data for Name: group_role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.group_role (group_id, role_id) FROM stdin;
\.


--
-- TOC entry 5128 (class 0 OID 16508)
-- Dependencies: 238
-- Data for Name: predicate; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.predicate (id, name, args) FROM stdin;
\.


--
-- TOC entry 5113 (class 0 OID 16400)
-- Dependencies: 223
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
-- TOC entry 5115 (class 0 OID 16410)
-- Dependencies: 225
-- Data for Name: route; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.route (id, route, route_desc, service_id, is_internal, "order") FROM stdin;
2	/auth-service/**	authentication routes	2	f	\N
3	/test-service/**	Endpoints for test service	1	f	0
\.


--
-- TOC entry 5133 (class 0 OID 16549)
-- Dependencies: 243
-- Data for Name: route_filter; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.route_filter (id, route_id, filter_id) FROM stdin;
2	3	1
3	3	2
\.


--
-- TOC entry 5132 (class 0 OID 16530)
-- Dependencies: 242
-- Data for Name: route_predicate; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.route_predicate (id, route_id, predicate_id) FROM stdin;
\.


--
-- TOC entry 5117 (class 0 OID 16419)
-- Dependencies: 227
-- Data for Name: route_role; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.route_role (id, route_id, role_id) FROM stdin;
3	2	5
6	3	5
\.


--
-- TOC entry 5119 (class 0 OID 16426)
-- Dependencies: 229
-- Data for Name: service; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.service (id, service_name, service_desc) FROM stdin;
2	auth-service	authentication service
1	test-service	testing service
\.


--
-- TOC entry 5121 (class 0 OID 16434)
-- Dependencies: 231
-- Data for Name: user_group; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_group (group_id, user_id) FROM stdin;
\.


--
-- TOC entry 5122 (class 0 OID 16439)
-- Dependencies: 232
-- Data for Name: user_login; Type: TABLE DATA; Schema: users; Owner: postgres
--

COPY users.user_login (id, login, pass, authority, login_desc, contact_id) FROM stdin;
5	admin	$2a$10$qsbWM/ZdHdslTBieU8YHu.s9qRKt0RZCHNhR4UEUlWx9ArErayc0G	\N	\N	5
6	testuser	$2a$10$Cm/zdcq/iptRrsNGo5elUOmomCTfdQj/ckEIWXxSQCOp2iF67jzMm	\N	\N	6
7	newuser	$2a$10$DkFnAeU1t2sK1isX23.sL.dylRzM1OWLSUQ11qwqPaQohFYyJ2Fb.	\N	\N	9
8	check	$2a$10$X4y7XGqyHBFX7tEVgVVFee/4LUQVoSbL1GtqDTsxM38uZPlpxu72m	\N	\N	7
\.


--
-- TOC entry 5124 (class 0 OID 16447)
-- Dependencies: 234
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
-- TOC entry 5151 (class 0 OID 0)
-- Dependencies: 237
-- Name: filter_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.filter_id_seq', 2, true);


--
-- TOC entry 5152 (class 0 OID 0)
-- Dependencies: 221
-- Name: group_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.group_id_seq', 1, false);


--
-- TOC entry 5153 (class 0 OID 0)
-- Dependencies: 236
-- Name: predicate_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.predicate_id_seq', 1, false);


--
-- TOC entry 5154 (class 0 OID 0)
-- Dependencies: 224
-- Name: role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.role_id_seq', 6, true);


--
-- TOC entry 5155 (class 0 OID 0)
-- Dependencies: 241
-- Name: route_filter_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.route_filter_id_seq', 3, true);


--
-- TOC entry 5156 (class 0 OID 0)
-- Dependencies: 226
-- Name: route_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.route_id_seq', 3, true);


--
-- TOC entry 5157 (class 0 OID 0)
-- Dependencies: 240
-- Name: route_predicate_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.route_predicate_id_seq', 1, false);


--
-- TOC entry 5158 (class 0 OID 0)
-- Dependencies: 228
-- Name: route_role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.route_role_id_seq', 6, true);


--
-- TOC entry 5159 (class 0 OID 0)
-- Dependencies: 230
-- Name: service_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.service_id_seq', 2, true);


--
-- TOC entry 5160 (class 0 OID 0)
-- Dependencies: 233
-- Name: user_login_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.user_login_id_seq', 8, true);


--
-- TOC entry 5161 (class 0 OID 0)
-- Dependencies: 235
-- Name: user_role_id_seq; Type: SEQUENCE SET; Schema: users; Owner: postgres
--

SELECT pg_catalog.setval('users.user_role_id_seq', 7, true);


--
-- TOC entry 4949 (class 2606 OID 16527)
-- Name: filter filter_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.filter
    ADD CONSTRAINT filter_pkey PRIMARY KEY (id);


--
-- TOC entry 4929 (class 2606 OID 16462)
-- Name: group group_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users."group"
    ADD CONSTRAINT group_pkey PRIMARY KEY (id);


--
-- TOC entry 4947 (class 2606 OID 16517)
-- Name: predicate predicate_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.predicate
    ADD CONSTRAINT predicate_pkey PRIMARY KEY (id);


--
-- TOC entry 4931 (class 2606 OID 16464)
-- Name: role right_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role
    ADD CONSTRAINT right_pkey PRIMARY KEY (id);


--
-- TOC entry 4933 (class 2606 OID 16466)
-- Name: role role_role_name_key; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.role
    ADD CONSTRAINT role_role_name_key UNIQUE (role_name, application);


--
-- TOC entry 4953 (class 2606 OID 16556)
-- Name: route_filter route_filter_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_filter
    ADD CONSTRAINT route_filter_pkey PRIMARY KEY (id);


--
-- TOC entry 4935 (class 2606 OID 16468)
-- Name: route route_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route
    ADD CONSTRAINT route_pkey PRIMARY KEY (id);


--
-- TOC entry 4951 (class 2606 OID 16538)
-- Name: route_predicate route_predicate_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_predicate
    ADD CONSTRAINT route_predicate_pkey PRIMARY KEY (id);


--
-- TOC entry 4937 (class 2606 OID 16470)
-- Name: route_role route_role_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_pkey PRIMARY KEY (id);


--
-- TOC entry 4939 (class 2606 OID 16472)
-- Name: service service_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.service
    ADD CONSTRAINT service_pkey PRIMARY KEY (id);


--
-- TOC entry 4941 (class 2606 OID 16474)
-- Name: user_login user_login_key; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT user_login_key UNIQUE (login);


--
-- TOC entry 4945 (class 2606 OID 16476)
-- Name: user_role user_right_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT user_right_pkey PRIMARY KEY (id);


--
-- TOC entry 4943 (class 2606 OID 16478)
-- Name: user_login users_pkey; Type: CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_login
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- TOC entry 4961 (class 2606 OID 16557)
-- Name: route_filter route_filter_filter_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_filter
    ADD CONSTRAINT route_filter_filter_id_fkey FOREIGN KEY (filter_id) REFERENCES users.filter(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 4962 (class 2606 OID 16562)
-- Name: route_filter route_filter_route_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_filter
    ADD CONSTRAINT route_filter_route_id_fkey FOREIGN KEY (route_id) REFERENCES users.route(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 4959 (class 2606 OID 16539)
-- Name: route_predicate route_predicate_predicate_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_predicate
    ADD CONSTRAINT route_predicate_predicate_id_fkey FOREIGN KEY (predicate_id) REFERENCES users.predicate(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 4960 (class 2606 OID 16544)
-- Name: route_predicate route_predicate_route_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_predicate
    ADD CONSTRAINT route_predicate_route_id_fkey FOREIGN KEY (route_id) REFERENCES users.route(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 4955 (class 2606 OID 16479)
-- Name: route_role route_role_role_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES users.role(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;


--
-- TOC entry 4956 (class 2606 OID 16484)
-- Name: route_role route_role_route_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route_role
    ADD CONSTRAINT route_role_route_id_fkey FOREIGN KEY (route_id) REFERENCES users.route(id) ON UPDATE CASCADE ON DELETE CASCADE NOT VALID;


--
-- TOC entry 4954 (class 2606 OID 16489)
-- Name: route route_service_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.route
    ADD CONSTRAINT route_service_id_fkey FOREIGN KEY (service_id) REFERENCES users.service(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 4957 (class 2606 OID 16494)
-- Name: user_role users_rights_right_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_right_id_fkey FOREIGN KEY (role_id) REFERENCES users.role(id);


--
-- TOC entry 4958 (class 2606 OID 16499)
-- Name: user_role users_rights_user_id_fkey; Type: FK CONSTRAINT; Schema: users; Owner: postgres
--

ALTER TABLE ONLY users.user_role
    ADD CONSTRAINT users_rights_user_id_fkey FOREIGN KEY (user_id) REFERENCES users.user_login(id);


-- Completed on 2026-09-28 21:31:10

--
-- PostgreSQL database dump complete
--

\unrestrict UF4SOfYh8T82QaO0Qo1JMgi6lAbC8PVzGwCjipoDh1I17P6PNesnw6b5QcdPH4a

