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
package reso.pReseau;

import reso.ip.*;

import java.util.*;

public class SRProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_PINGPONG= Datagram.allocateProtocolNumber("PING-PONG");
	
	private final IPHost host;


	public SRProtocol(IPHost host) {
		this.host= host;
	}

	static ArrayList toSend=new ArrayList<>();
	static ArrayList toSendS=new ArrayList<>();

	static ArrayList toReceive=new ArrayList<>();

	static ArrayList checkingT=new ArrayList<>();

	public void init(IPLayer ip,int num) throws Exception {
		toSend=toSplit(num); // Divise le msg en segment
		for (int i=0;i<toSend.size();i++) // Envoi chaque segment
		{
			ArrayList c=new ArrayList<>();
			String s= (String) toSend.get(i);
			Integer hI=Integer.parseInt(s);
			Segment sg=new Segment(hI,i);
			c.add(sg);
			c.add(false);
			toSendS.add(c);
			toReceive.add(c);

			sendT(sg,ip,false);
			checkingT.add(c);
			//sg.bTimer();
		}		//System.out.println("fentre init "+countM+"\n");

		//count=
		//System.out.println("i " +toSend);
		//System.out.println("f " +toReceive);
		//sendT(ip,true);

	}
	static int sCount=0;
	static boolean nz=false;
	public void sendT(Object m,IPLayer ip,boolean again) throws Exception
	{
		//System.out.println("ok ok : "+m);
		if (!again)  // 1er envoi de segment
		{
			if (m.getClass().equals(Segment.class)) // envoi segemnt
			{
				Segment sg = (Segment) m;
				System.out.println("ok ok seq:  " + sg.getSq() + " seg : " + sg.getSeg()+" ip : "+ip);

				ip.send(IPAddress.getByAddress("192.168.0.1"), IPAddress.getByAddress("192.168.0.2"), SRProtocol.IP_PROTO_PINGPONG, sg);
				sg.bTimer(this, ip);
			}
			else // envoi ACK
			{
				ACK ack = (ACK) m;
				if (ack.getSq() != 1 || nz) {
					ip.send(IPAddress.getByAddress("192.168.0.2"), IPAddress.getByAddress("192.168.0.1"), SRProtocol.IP_PROTO_PINGPONG, ack);
					if (ack.getSq() == 1) {
						System.out.println("||||||resent 1 done");
					}
				}
				if (ack.getSq() == 1) {
					nz = true;
				}
			}
		}
		else // renvoi segment manquant
		{

			for (int i = 0; i < checkingT.size(); i++) {
				ArrayList a = (ArrayList) checkingT.get(i);
				Segment sg = (Segment) a.get(0);
				boolean c = (boolean) a.get(1);
				//System.out.println("ici "+a);
				if (!c) {
					System.out.println(" on renvoi : seg " + sg.getSeg() + " seq : " + sg.getSq()+" ip : "+ip);
					Demo.host1.getIPLayer().send(IPAddress.getByAddress("192.168.0.1"), IPAddress.getByAddress("192.168.0.2"), SRProtocol.IP_PROTO_PINGPONG, sg);
					sg.bTimer(this,ip);

				}
			}
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

	public static int rCount=0;
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
		Object rec = (Object) datagram.getPayload();
		System.out.println("final : "+rec);
		if (rec.getClass().equals(Segment.class)) // recoit un segment
		{
			Segment msg= (Segment) rec;
			System.out.println("   expectedSq : "+rCount+", received : "+msg.getSq()+", msg : "+msg.getSeg());
				if (msg.getSq() == (rCount)) // bon numero de sequence reçu
				{

					bufferOrder.add(msg);
					rCount++;
					ACK ack=new ACK(msg.getSq());
					sendT(ack,host.getIPLayer(),false);
					//System.out.println("Order "+msg.getS()+" "+msg+ ",dgram.src=" + datagram.src + ", dgram.dst=" + datagram.dst );
				}
				else // mauvais numero de sequence reussi
				{
					bufferNoOrder.add(msg);
					//System.out.println("No Order : expected "+ cS+ " get : "+msg.getS()+" "+msg+ ",dgram.src=" + datagram.src + ", dgram.dst=" + datagram.dst );
				}

		}
		else // recoit un ACK
		{
			ACK msg= (ACK) rec;
			ArrayList a= (ArrayList) checkingT.get(msg.getSq());
			Segment s= (Segment) a.get(0);
			System.out.println("Receive for "+s.getSeg()+" seq "+s.getSq()+" ");

			s.sTimer();
			a.set(1,true); // ACK du segment reçu

			//System.out.println("sent "+toSendS);
			//System.out.println("receive "+toReceive+"\n");

			//System.out.println(" ok");
		}
	}

}
