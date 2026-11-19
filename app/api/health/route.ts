import { apiOk } from "@/lib/http";
import {getDb,isDatabaseConfigured} from "@/lib/mongo";
export const dynamic="force-dynamic";
export async function GET(){
  if(!isDatabaseConfigured())return apiOk({service:"sk-ship",mode:"setup",database:"not-configured",bookingEnabled:false});
  try {await (await getDb()).command({ping:1});return apiOk({service:"sk-ship",mode:"connected",database:"online",bookingEnabled:false});}
  catch{return apiOk({service:"sk-ship",mode:"degraded",database:"unreachable",bookingEnabled:false});}
}
