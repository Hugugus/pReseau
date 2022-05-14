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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SRProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_PINGPONG= Datagram.allocateProtocolNumber("PING-PONG");
	
	private final IPHost host;


	public SRProtocol(IPHost host) {
		this.host= host;
	}

	static ArrayList toSend=new ArrayList<>(); // ArrayList qui contiendra chaque segment à envoyer

	static ArrayList checkingT=new ArrayList<>(); // ArrayList qui contiendra pour chaque segment, une arraylist contenant le segment et un boolean qui indique si son ack a été reçu

	static ArrayList ackT=new ArrayList(); // ArrayList qui contiendra chaque ack
	static int ackC=0; //Permet de compter le nombre de fois qu'un ack est repeté (pour faciliter Multiplicative decrease)

	static double wSize=1; // taille de la fenetre initial
	static LocalDateTime init; // temps lors de l'envoi d'un segment de test
	static LocalDateTime end; // temps lors de la reception de l'ack de test

	static String num; // sauvegarde ce qu'il va falloir envoyé

	static boolean first=true; // permet de savoir si on envoi le segment de test

	static boolean slowS=true; // permet de savoir si on est en slow start
	static int slowEnd=2; // Seuil du slow Start


	int count=0;// Permet verifier que les envois respectent la taille de la fenetre
	int nano; // Sauvegarde le temps qu'il faut à un packet pour être envoyé et recevoir son ack

	//double alpha=0.125;

	int current=0; // Permet de savoir où on en est dans l'envoi de segment

	public void init(IPLayer ip,Object n) throws Exception // Envoi un segment test pour obtenir le temps neccesaire à l'obtention d'un ack
	{
		System.out.println("A envoyer "+n);
		num= (String) n;
		init = LocalDateTime.now();
		Segment sg=new Segment(0,0);
		sendT(sg,ip,false);
	}

	public void start() throws Exception // commence l'envoi
	{
		toSend=toSplit(num); // Divise le msg en segment

		//System.out.println("init : "+init);
		//System.out.println("end "+end);
		long sec=Duration.between(init, end).getSeconds();
		nano = Duration.between(init, end).getNano();
		System.out.println("difference : "+sec+"s "+nano);
		System.out.println("wSize : "+wSize);
		int help=0;

		for (int i=0;i<toSend.size();i++) // Envoi chaque segment
		{
			if(help==wSize) // Arrete lorsque la taille de la fenetre est atteinte
			{
				break;
			}

			ArrayList c=new ArrayList<>();
			String s= (String) toSend.get(i);
			Integer hI=Integer.parseInt(s);
			Segment sg=new Segment(hI,i);
			c.add(sg);
			c.add(false);

			sendT(sg,host.getIPLayer(),false);
			//System.out.println("ici "+sg);
			checkingT.add(c);
			help++;
			current++;

			//sg.bTimer();
		}
	}

	public void cSend() throws Exception { // continue a envoyer des segment
		int help=0;
		for (int i=current;i<toSend.size();i++) // Envoi chaque segment
		{
			if(help+1>=wSize) //Arrete lorsque la taille de la fenetre va etre depassé
			{
				break;
			}
			ArrayList c=new ArrayList<>();
			String s= (String) toSend.get(i);
			Integer hI=Integer.parseInt(s);
			Segment sg=new Segment(hI,i);
			c.add(sg);
			c.add(false);

			sendT(sg,host.getIPLayer(),false);
			checkingT.add(c);
			help++;
			current++;

			//sg.bTimer();
		}
	}

	static int sCount=0;
	static boolean nz=false;
	public void sendT(Object m,IPLayer ip,boolean again) throws Exception
	{
		//System.out.println("ok ok : "+m);
		if (!again)  // Pas de selective repeat
		{
			if (m.getClass().equals(Segment.class)) // envoi segemnt
			{
				Segment sg = (Segment) m;

				ip.send(IPAddress.getByAddress("192.168.0.1"), IPAddress.getByAddress("192.168.0.2"), SRProtocol.IP_PROTO_PINGPONG, sg);
				if(!first)
				{
					sg.bTimer(this, ip);
				}
			}
			else // envoi ACK
			{
				ACK ack = (ACK) m;
				ip.send(IPAddress.getByAddress("192.168.0.2"), IPAddress.getByAddress("192.168.0.1"), SRProtocol.IP_PROTO_PINGPONG, ack);

				/***if (ack.getSq() != 1 || nz) {
					ip.send(IPAddress.getByAddress("192.168.0.2"), IPAddress.getByAddress("192.168.0.1"), SRProtocol.IP_PROTO_PINGPONG, ack);
					if (ack.getSq() == 1) {
						System.out.println("||||||resent 1 done");
					}
				}
				if (ack.getSq() == 1) {
					nz = true;
				}*/
			}
		}
		else // Selective repeat
		{

			for (int i = 0; i < checkingT.size(); i++) {
				ArrayList a = (ArrayList) checkingT.get(i);
				Segment sg = (Segment) a.get(0);
				boolean c = (boolean) a.get(1);
				//System.out.println("ici "+a);
				if (!c)
				{
					System.out.println(" on renvoi : seg " + sg.getSeg() + " seq : " + sg.getSq()+" ip : "+ip);
					ip.send(IPAddress.getByAddress("192.168.0.1"), IPAddress.getByAddress("192.168.0.2"), SRProtocol.IP_PROTO_PINGPONG, sg);
					sg.bTimer(this,ip);

				}
			}
		}

	}


	static int fCount=0;

	public static ArrayList toSplit(Object n)
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
		if (rec.getClass().equals(Segment.class)) // recoit un segment
		{
			Segment msg= (Segment) rec;
			if (first)
			{
				ACK ack = new ACK(msg.getSq());
				sendT(ack, host.getIPLayer(), false);
			}
			else
			{
				System.out.println("   expectedSq : " + rCount + ", received : " + msg.getSq() + ", msg : " + msg.getSeg());
				ACK ack = new ACK(msg.getSq());
				if (msg.getSq() == (rCount)) // bon numero de sequence reçu
				{

					bufferOrder.add(msg);
					rCount++;

					//System.out.println("Order "+msg.getS()+" "+msg+ ",dgram.src=" + datagram.src + ", dgram.dst=" + datagram.dst );
				}
				else // mauvais numero de sequence reussi
				{
					bufferNoOrder.add(msg);
					//System.out.println("No Order : expected "+ cS+ " get : "+msg.getS()+" "+msg+ ",dgram.src=" + datagram.src + ", dgram.dst=" + datagram.dst );
				}
				sendT(ack, host.getIPLayer(), false);

			}

		}
		else // recoit un ACK
		{
			ACK msg = (ACK) rec;
			if (first)
			{
				end = LocalDateTime.now();
				first=false;
				start(); // on commence a tout renvoyer
			}

			else
			{
				if (!ackT.contains(msg))
				{
					ackT.add(msg);
					ArrayList a = (ArrayList) checkingT.get(msg.getSq());
					Segment s = (Segment) a.get(0);
					System.out.println("Receive for " + s.getSeg() + " seq " + s.getSq() + " ");

					s.sTimer();
					a.set(1, true); // ACK du segment reçu

					count++;
					if (slowS) // slow start
					{
						wSize++;
						if(wSize==slowEnd)
						{
							slowS=false;
							System.out.println("Slow start fini");
						}
						cSend();
					}
					else
					{
						if (count + 1 >= wSize) //Additive increase
						{
							//System.out.println("on attend pour le fenetre");
							long c = (long) (nano * wSize);
							TimeUnit.NANOSECONDS.sleep(c);
							count = 0;
							//double h1 = (1 - alpha) * wSize;
							//double h2 = (0.125 * wSize);
							//System.out.println("h1 " + h1 + " et h2 : " + h2 + " et wSize : " + wSize);
							wSize = wSize + (1 / wSize);
							System.out.println("ok attendu et wSize : " + wSize);
							cSend();
							//TimeUnit.SECONDS.sleep(2);
						}
					}
				}
				else //Multiplicative decrease
				{
					ackC++;
					if (ackC==3)
					{
						wSize=wSize/2;
						ackC=0;
					}
				}
			}

		}
	}

}
