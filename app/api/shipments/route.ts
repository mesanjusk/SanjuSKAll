import { NextRequest } from "next/server";
import {ObjectId} from "mongodb";
import {currentSession,audit} from "@/lib/auth";
import {apiError,apiOk} from "@/lib/http";
import {getDb} from "@/lib/mongo";
import {safeMutation} from "@/lib/security";
import {draftSchema} from "@/lib/validation";
export const dynamic="force-dynamic";
export async function GET(){
  const session=await currentSession();if(!session)return apiError("Please sign in",401);
  try {
    const db=await getDb();
    const shipments=await db.collection("shipments").find({merchantId:new ObjectId(session.sub)},{projection:{recipientMobile:0,address:0}}).sort({createdAt:-1}).limit(100).toArray();
    return apiOk({shipments:shipments.map(s=>({...s,_id:s._id.toString(),merchantId:undefined}))});
  }catch{return apiError("Unable to load shipments",503);}
}
export async function POST(request:NextRequest){
  if(!safeMutation(request))return apiError("Invalid request origin",403);
  const session=await currentSession();if(!session)return apiError("Please sign in",401);
  try{
    const value=draftSchema.safeParse(await request.json());
    if(!value.success)return apiError(value.error.issues[0]?.message||"Invalid shipment data",422);
    const db=await getDb();
    // Draft only: a courier AWB is never invented. Book only after contract-approved carrier APIs are connected.
    const now=new Date();
    const created=await db.collection("shipments").insertOne({...value.data,merchantId:new ObjectId(session.sub),status:"draft",carrier:null,trackingNumber:null,createdAt:now,updatedAt:now});
    await audit(session.sub,"shipment.draft_created",{shipmentId:created.insertedId.toString()});
    return apiOk({id:created.insertedId.toString(),status:"draft",message:"Saved as draft. Courier booking is not enabled yet."},201);
  }catch{return apiError("Unable to save draft",503);}
}
