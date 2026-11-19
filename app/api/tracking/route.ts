import {NextRequest} from "next/server";
import {getDb,isDatabaseConfigured} from "@/lib/mongo";
import {apiError,apiOk,databaseUnavailable} from "@/lib/http";
export const dynamic="force-dynamic";
export async function GET(request:NextRequest){
  if(!isDatabaseConfigured())return databaseUnavailable();
  const awb=(request.nextUrl.searchParams.get("awb")||"").trim();
  if(!/^[\w-]{6,45}$/.test(awb))return apiError("Enter a valid tracking number",422);
  try{
    const shipment=await (await getDb()).collection("shipments").findOne({trackingNumber:awb,status:{$ne:"draft"}},{projection:{trackingNumber:1,status:1,carrier:1,updatedAt:1}});
    if(!shipment)return apiError("No shipment found for this tracking number",404);
    return apiOk({tracking:{awb:shipment.trackingNumber,status:shipment.status,carrier:shipment.carrier,updatedAt:shipment.updatedAt}});
  }catch{return apiError("Tracking is unavailable",503);}
}
