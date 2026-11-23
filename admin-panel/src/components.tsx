import { cloneElement, isValidElement, useEffect, useId, useRef, useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { AlertCircle, Check, ChevronDown, Search, X } from 'lucide-react'
import { statusTone } from './domain'

export function Badge({children,tone}:{children:ReactNode;tone?:string}) {return <span className={`badge ${tone || statusTone(String(children))}`}>{children}</span>}
export function Method({value}:{value:string}) {return <span className={`method ${value.toLowerCase()}`}>{value}</span>}
export function PageTitle({eyebrow,title,description,actions}:{eyebrow?:string;title:string;description:string;actions?:ReactNode}) {return <header className="page-title"><div>{eyebrow&&<div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1><p>{description}</p></div><div className="page-actions">{actions}</div></header>}
export function Panel({title,subtitle,actions,children,className=''}:{title?:string;subtitle?:string;actions?:ReactNode;children:ReactNode;className?:string}) {return <section className={`panel ${className}`}>{title&&<div className="panel-heading"><div><h2>{title}</h2>{subtitle&&<p>{subtitle}</p>}</div>{actions}</div>}{children}</section>}
export function Alert({children,tone='error',action}:{children:ReactNode;tone?:string;action?:ReactNode}) {return <div className={`alert ${tone}`} role={tone==='error'?'alert':'status'}><AlertCircle size={18}/><div>{children}</div>{action}</div>}
export function Empty({title='Даних ще немає',children}:{title?:string;children?:ReactNode}) {return <div className="empty"><div className="empty-symbol">◇</div><h3>{title}</h3><p>{children || 'Дані з’являться після підключення сервісів і першого циклу спостереження.'}</p></div>}
export function Skeleton({rows=4}:{rows?:number}) {return <div className="skeletons" aria-label="Завантаження даних" aria-busy="true">{Array.from({length:rows},(_,i)=><div className="skeleton" key={i}/>)}</div>}
export function SearchInput({value,onChange,placeholder='Пошук…'}:{value:string;onChange:(value:string)=>void;placeholder?:string}) {return <label className="search-input"><Search size={17}/><input aria-label={placeholder} placeholder={placeholder} value={value} onChange={event=>onChange(event.target.value)}/>{value&&<button aria-label="Очистити пошук" onClick={()=>onChange('')}><X size={14}/></button>}</label>}
export function Select({label,value,onChange,children}:{label:string;value:string;onChange:(value:string)=>void;children:ReactNode}) {return <label className="select-control"><span className="sr-only">{label}</span><select aria-label={label} value={value} onChange={event=>onChange(event.target.value)}>{children}</select><ChevronDown size={14}/></label>}
export function DataTable({headers,children,empty=false}:{headers:string[];children:ReactNode;empty?:boolean}) {return <div className="table-scroll"><table><thead><tr>{headers.map((header,index)=><th key={`${header}-${index}`} scope="col">{header}</th>)}</tr></thead><tbody>{children}</tbody></table>{empty&&<Empty title="Нічого не знайдено">Спробуйте змінити пошук або фільтри.</Empty>}</div>}

export function Modal({title,children,onClose,footer,busy=false}:{title:string;children:ReactNode;onClose:()=>void;footer?:ReactNode;busy?:boolean}) {
  const ref=useRef<HTMLDialogElement>(null)
  useEffect(()=>{
    const previous=document.activeElement as HTMLElement|null
    ref.current?.showModal()
    return ()=>{previous?.focus()}
  },[])
  return <dialog ref={ref} className="modal" aria-labelledby="modal-title" onCancel={event=>{event.preventDefault();if(!busy) onClose()}}><div className="modal-heading"><h2 id="modal-title">{title}</h2><button className="icon-button" aria-label="Закрити діалог" disabled={busy} onClick={onClose}><X size={20}/></button></div><div className="modal-body">{children}</div>{footer&&<div className="modal-footer">{footer}</div>}</dialog>
}
export function RolePicker({roles,value,onChange,disabled=false}:{roles:{role:string;roleDesc?:string}[];value:string[];onChange:(roles:string[])=>void;disabled?:boolean}) {
  const [search,setSearch]=useState('')
  return <div className="role-picker"><SearchInput value={search} onChange={setSearch} placeholder="Знайти роль…"/><div className="role-options">{roles.filter(role=>role.role.toLowerCase().includes(search.toLowerCase())).map(role=><label key={role.role} className="role-option"><input type="checkbox" disabled={disabled} checked={value.includes(role.role)} onChange={event=>onChange(event.target.checked?[...value,role.role]:value.filter(r=>r!==role.role))}/><span><strong>{role.role}</strong>{role.roleDesc&&<small>{role.roleDesc}</small>}</span>{value.includes(role.role)&&<Check size={16}/>}</label>)}{!roles.length&&<p className="muted">Спочатку створіть роль.</p>}</div><small className="muted">Обрано ролей: {value.length}</small></div>
}
export function Field({label,children,hint}:{label:string;children:ReactNode;hint?:string}) {
  const id=useId(),hintId=`${id}-hint`
  const control=isValidElement(children)?cloneElement(children as ReactElement<{id?:string;'aria-describedby'?:string}>,{id,'aria-describedby':hint?hintId:undefined}):children
  return <div className="field"><label htmlFor={id}>{label}</label>{control}{hint&&<small id={hintId}>{hint}</small>}</div>
}
