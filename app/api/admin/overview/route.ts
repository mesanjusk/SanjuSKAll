import {currentSession} from "@/lib/auth";
import {getDb} from "@/lib/mongo";
import {apiError,apiOk} from "@/lib/http";
export const dynamic="force-dynamic";
export async function GET(){
  const session=await currentSession();if(!session)return apiError("Please sign in",401);
  if(session.role!=="admin")return apiError("Admin access required",403);
  try{
    const db=await getDb();
    const [merchants,shipments,drafts]=await Promise.all([
      db.collection("users").countDocuments({role:"merchant"}),
      db.collection("shipments").countDocuments(),
      db.collection("shipments").countDocuments({status:"draft"})
    ]);
    return apiOk({merchants,shipments,drafts,booked:shipments-drafts,carrierIntegrations:0});
  }catch{return apiError("Admin overview unavailable",503);}
}
