// Developer-only isolated UI verification. Requires its own already initialized PostgreSQL.
// Never uses config/local.properties or an application database. Ctrl+C stops owned JVMs.
import { randomBytes } from 'node:crypto'
import { spawn } from 'node:child_process'
import { openSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
const repo=resolve(dirname(fileURLToPath(import.meta.url)),'..')
const output=resolve(repo,'target/frontend-qa')
mkdirSync(output,{recursive:true})
const java=process.env.QA_JAVA || 'C:/Program Files/Java/latest/jdk-21/bin/java.exe'
const env={...process.env,
  DB_SCHEMA_URL:'jdbc:postgresql://127.0.0.1:55489/adaptive_ui_qa?currentSchema=users',DB_USERNAME:'verifier',DB_PASSWORD:'isolated-test',
  JWT_SECRET:randomBytes(32).toString('base64'),JWT_EXPIRE:'7200000',
  ADAPTIVE_SYNC_KEY:'isolated-ui-qa-manager-key-at-least-32-characters',GATEWAY_REFRESH_KEY:'isolated-ui-qa-gateway-key-at-least-32-characters',
  BOOTSTRAP_ADMIN_LOGIN:'ui-admin',BOOTSTRAP_ADMIN_PASSWORD:'Local-qa-admin-2026!',KEY_STORE_PASSWORD_CHANGEIT:'unused-local-http',
}
const portOffset=Number(process.env.QA_PORT_OFFSET || 10000)
const ports={eureka:8761+portOffset,auth:8334+portOffset,gateway:8088+portOffset,demo:8996+portOffset,manager:8335+portOffset}
const children=[]
const intentional=new Set()
let managerProcess=null
let stopping=false
function stop(){if(stopping)return;stopping=true;for(const child of children)child.kill();setTimeout(()=>process.exit(0),1000)}
process.on('SIGINT',stop);process.on('SIGTERM',stop)
process.stdin.resume();process.stdin.on('data',async data=>{
  const command=data.toString().trim()
  if(command==='stop'){stop();return}
  if(command==='manager-stop' || command==='manager-observe' || command==='manager-apply'){
    if(managerProcess && managerProcess.exitCode===null){
      intentional.add(managerProcess.pid)
      const previous=managerProcess
      await new Promise(resolve=>{previous.once('exit',resolve);previous.kill()})
    }
    managerProcess=null
    if(command!=='manager-stop'){
      managerProcess=launch('manager','adaptive-manager/target/adaptive-manager-0.0.1-SNAPSHOT.jar',[
        `--manager.mode=${command==='manager-observe'?'OBSERVE':'APPLY'}`,
        '--manager.polling-enabled=false',
      ])
      await ready(`http://127.0.0.1:${ports.manager}/observations`)
    }
    console.log(`QA transition complete: ${command}`)
  }
})
async function ready(url,expected=200){
  for(let attempt=0;attempt<100;attempt++){
    if(stopping)throw new Error('Stopped')
    try{const response=await fetch(url,{signal:AbortSignal.timeout(2000)});if(response.status===expected)return}catch{}
    await new Promise(r=>setTimeout(r,1000))
  }
  throw new Error(`Service readiness timed out: ${url}`)
}
function launch(name,jar,args=[]){
  const log=openSync(resolve(output,`${name}.log`),'w')
  const child=spawn(java,['-Xms64m','-Xmx256m','-jar',resolve(repo,jar),'--spring.profiles.active=local',
    '--spring.config.import=optional:file:./target/frontend-qa/no-private-properties',
    `--server.port=${ports[name]}`,
    `--eureka.client.service-url.defaultZone=http://127.0.0.1:${ports.eureka}/eureka/`,
    ...(name==='auth'?[`--adaptive.manager.base-url=http://127.0.0.1:${ports.manager}`,`--adaptive.gateway.base-url=http://127.0.0.1:${ports.gateway}`]:[]),
    ...(name==='manager'?[`--manager.auth-base-url=http://127.0.0.1:${ports.auth}`,`--manager.eureka-url=http://127.0.0.1:${ports.eureka}/eureka/`]:[]),
    '--eureka.instance.lease-renewal-interval-in-seconds=5','--eureka.client.registry-fetch-interval-seconds=5',...args],
    {cwd:repo,env,windowsHide:true,stdio:['ignore',log,log]})
  children.push(child)
  child.on('error',error=>{console.error(`${name}: ${error.message}`);stop()})
  child.on('exit',code=>{if(!stopping&&!intentional.has(child.pid)){console.error(`${name} exited (${code}); inspect its local log`);stop()}})
  writeFileSync(resolve(output,'processes.json'),JSON.stringify(children.map(c=>c.pid)))
  return child
}
try{
  launch('eureka','discoveryserver/target/discoveryserver-0.0.3-.jar')
  await ready(`http://127.0.0.1:${ports.eureka}/eureka/apps`);console.log('Eureka ready')
  launch('auth','auth-service/target/auth-service-0.0.1-SNAPSHOT.jar')
  await ready(`http://127.0.0.1:${ports.auth}/auth-service/v3/api-docs`);console.log('Auth ready')
  launch('gateway','gatewayserver/target/gatewayserver-0.0.1-SNAPSHOT.jar')
  launch('demo','test-service/target/test-service-0.0.1-SNAPSHOT.jar',['--spring.profiles.active=local,adaptive-demo'])
  await ready(`http://127.0.0.1:${ports.demo}/adaptive-demo/v3/api-docs`);console.log('Demo ready')
  managerProcess=launch('manager','adaptive-manager/target/adaptive-manager-0.0.1-SNAPSHOT.jar')
  await ready(`http://127.0.0.1:${ports.manager}/observations`);console.log('Manager ready')
  await ready(`http://127.0.0.1:${ports.gateway}/adaptive-demo/demo/public`);console.log('Gateway ready; public demo reachable. Isolated backend ready.')
}catch(error){console.error(error.message);stop()}
