import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Check, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { body, errorMessage, request } from '../api'
import { Alert, Badge, DataTable, Field, Modal, Panel, RolePicker } from '../components'
import { hydrateRoute, parseComponent } from '../routeForms'
import type { ComponentDraft } from '../routeForms'
import { useAuth, useData, useToast } from '../state'
import type { Route, Service } from '../types'

export function ServiceActions({service,create=false}:{service?:Service;create?:boolean}) {
  const [dialog,setDialog]=useState<'edit'|'delete'|null>(null)
  return <><div className="row-actions">{create?<button className="button primary" onClick={()=>setDialog('edit')}><Plus size={16}/>Створити сервіс</button>:<><button className="button small-button" onClick={()=>setDialog('edit')} aria-label={`Редагувати сервіс ${service?.serviceName}`}><Pencil size={14}/>Редагувати</button><button className="icon-button danger-text" onClick={()=>setDialog('delete')} aria-label={`Видалити сервіс ${service?.serviceName}`}><Trash2 size={16}/></button></>}</div>{dialog==='edit'&&<ServiceEditor service={service} onClose={()=>setDialog(null)}/>} {dialog==='delete'&&service&&<ConfirmMutation title={`Видалити сервіс ${service.serviceName}?`} confirmation={service.serviceName} path={`/service/${service.id}`} method="DELETE" onClose={()=>setDialog(null)} redirect="/services" description="Буде видалено сервіс разом із його маршрутами, API operations та політиками. Це незворотне видалення. Якщо сервіс залишається в Eureka і дозволений для адаптації, наступний APPLY може зареєструвати його знову."/>}</>
}

function ServiceEditor({service,onClose}:{service?:Service;onClose:()=>void}) {
  const {token}=useAuth(),{refresh}=useData(),notify=useToast(),navigate=useNavigate()
  const [name,setName]=useState(service?.serviceName || ''),[description,setDescription]=useState(service?.serviceDesc || ''),[busy,setBusy]=useState(false),[error,setError]=useState('')
  async function save(event:React.FormEvent) {
    event.preventDefault();setBusy(true);setError('')
    try {
      const dto={name:name.trim(),description:description.trim() || null}
      if (!dto.name) throw new Error('Укажіть назву сервісу.')
      await request(service?`/service/${service.id}`:'/service/add',token,{method:service?'PUT':'POST',body:body(service?dto:[dto])})
      notify('Сервіс збережено. Для зміни маршрутизації опублікуйте конфігурацію Gateway.');onClose();await refresh()
      if(service && name!==service.serviceName)navigate(`/services/${encodeURIComponent(name.trim())}`)
    }catch(error){setError(errorMessage(error))}finally{setBusy(false)}
  }
  return <Modal title={service?'Редагувати сервіс':'Створити сервіс'} busy={busy} onClose={onClose} footer={<><button className="button" disabled={busy} onClick={onClose}>Скасувати</button><button className="button primary" form="service-form" disabled={busy}>{busy?'Збереження…':'Зберегти сервіс'}</button></>}><form id="service-form" onSubmit={save}>{error&&<Alert>{error}</Alert>}<Field label="Назва сервісу" hint="Eureka service ID або http(s) URL для ручного зовнішнього сервісу."><input required maxLength={255} value={name} disabled={busy||service?.managementMode==='AUTOMATIC'} onChange={e=>setName(e.target.value)} placeholder="catalog-service"/></Field>{service?.managementMode==='AUTOMATIC'&&<Alert tone="info">Для перейменування спочатку переведіть сервіс у MANUAL. Зміна опису не змінює GENERATED конфігурацію.</Alert>}<Field label="Опис сервісу"><textarea rows={3} maxLength={2000} value={description} disabled={busy} onChange={e=>setDescription(e.target.value)}/></Field>{!service&&<p className="muted small">Новий сервіс створюється в MANUAL. Додайте його маршрути самостійно або змініть management mode для адаптації.</p>}</form></Modal>
}

function ConfirmMutation({title,description,path,method,payload,onClose,confirmation,redirect}:{title:string;description:string;path:string;method:string;payload?:unknown;onClose:()=>void;confirmation?:string;redirect?:string}) {
  const {token}=useAuth(),{refresh}=useData(),notify=useToast(),navigate=useNavigate()
  const [value,setValue]=useState(''),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('')
  async function submit() {
    setBusy(true);setError('')
    try{await request(path,token,{method,...(payload!==undefined?{body:body(payload)}:{})});notify('Зміни збережено в Auth. Опублікуйте маршрути в Gateway.');onClose();if(redirect)navigate(redirect);await refresh()}
    catch(error){setError(errorMessage(error))}finally{setBusy(false)}
  }
  return <Modal title={title} busy={busy} onClose={onClose} footer={<><button className="button" disabled={busy} onClick={onClose}>Скасувати</button><button className="button danger-button" disabled={busy||(confirmation?value!==confirmation:!confirmed)} onClick={()=>void submit()}>{busy?'Виконання…':'Підтвердити'}</button></>}>{error&&<Alert>{error}</Alert>}<Alert tone="warning">{description}</Alert>{confirmation?<Field label={`Введіть ${confirmation} для підтвердження`}><input value={value} onChange={e=>setValue(e.target.value)} disabled={busy} autoComplete="off"/></Field>:<label className="confirm-check"><input type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)} disabled={busy}/><span>Підтверджую зміну маршрутизації та доступу.</span></label>}</Modal>
}

export function PublishRoutes() {
  const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('')
  const {token}=useAuth(),notify=useToast()
  async function publish(){setBusy(true);setError('');try{await request('/actuator/gateway/refresh',token,{method:'POST'},'');notify('Gateway прийняв запит на оновлення маршрутів.');setOpen(false)}catch(error){setError(errorMessage(error))}finally{setBusy(false)}}
  return <><button className="button" onClick={()=>{setError('');setOpen(true)}}><RefreshCw size={16}/>Опублікувати маршрути</button>{open&&<Modal title="Оновити маршрути Gateway?" busy={busy} onClose={()=>setOpen(false)} footer={<><button className="button" disabled={busy} onClick={()=>setOpen(false)}>Скасувати</button><button className="button primary" disabled={busy} onClick={()=>void publish()}>{busy?'Оновлення…':'Підтвердити публікацію'}</button></>}>{error&&<Alert>{error}</Alert>}<p>Gateway перечитає з Auth усю конфігурацію маршрутів, включно з ручними змінами. Це не запускає Adaptive Manager і не змінює його режим.</p></Modal>}</>
}

export function RouteActions({route,serviceName,create=false}:{route?:Route;serviceName?:string;create?:boolean}) {
  const {errors,loading,refreshedAt}=useData()
  const [dialog,setDialog]=useState<'edit'|'delete'|'state'|null>(null)
  const unavailable=!!errors.components||!!errors.roles||!!errors.services||(loading&&!refreshedAt)
  return <><div className="row-actions">{create?<button className="button primary" disabled={unavailable} onClick={()=>setDialog('edit')}><Plus size={16}/>Створити маршрут</button>:<><button className="button small-button" disabled={unavailable} onClick={()=>setDialog('edit')} aria-label={`Редагувати маршрут ${route?.route}`}><Pencil size={14}/>Редагувати</button><button className="text-button" onClick={()=>setDialog('state')} aria-label={`${route?.active?'Вимкнути':'Увімкнути'} маршрут ${route?.route}`}>{route?.active?'Вимкнути':'Увімкнути'}</button><button className="icon-button danger-text" aria-label={`Видалити маршрут ${route?.route}`} onClick={()=>setDialog('delete')}><Trash2 size={15}/></button></>}</div>{dialog==='edit'&&<RouteEditor route={route} serviceName={serviceName} onClose={()=>setDialog(null)}/>} {dialog==='delete'&&route&&<ConfirmMutation title={`Видалити маршрут #${route.id}?`} description={route.source==='GENERATED'?'GENERATED маршрут буде деактивовано з ручним override. Наступний sync не відновить його автоматично.':'MANUAL маршрут і його прив’язки буде видалено. Після публікації Gateway перестане ним користуватися.'} path={`/service/route/${route.id}`} method="DELETE" confirmation={route.route} onClose={()=>setDialog(null)}/>} {dialog==='state'&&route&&<ConfirmMutation title={`${route.active?'Вимкнути':'Увімкнути'} маршрут #${route.id}?`} description={`Зміна active впливає на маршрутизацію та доступ. ${route.source==='GENERATED'?'Для GENERATED буде встановлено ручний override.':''} Після збереження опублікуйте конфігурацію Gateway.`} path={`/service/route/${route.id}/state`} method="PUT" payload={{active:!route.active}} onClose={()=>setDialog(null)}/>}</>
}

export function ServiceRoutes({service}:{service:Service}) {
  const {routes,components}=useData()
  const items=routes.filter(route=>route.serviceId===service.id)
  return <Panel title="Маршрути сервісу" subtitle="Ручне керування шляхами, ролями, фільтрами та предикатами" actions={<RouteActions create serviceName={service.serviceName}/>}><DataTable headers={['Path','Стан / джерело','Фільтри','Предикати','Дії']} empty={!items.length}>{items.map(route=><tr key={route.id}><td><code>{route.route}</code></td><td><Badge>{route.active?'ACTIVE':'INACTIVE'}</Badge><small className="cell-sub">{route.isOverridden?'OVERRIDE':route.source}</small></td><td>{route.filters.map(ref=>components.filters.find(f=>f.id===ref.filterId)?.name || `#${ref.filterId}`).join(', ') || '—'}</td><td>{route.predicates.map(ref=>components.predicates.find(p=>p.id===ref.predicateId)?.name || `#${ref.predicateId}`).join(', ') || '—'}</td><td><RouteActions route={route}/></td></tr>)}</DataTable></Panel>
}

function ComponentFields({kind,value,onChange,disabled}:{kind:'filters'|'predicates';value:ComponentDraft[];onChange:(value:ComponentDraft[])=>void;disabled:boolean}) {
  const label=kind==='filters'?'Фільтри':'Предикати'
  const options=kind==='filters'?['Authentication','StripPrefix','RewritePath','AddRequestHeader','AddResponseHeader','SetPath','RemoveRequestHeader','RemoveResponseHeader','Retry','CircuitBreaker']:['Method','Path','Header','Query','Host','Cookie','RemoteAddr','Before','After','Between']
  function update(index:number,patch:Partial<ComponentDraft>){onChange(value.map((item,i)=>i===index?{...item,...patch}:item))}
  return <section className="component-editor"><div className="component-heading"><h3>{label}</h3><button type="button" className="text-button" disabled={disabled} onClick={()=>onChange([...value,{key:crypto.randomUUID(),name:kind==='filters'?'Authentication':'Method',args:kind==='filters'?'{}':'{\n  "methods": "GET"\n}'}])}><Plus size={15}/>Додати {kind==='filters'?'фільтр':'предикат'}</button></div><datalist id={`${kind}-suggestions`}>{options.map(name=><option key={name} value={name}/>)}</datalist>{value.length?value.map((item,index)=><div className="component-row" key={item.key}><div className="component-row-heading"><Field label={`${kind==='filters'?'Фільтр':'Предикат'} ${index+1}`}><input list={`${kind}-suggestions`} required value={item.name} disabled={disabled} onChange={e=>update(index,{name:e.target.value})}/></Field><button type="button" className="icon-button danger-text" disabled={disabled} aria-label={`Прибрати ${kind==='filters'?'фільтр':'предикат'} ${index+1}`} onClick={()=>onChange(value.filter((_,i)=>i!==index))}><Trash2 size={17}/></button></div><Field label={`Параметри ${item.name || index+1} (JSON)`} hint="Об’єкт ключ → рядкове значення, наприклад { &quot;methods&quot;: &quot;GET,POST&quot; }."><textarea className="json-input" rows={3} value={item.args} disabled={disabled} onChange={e=>update(index,{args:e.target.value})} spellCheck={false}/></Field></div>):<p className="muted small">Додаткові {label.toLowerCase()} не задані.</p>}</section>
}

function RouteEditor({route,serviceName,onClose}:{route?:Route;serviceName?:string;onClose:()=>void}) {
  const {services,roles,components,refresh}=useData(),{token}=useAuth(),notify=useToast()
  const [hydrated]=useState(()=>{try{return {...(route?hydrateRoute(route,components,roles):{roles:[],filters:[{key:'new',name:'Authentication',args:'{}'}],predicates:[]}),error:''}}catch(error){return {roles:[],filters:[],predicates:[],error:errorMessage(error)}}})
  const [path,setPath]=useState(route?.route || ''),[description,setDescription]=useState(route?.routeDesc || ''),[service,setService]=useState(serviceName || services.find(s=>s.id===route?.serviceId)?.serviceName || services[0]?.serviceName || ''),[internal,setInternal]=useState(route?.isInternal || false),[order,setOrder]=useState(route?.order || 0)
  const [selected,setSelected]=useState<string[]>(hydrated.roles),[filters,setFilters]=useState<ComponentDraft[]>(hydrated.filters),[predicates,setPredicates]=useState<ComponentDraft[]>(hydrated.predicates),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(hydrated.error)
  const generated=route?.source==='GENERATED'
  const noAuth=!generated&&!internal&&!filters.some(f=>f.name==='Authentication')
  async function save(event:React.FormEvent){
    event.preventDefault();if(hydrated.error||!confirmed)return;setBusy(true);setError('')
    try{
      if(!path.startsWith('/'))throw new Error('Route path має починатися з /.')
      if(!Number.isInteger(order)||Math.abs(order)>2147483647)throw new Error('Order має бути цілим числом у межах Int.')
      if(!internal&&!services.some(s=>s.serviceName===service))throw new Error('Оберіть існуючий сервіс.')
      const dto={route:path.trim(),routeDescription:description.trim() || null,serviceName:internal?'':service,isInternal:internal,order,roles:selected,filters:filters.map(parseComponent),predicates:predicates.map(parseComponent)}
      await request(route?`/service/route/${route.id}`:'/service/route/add',token,{method:route?'PUT':'POST',body:body(route?dto:[dto])})
      notify('Маршрут, ролі, фільтри та предикати збережено. Опублікуйте маршрути в Gateway.');onClose();await refresh()
    }catch(error){setError(errorMessage(error))}finally{setBusy(false)}
  }
  return <Modal title={route?`Редагувати маршрут #${route.id}`:'Створити маршрут'} busy={busy} onClose={onClose} footer={<><button className="button" disabled={busy} onClick={onClose}>Скасувати</button><button className="button primary" form="route-form" disabled={busy||!confirmed||!!hydrated.error}><Check size={16}/>{busy?'Збереження…':'Зберегти маршрут'}</button></>}><form id="route-form" onSubmit={save}>{error&&<Alert>{error}</Alert>}<Field label="Route path" hint="Шаблон Spring Gateway, наприклад /catalog/**."><input required maxLength={2048} value={path} disabled={busy} onChange={e=>setPath(e.target.value)} placeholder="/catalog/**"/></Field><Field label="Опис маршруту"><textarea rows={2} value={description} disabled={busy} onChange={e=>setDescription(e.target.value)}/></Field><Field label="Сервіс маршруту"><select value={service} disabled={busy||generated||internal} onChange={e=>setService(e.target.value)}>{services.map(s=><option key={s.id} value={s.serviceName}>{s.serviceName}</option>)}</select></Field><label className="check-inline"><input type="checkbox" checked={internal} disabled={busy||generated} onChange={e=>setInternal(e.target.checked)}/>Internal route Gateway</label><Field label="Order" hint="Менше число — вищий пріоритет. Дозволені від’ємні значення."><input type="number" step={1} required value={order} disabled={busy} onChange={e=>setOrder(Number(e.target.value))}/></Field><h3 className="form-section-title">Маршрутні ролі</h3><RolePicker roles={roles} value={selected} onChange={setSelected} disabled={busy}/><ComponentFields kind="filters" value={filters} onChange={setFilters} disabled={busy}/><ComponentFields kind="predicates" value={predicates} onChange={setPredicates} disabled={busy}/>{generated&&<Alert tone="warning">Ручне редагування створить override. Manager більше не перезаписуватиме цей route. Gateway завжди додає Authentication до GENERATED route; operation policies залишаються чинними.</Alert>}{noAuth&&<Alert tone="warning">Без Authentication у MANUAL service route Gateway не перевірятиме JWT і маршрутні ролі. Переконайтеся, що публічний доступ потрібний.</Alert>}<label className="confirm-check"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e=>setConfirmed(e.target.checked)}/><span>Підтверджую конфігурацію маршруту та доступу. Зміни стосуються цього маршруту; після збереження їх потрібно опублікувати.</span></label></form></Modal>
}
