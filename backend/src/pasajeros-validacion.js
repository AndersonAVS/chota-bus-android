import {ErrorHttp} from './errores.js';

// Valida el formulario sin aceptar UID, precios o asientos fuera del contrato.
export function validarPasajeros(cuerpo) {
  const invalido=()=>new ErrorHttp(400,'PASAJEROS_INVALIDOS','Completa un pasajero por asiento con nombre, DNI de 8 digitos y celular de 9 digitos.');
  if(!cuerpo||typeof cuerpo!=='object'||Array.isArray(cuerpo)||Object.keys(cuerpo).length!==1||
     !Array.isArray(cuerpo.pasajeros)||cuerpo.pasajeros.length<1||cuerpo.pasajeros.length>9)throw invalido();
  const datos=cuerpo.pasajeros.map(p=>{
    if(!p||typeof p!=='object'||Array.isArray(p)||Object.keys(p).length!==4||
      Object.keys(p).some(k=>!['asiento','nombreCompleto','dni','celular'].includes(k))||
      !Number.isInteger(p.asiento)||p.asiento<1||p.asiento>2147483647||
      typeof p.nombreCompleto!=='string'||!p.nombreCompleto.trim()||p.nombreCompleto.trim().length>200||
      typeof p.dni!=='string'||!/^\d{8}$/.test(p.dni)||typeof p.celular!=='string'||!/^\d{9}$/.test(p.celular))throw invalido();
    return{asiento:p.asiento,nombreCompleto:p.nombreCompleto.trim(),dni:p.dni,celular:p.celular};
  });
  if(new Set(datos.map(p=>p.asiento)).size!==datos.length)throw invalido();
  if(new Set(datos.map(p=>p.dni)).size!==datos.length)throw new ErrorHttp(400,'DNI_REPETIDO','Cada pasajero debe tener un DNI diferente.');
  return datos.sort((a,b)=>a.asiento-b.asiento);
}
