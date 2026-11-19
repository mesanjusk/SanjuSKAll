import {Nav} from "@/components/nav";
import {Footer} from "@/components/footer";
import {TrackingForm} from "@/components/tracking-form";
import {MapPin,Package,Truck} from "lucide-react";
export const metadata={title:"Track Shipment"};
export default function Tracking(){return <><Nav/><main className="inner-page tracking-page"><div className="shell"><div className="inner-heading"><span className="kicker">SHIPMENT VISIBILITY</span><h1>Know where your<br/><span>package is.</span></h1><p>Enter a tracking number to check a shipment. Live carrier updates will be enabled after partner integration.</p></div><TrackingForm/><div className="tracking-steps"><span><Package size={20}/> Booked</span><span className="tracking-line"/><span><Truck size={20}/> In transit</span><span className="tracking-line"/><span><MapPin size={20}/> Delivered</span></div></div></main><Footer/></>}
