import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { body, errorMessage, request, sessionInfo } from './api'
import type { Operation, Permission, Resource, Role, Route, RouteComponents, Service, Snapshot, User } from './types'

const SESSION_KEY='adaptive-admin-session'
interface AuthState {token: string | null; login: string; notice: string; signIn: (login:string,password:string)=>Promise<void>; signOut: ()=>void}
const AuthContext=createContext<AuthState>(null!)
export const useAuth=()=>useContext(AuthContext)
export function AuthProvider({children}:{children:ReactNode}) {
  const [token,setToken]=useState<string|null>(()=>{
    const value=sessionStorage.getItem(SESSION_KEY)
    return value && (sessionInfo(value)?.expires || 0)>Date.now() ? value : null
  })
  const [notice,setNotice]=useState('')
  const signOut=useCallback(()=>{sessionStorage.removeItem(SESSION_KEY);setToken(null)},[])
  useEffect(()=>{
    if (!token) return
    const expire=()=>{signOut();setNotice('Сесію завершено. Увійдіть ще раз.')}
    window.addEventListener('auth-expired',expire)
    const timer=window.setTimeout(expire,Math.max(0,Math.min((sessionInfo(token)?.expires || 0)-Date.now(),2147483647)))
    return ()=>{window.removeEventListener('auth-expired',expire);clearTimeout(timer)}
  },[token,signOut])
  async function signIn(login:string,password:string) {
    const value=await request<string>('/login',null,{method:'POST',body:body({login,password})})
    if (typeof value!=='string' || !sessionInfo(value)) throw new Error('Сервер не повернув коректний JWT.')
    // Verify the current database role on the server before opening the admin panel.
    await request<Service[]>('/admin/services',value)
    sessionStorage.setItem(SESSION_KEY,value);setNotice('');setToken(value)
  }
  return <AuthContext.Provider value={{token,login:token ? sessionInfo(token)?.login || 'Administrator' : '',notice,signIn,signOut}}>{children}</AuthContext.Provider>
}

interface Data { services: Service[]; routes: Route[]; permissions: Permission[]; users: User[]; roles: Role[]; adaptive: Snapshot | null; operations: Record<string,Operation[]>; components: RouteComponents }
interface DataState extends Data {loading:boolean; refreshedAt:string|null; errors:Partial<Record<Resource,string>>; refresh:()=>Promise<void>}
const DataContext=createContext<DataState>(null!)
export const useData=()=>useContext(DataContext)
const initial:Data={services:[],routes:[],permissions:[],users:[],roles:[],adaptive:null,operations:{},components:{filters:[],predicates:[]}}
export function DataProvider({children}:{children:ReactNode}) {
  const {token}=useAuth()
  const [data,setData]=useState<Data>(initial)
  const [errors,setErrors]=useState<Partial<Record<Resource,string>>>({})
  const [loading,setLoading]=useState(true)
  const [refreshedAt,setRefreshedAt]=useState<string|null>(null)
  const controller=useRef<AbortController|null>(null)
  const refresh=useCallback(async()=>{
    controller.current?.abort()
    const active=new AbortController(); controller.current=active
    setLoading(true)
    const keys=['services','routes','permissions','users','roles','adaptive','components'] as const
    const results=await Promise.allSettled(keys.map(key=>request(`/admin/${key==='components'?'route-components':key}`,token,{signal:active.signal})))
    if (active.signal.aborted) return
    const next:Partial<Data>={}, failures:Partial<Record<Resource,string>>={}
    results.forEach((result,index)=>{
      const key=keys[index]
      if (result.status==='fulfilled') Object.assign(next,{[key]:result.value})
      else failures[key]=errorMessage(result.reason)
    })
    if (next.services) {
      const operations=await Promise.allSettled(next.services.map(async service=>[
        service.serviceName,
        (await request<Operation[]>(`/admin/services/${encodeURIComponent(service.serviceName)}/operations`,token,{signal:active.signal})).map(operation=>({...operation,serviceName:service.serviceName})),
      ] as const))
      if (active.signal.aborted) return
      next.operations={}
      operations.forEach((result,index)=>{
        if (result.status==='fulfilled') next.operations![result.value[0]]=result.value[1]
        else failures.operations=`Не отримано операції: ${next.services![index].serviceName}. ${errorMessage(result.reason)}`
      })
    }
    if (active.signal.aborted) return
    setData(previous=>({...previous,...next}));setErrors(failures);setRefreshedAt(new Date().toISOString());setLoading(false)
  },[token])
  useEffect(()=>{
    void refresh()
    const timer=window.setInterval(()=>{if(document.visibilityState==='visible') void refresh()},15000)
    return ()=>{clearInterval(timer);controller.current?.abort()}
  },[refresh])
  return <DataContext.Provider value={{...data,loading,errors,refreshedAt,refresh}}>{children}</DataContext.Provider>
}

type Notice={id:number;message:string;tone:'success'|'error'|'info'}
const ToastContext=createContext<(message:string,tone?:Notice['tone'])=>void>(()=>{})
export const useToast=()=>useContext(ToastContext)
export function ToastProvider({children}:{children:ReactNode}) {
  const [notices,setNotices]=useState<Notice[]>([])
  function notify(message:string,tone:Notice['tone']='success') {
    const id=Date.now()+Math.random()
    setNotices(previous=>[...previous,{id,message,tone}])
    window.setTimeout(()=>setNotices(previous=>previous.filter(n=>n.id!==id)),7000)
  }
  return <ToastContext.Provider value={notify}>{children}<div className="toasts" aria-live="polite">{notices.map(notice=><div key={notice.id} className={`toast ${notice.tone}`} role={notice.tone==='error'?'alert':'status'}><span>{notice.message}</span><button aria-label="Закрити повідомлення" onClick={()=>setNotices(previous=>previous.filter(n=>n.id!==notice.id))}>×</button></div>)}</div></ToastContext.Provider>
}
