import { z } from "zod";
export const registrationSchema = z.object({
  name:z.string().trim().min(2).max(100),
  company:z.string().trim().min(2).max(120),
  email:z.email().max(180).transform(s=>s.toLowerCase()),
  mobile:z.string().regex(/^[6-9]\d{9}$/, "Enter a valid 10-digit Indian mobile number"),
  password:z.string().min(10).max(128),
});
export const loginSchema = z.object({email:z.email().transform(s=>s.toLowerCase()),password:z.string().min(1)});
export const draftSchema = z.object({
  orderRef:z.string().trim().min(1).max(80),
  recipientName:z.string().trim().min(2).max(100),
  recipientMobile:z.string().regex(/^[6-9]\d{9}$/),
  address:z.string().trim().min(10).max(350),
  originPin:z.string().regex(/^\d{6}$/),
  destinationPin:z.string().regex(/^\d{6}$/),
  weightGrams:z.number().int().min(1).max(100000),
  paymentMode:z.enum(["prepaid","cod"]),
  codAmount:z.number().min(0).max(1000000),
}).refine(data=>data.paymentMode!=="cod" || data.codAmount>0,{message:"COD amount is required for COD shipments"});
