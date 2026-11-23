import { useState } from 'react'
import { Settings2 } from 'lucide-react'
import { body, errorMessage, request } from '../api'
import { Alert, Field, Modal } from '../components'
import { useAuth, useData, useToast } from '../state'

export default function ManagerModeControl({cycleBusy}:{cycleBusy:boolean}) {
  const {adaptive,errors,refresh}=useData(),{token}=useAuth(),notify=useToast()
  const [open,setOpen]=useState(false),[mode,setMode]=useState('OBSERVE'),[confirm,setConfirm]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('')
  async function save(){
    setBusy(true);setError('')
    try{await request('/admin/adaptive/mode',token,{method:'PUT',body:body({mode})});notify(`Режим ${mode} увімкнено. Він діятиме для наступних циклів.`);setOpen(false);await refresh()}
    catch(error){setError(errorMessage(error))}finally{setBusy(false)}
  }
  return <><button className="button" disabled={cycleBusy||!adaptive||!!errors.adaptive} onClick={()=>{setMode(adaptive?.mode==='APPLY'?'OBSERVE':'APPLY');setConfirm(false);setError('');setOpen(true)}}><Settings2 size={16}/>Змінити режим</button>{open&&<Modal title="Режим Adaptive Manager" busy={busy} onClose={()=>setOpen(false)} footer={<><button className="button" disabled={busy} onClick={()=>setOpen(false)}>Скасувати</button><button className="button primary" disabled={busy||!confirm||mode===adaptive?.mode} onClick={()=>void save()}>{busy?'Перемикання…':`Увімкнути ${mode}`}</button></>}>{error&&<Alert>{error}</Alert>}<Field label="Режим Manager"><select value={mode} disabled={busy} onChange={e=>{setMode(e.target.value);setConfirm(false)}}><option value="OBSERVE">OBSERVE — лише аналізувати</option><option value="APPLY">APPLY — застосовувати конфігурацію</option></select></Field><div className="mode-explanation"><p><strong>OBSERVE</strong> виявляє сервіси, читає OpenAPI та показує пропозиції змін. Auth і Gateway не змінюються. Дозволяє переглянути виявлені зміни перед застосуванням.</p><p><strong>APPLY</strong> виконує той самий аналіз, потім синхронізує допущену GENERATED конфігурацію з Auth і публікує її в Gateway. MANUAL/DISABLED та ручні override залишаються захищеними.</p></div><Alert tone={mode==='APPLY'?'warning':'info'}>{mode==='APPLY'?'Наступні планові та ручні цикли зможуть змінювати конфігурацію. Перемикання саме по собі не запускає синхронізацію.':'Наступні цикли продовжать спостереження, але не записуватимуть конфігурацію.'} Активний цикл не переривається: під час його роботи backend поверне 409.</Alert><p className="muted small">Зміна діє до перезапуску Manager. Після перезапуску використовується manager.mode зі стартової конфігурації.</p><label className="confirm-check"><input type="checkbox" checked={confirm} disabled={busy} onChange={e=>setConfirm(e.target.checked)}/><span>Підтверджую перехід у {mode} для наступних циклів.</span></label></Modal>}</>
}
