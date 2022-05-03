/*******************************************************************************
 * Copyright (c) 2011 Bruno Quoitin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Bruno Quoitin - initial API and implementation
 ******************************************************************************/
package p;

import reso.ip.*;

import java.util.*;

public class PingPongProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_PINGPONG= Datagram.allocateProtocolNumber("PING-PONG");
	
	private final IPHost host;


	public PingPongProtocol(IPHost host) {
		this.host= host;
	}

	static ArrayList toSend=new ArrayList<>();
	static ArrayList toSendS=new ArrayList<>();

	static ArrayList toReceive=new ArrayList<>();

	static int countM=3;

	static  int wTime=1;

	public void init(IPLayer ip,int num) throws Exception {
		toSend=toSplit(num);
		for (int i=0;i<toSend.size();i++)
		{
			ArrayList c=new ArrayList<>();
			String s= (String) toSend.get(i);
			Integer hI=Integer.parseInt(s);
			Segment sg=new Segment(hI,i);
			c.add(sg);
			c.add(false);
			toSendS.add(c);
			toReceive.add(c);
		}		System.out.println("fentre init "+countM+"\n");

		System.out.println("i " +toSend);
		System.out.println("f " +toSendS);
		sendT(ip,true);

	}
	static int sCount=0;
	public void sendT(IPLayer ip,boolean init) throws Exception {
		boolean end=false;
		for (int i =0;i<toSendS.size();i++)
		{
			if(host.equals(Demo.host1))
			{
				ArrayList a= (ArrayList) toSendS.get(i);
				Segment sg= (Segment) a.get(0);
				boolean bool= (boolean) a.get(1);

				if(!init) // Timer expiré
				{
					//System.out.println("timer expiré "+sg);
					for (int j=0;j<toSendS.size();j++)
					{
						ArrayList b= (ArrayList) toReceive.get(j);
						sg= (Segment) b.get(0);
						bool= (boolean) b.get(1);
						if(!bool)
						{
							if(sg.cCount<= sg.cMax)
							{
							ip.send(IPAddress.getByAddress("192.168.0.1"),IPAddress.getByAddress("192.168.0.2") , PingPongProtocol.IP_PROTO_PINGPONG, sg);
							sg.bTimer(this,ip);
							ArrayList h=new ArrayList<>();
							h.add(sg);
							h.add(true);
							toSendS.set(i,h);
							}
							else
							{
								//System.out.println(toSendS+"\n"+toReceive);
								//System.out.println("  Limite de tentative atteinte pour "+sg);
								//toReceive.remove(j);
								//toSendS.remove(j);
								//System.out.println(toSendS+"\n"+toReceive+"\n");
								sg.t.cancel();
								sg.th.cancel();
								end=true;
								break;
							}
							break;
						}

					}
				}
				else if(countM>0 && !bool) // On envoie les elements de la fenetre
				{


					ip.send(IPAddress.getByAddress("192.168.0.1"),IPAddress.getByAddress("192.168.0.2") , PingPongProtocol.IP_PROTO_PINGPONG, sg);
					sg.bTimer(this,ip);
					ArrayList h=new ArrayList<>();
					h.add(sg);
					h.add(true);
					toSendS.set(i,h);

					countM--;
					sCount++;
					//System.out.println("fentre now "+countM+" sg sent "+sg+"\n");
					//System.out.println("              timer ");
				}
				if(end)
				{
					System.out.println("Fin arreté");
					break;
				}

			}

			if(end)
			{
				System.out.println("Fin arreté");
				break;
			}
			/// RTO
			//ip.addListener(PingPongProtocol.IP_PROTO_PINGPONG, new PingPongProtocol (host ));

		}

	}

	static int fCount=0;

	public static ArrayList toSplit(int n)
	{
		ArrayList res=new ArrayList();
		//System.out.println("init "+n);
		String s=""+n;
		for(int i=0;i<s.length();i++)
		{
			res.add(""+s.charAt(i));
		}
		return res;
	}

	public static int cS=-1;
	public ArrayList bufferNoOrder=new ArrayList<>();
	public ArrayList bufferOrder=new ArrayList<>();


	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
		Object rec = (Object) datagram.getPayload();
		if (rec.getClass().equals(Segment.class))
		{
			Segment msg= (Segment) rec;
			int c=cS+1;
			System.out.println("   expectedSq : "+c+", received : "+msg.getS()+", msg : "+msg);

			if (cS < 0)
			{
				cS = 0;
				bufferOrder.add(msg);
			}
			else
			{

				if (msg.getS() == (cS + 1))
				{
					bufferOrder.add(msg);
					cS = cS + 1;
					//System.out.println("Order "+msg.getS()+" "+msg+ ",dgram.src=" + datagram.src + ", dgram.dst=" + datagram.dst );
				} else {
					bufferNoOrder.add(msg);
					//System.out.println("No Order : expected "+ cS+ " get : "+msg.getS()+" "+msg+ ",dgram.src=" + datagram.src + ", dgram.dst=" + datagram.dst );

				}
			}

			if (msg.getS()!=3)
				host.getIPLayer().send(datagram.dst, datagram.src, IP_PROTO_PINGPONG, new ACK(msg.getS()));

		}
		else
		{
			ACK msg= (ACK) rec;
			//System.out.println("\nAck sq "+msg.seq+" "+msg+ ",dgram.src=" + datagram.src + ", dgram.dst=" + datagram.dst );


			ArrayList c= (ArrayList) toReceive.get(msg.seq);
			System.out.println("\n   Ack  Sq received : "+msg.seq+" for : "+c );
			Segment s= (Segment) c.get(0);
			//System.out.println("Receive for "+s.num+" seq "+s.getS()+" ");
			s.sTimer();
			//System.out.println("init "+toSendS);
			//toSend.remove(""+s.num);
			//toSendS.remove(s);
			c.set(1,true);
			System.out.println("sent "+toSendS);
			System.out.println("receive "+toReceive+"\n");
			countM=countM+1;

			sendT(host.getIPLayer(),true);
			//System.out.println(" ok");
		}
	}

}
