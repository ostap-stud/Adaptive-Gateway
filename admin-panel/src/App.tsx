import { useEffect, useState } from 'react'
import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Activity, ArrowUpRight, Boxes, ChevronRight, GitBranch, KeyRound, LayoutDashboard, LogOut, Menu, RefreshCw, Route as RouteIcon, ShieldCheck, Users, X } from 'lucide-react'
import { ApiError, errorMessage } from './api'
import { Alert, Field } from './components'
import { DataProvider, useAuth, useData } from './state'
import { Dashboard, OperationsPage, RoutesPage, ServiceDetails, ServicesPage } from './pages/Inventory'
import { CreateUserPage, PermissionsPage, UsersPage } from './pages/Access'
import AdaptivePage from './pages/Adaptive'

const navigation=[
  {path:'/',label:'Dashboard',icon:LayoutDashboard},
  {path:'/services',label:'Services',icon:Boxes},
  {path:'/operations',label:'API Operations',icon:GitBranch},
  {path:'/routes',label:'Routes',icon:RouteIcon},
  {path:'/permissions',label:'Permissions',icon:KeyRound},
  {path:'/users',label:'Users / Roles',icon:Users},
  {path:'/adaptive',label:'Adaptive Manager',icon:Activity},
]

function Login() {
  const {signIn,notice}=useAuth()
  const navigate=useNavigate()
  const [login,setLogin]=useState(''),[password,setPassword]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false)
  async function submit(event:React.FormEvent) {
    event.preventDefault();setBusy(true);setError('')
    try {await signIn(login.trim(),password);navigate('/',{replace:true})}
    catch(error) {setError(error instanceof ApiError && error.status===403 ? 'Цей обліковий запис не має ролі адміністратора ROLE_INFRA_ADMIN.' : errorMessage(error))}
    finally {setBusy(false)}
  }
  return <div className="login-layout"><aside className="login-story"><Link className="brand" to="/login"><span className="brand-mark"><GitBranch size={22}/></span><span>Adaptive<span className="brand-light">Gateway</span><small>ADMIN CONSOLE</small></span></Link><div className="login-pitch"><span className="eyebrow">АДАПТИВНА ІНФРАСТРУКТУРА</span><h1>Ваша система.<br/>Під контролем.</h1><p>Сервіси, API та політики доступу —<br/>в одному робочому просторі.</p><div className="architecture"><div className="architecture-node"><Boxes size={22}/><span>Services<small>Eureka discovery</small></span></div><div className="architecture-line"/><div className="architecture-node highlight"><Activity size={22}/><span>Adaptive Manager<small>Observe · Analyze · Synchronize</small></span></div><div className="architecture-line"/><div className="architecture-pair"><div className="architecture-node"><ShieldCheck size={20}/><span>Auth Service</span></div><div className="architecture-node"><RouteIcon size={20}/><span>Gateway</span></div></div></div></div><div className="login-foot">Adaptive Gateway <span>Control plane / v1.0</span></div></aside><main className="login-form-wrap"><div className="login-form"><div className="login-icon"><ShieldCheck size={26}/></div><span className="eyebrow">ADMIN WORKSPACE</span><h2>Вхід до панелі</h2><p>Увійдіть з обліковим записом адміністратора.</p>{notice&&<Alert tone="info">{notice}</Alert>}{error&&<Alert>{error}</Alert>}<form onSubmit={submit}><Field label="Логін"><input autoFocus required autoComplete="username" value={login} onChange={e=>setLogin(e.target.value)} placeholder="Ваш логін" disabled={busy}/></Field><Field label="Пароль"><input required type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Введіть пароль" disabled={busy}/></Field><button className="button primary full" disabled={busy}>{busy?<><RefreshCw size={17} className="spin"/>Вхід…</>:<>Увійти до панелі<ChevronRight size={17}/></>}</button></form><div className="login-note"><KeyRound size={17}/><p>Створення нових користувачів доступне адміністратору в розділі <strong>Users / Roles</strong>.</p></div><small className="muted">Захищено JWT · Сесія зберігається лише в цій вкладці</small></div></main></div>
}

function Shell() {
  const {login,signOut}=useAuth(),{loading,refresh,adaptive,errors,refreshedAt}=useData()
  const location=useLocation(),[mobile,setMobile]=useState(false)
  useEffect(()=>{window.scrollTo(0,0)},[location.pathname])
  const current=navigation.find(item=>item.path==='/'?location.pathname==='/':location.pathname.startsWith(item.path))
  return <div className="app-shell"><aside className={`sidebar ${mobile?'open':''}`}><Link className="brand" to="/" onClick={()=>setMobile(false)}><span className="brand-mark"><GitBranch size={22}/></span><span>Adaptive<span className="brand-light">Gateway</span><small>ADMIN CONSOLE</small></span></Link><button className="mobile-close icon-button" aria-label="Закрити меню" onClick={()=>setMobile(false)}><X size={20}/></button><div className="nav-label">WORKSPACE</div><nav aria-label="Головна навігація">{navigation.map(({path,label,icon:Icon})=><NavLink key={path} to={path} end={path==='/'} onClick={()=>setMobile(false)} className={({isActive})=>`nav-item ${isActive?'active':''}`}><Icon size={19}/><span>{label}</span>{path==='/adaptive'&&<span className="nav-new">LIVE</span>}</NavLink>)}</nav><div className="sidebar-bottom"><div className="sidebar-manager"><Activity size={18}/><div><strong>Adaptive Manager</strong><small>{errors.adaptive?'З’єднання недоступне':adaptive?`Режим ${adaptive.mode}`:'Очікування даних'}</small></div><span className={`connection-dot ${!errors.adaptive&&adaptive?.registryStatus==='AVAILABLE'?'online':''}`}/></div><div className="sidebar-caption"><ShieldCheck size={14}/> Централізоване керування доступом</div></div></aside>{mobile&&<button className="sidebar-overlay" aria-label="Закрити меню" onClick={()=>setMobile(false)}/>}<div className="workspace"><header className="topbar"><div className="breadcrumb"><button className="mobile-menu icon-button" onClick={()=>setMobile(true)} aria-label="Відкрити меню"><Menu size={21}/></button><span>Workspace</span><ChevronRight size={14}/><strong>{current?.label || 'Admin'}</strong></div><div className="topbar-actions"><span className="environment"><span/>REST API</span><button className="icon-button" title={refreshedAt?`Оновлено: ${new Date(refreshedAt).toLocaleTimeString('uk-UA')}`:'Оновити дані'} aria-label="Оновити дані" disabled={loading} onClick={()=>void refresh()}><RefreshCw size={17} className={loading?'spin':''}/></button><div className="user-chip"><span className="avatar">{login.slice(0,2).toUpperCase()}</span><div><strong>{login}</strong><small>Administrator</small></div></div><button className="icon-button" aria-label="Вийти" title="Вийти" onClick={signOut}><LogOut size={17}/></button></div></header><main className="main-content"><Outlet/></main><footer className="workspace-footer"><span>Adaptive Gateway <span className="muted">/ Admin Console</span></span><span>Дані з REST API · оновлення кожні 15 с</span></footer></div></div>
}

export function ResourceErrors({resources}:{resources:string[]}) {
  const {errors,refresh}=useData()
  const entries=Object.entries(errors).filter(([resource])=>resources.includes(resource))
  if (!entries.length) return null
  return <div className="resource-errors">{entries.map(([resource,error])=><Alert key={resource} action={<button className="text-button" onClick={()=>void refresh()}>Повторити</button>}><strong>{resource}:</strong> {error} Збережені на екрані дані можуть бути застарілими.</Alert>)}</div>
}
function Protected() {const {token}=useAuth();return token?<DataProvider><Shell/></DataProvider>:<Navigate to="/login" replace/>}
function LoginRoute() {const {token}=useAuth();return token?<Navigate to="/" replace/>:<Login/>}
function NotFound(){return <div className="empty"><h1>Сторінку не знайдено</h1><Link to="/" className="button primary">На Dashboard<ArrowUpRight size={16}/></Link></div>}
export default function App(){return <Routes><Route path="/login" element={<LoginRoute/>}/><Route element={<Protected/>}><Route index element={<Dashboard/>}/><Route path="services" element={<ServicesPage/>}/><Route path="services/:name" element={<ServiceDetails/>}/><Route path="operations" element={<OperationsPage/>}/><Route path="routes" element={<RoutesPage/>}/><Route path="permissions" element={<PermissionsPage/>}/><Route path="users" element={<UsersPage/>}/><Route path="users/new" element={<CreateUserPage/>}/><Route path="register" element={<CreateUserPage/>}/><Route path="adaptive" element={<AdaptivePage/>}/><Route path="*" element={<NotFound/>}/></Route></Routes>}

