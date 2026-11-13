// Mantiene treinta dias de salidas DEMO y solo repite SQL al cambiar el dia de Lima.
export function crearProgramador(pool,{ahora=()=>new Date(),entorno=process.env}={}){
 let diaProgramado=null,enCurso=null;
 return async function programar(){
  if(entorno.NODE_ENV==='production')return;
  const dia=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Lima',year:'numeric',month:'2-digit',day:'2-digit'}).format(ahora());
  if(dia===diaProgramado)return;
  if(enCurso)return enCurso;
  enCurso=(async()=>{
   const cliente=await pool.connect();
   try{
    await cliente.query("SET statement_timeout='60s'");
    await cliente.query('SELECT programar_salidas_demo($1::date,30)',[dia]);
    diaProgramado=dia;
   }finally{await cliente.query('RESET statement_timeout').catch(()=>{});cliente.release();}
  })();
  try{await enCurso;}finally{enCurso=null;}
 };
}
