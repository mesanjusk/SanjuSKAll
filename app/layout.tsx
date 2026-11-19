import type {Metadata} from "next";
import "./globals.css";
export const metadata:Metadata={title:{default:"SK Ship | Smarter Shipping for Indian Business",template:"%s | SK Ship"},description:"A modern courier aggregation workspace for Indian merchants. Manage parcels, compare partner shipping rates and track shipments in one place once integrations are enabled.",robots:{index:false,follow:false}};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>}
