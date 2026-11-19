import Link from "next/link";
export function Brand({small=false}:{small?:boolean}){
  return <Link href="/" className="brand"><span className="brand-icon"><span>S</span><span>K</span></span><span className="brand-name">SK<span>Ship</span>{!small&&<small>SMART LOGISTICS</small>}</span></Link>;
}
