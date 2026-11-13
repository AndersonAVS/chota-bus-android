import {exigirAutenticacion} from './autenticacion.js';
import {ErrorHttp} from './errores.js';
import {crearBoletosRepository} from './boletos-repository.js';

// Solo permite consultar boletos propios; un identificador no sustituye la autenticacion.
export function registrarBoletos(app,pool,opciones={}){
 const auth=exigirAutenticacion(opciones.verificarToken);
 const repo=opciones.boletosRepository??crearBoletosRepository(pool);
 const validar=id=>{if(typeof id!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id))
  throw new ErrorHttp(400,'DATOS_INVALIDOS','El identificador no es valido.');};
 // Limita cada respuesta y no acepta un UID enviado por el cliente.
 app.get('/api/v1/boletos',auth,async(req,res)=>{
  if(Object.keys(req.query).some(k=>!['despues','limite'].includes(k)))throw new ErrorHttp(400,'DATOS_INVALIDOS','Revisa los parametros de consulta.');
  const despues=req.query.despues;
  if(despues!==undefined)validar(despues);
  const limite=req.query.limite===undefined?20:Number(req.query.limite);
  if(req.query.limite!==undefined&&(typeof req.query.limite!=='string'||!(/^[1-9][0-9]?$/.test(req.query.limite)))||!Number.isInteger(limite)||limite<1||limite>50)
   throw new ErrorHttp(400,'DATOS_INVALIDOS','El limite debe estar entre 1 y 50.');
  const pagina=await repo.listar(req.usuarioUid,despues??null,limite);
  res.json({datos:pagina.boletos,siguiente:pagina.siguiente});
 });
 app.get('/api/v1/reservas/:id/boletos' ,auth,async(req,res)=>{
  validar(req.params.id);res.json({datos:await repo.deReserva(req.usuarioUid,req.params.id)});
 });
 app.get('/api/v1/boletos/:id',auth,async(req,res)=>{
  validar(req.params.id);res.json({datos:await repo.obtener(req.usuarioUid,req.params.id)});
 });
}
