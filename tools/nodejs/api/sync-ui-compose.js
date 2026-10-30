"use strict";
// Keep the portable local state/callback implementation byte-identical across the two runtimes.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..'),shared=fs.readFileSync(path.join(__dirname,'portable-ui-compose.js'),'utf8');
for(const [file,end] of [
  [path.join(root,'app/src/main/cpp/node_bridge_sources.cpp'),'  function __autojs6_ui_options('],
  // Bun's generated sections follow the shared facades (AutoJs6 api-coverage P4.3: ending at createHostClient replaced them).
  [path.resolve(root,'../AutoJs6-Plugin-Bun-Runtime/app/src/main/assets/autojs6/host-client.mjs'),'// BEGIN GENERATED AUTOMATION']
]) {
  const text=fs.readFileSync(file,'utf8'),start=text.indexOf('  function __autojs6_create_ui(');
  const stop=text.indexOf(end,text.indexOf('  function __autojs6_create_compose(',start));
  assert(start>=0&&stop>start);
  const next=text.slice(0,start)+shared+'\n'+text.slice(stop);
  if(process.argv.includes('--check'))assert.equal(next.replace(/\n\n(?=  function __autojs6_ui_options|\/\/ BEGIN GENERATED AUTOMATION)/g,'\n'),text.replace(/\n\n(?=  function __autojs6_ui_options|\/\/ BEGIN GENERATED AUTOMATION)/g,'\n'),file);
  else fs.writeFileSync(file,next);
}
console.log('Shared UI/Compose facades synchronized');
