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

	static long t1;
	static long t2;

	static ArrayList toSend=new ArrayList<>(); // ArrayList qui contiendra chaque segment à envoyer

	static ArrayList checkingT=new ArrayList<>(); // ArrayList qui contiendra pour chaque segment, une arraylist contenant le segment et un boolean qui indique si son ack a été reçu

	static ArrayList ackT=new ArrayList(); // ArrayList qui contiendra chaque ack
	static int ackC=0; //Permet de compter le nombre de fois qu'un ack est repeté (pour faciliter Multiplicative decrease)

	static double wSize=1; // taille de la fenetre initial


	static String num; // sauvegarde ce qu'il va falloir envoyer

	static boolean first=true; // permet de savoir si on envoi le segment de test


	static boolean slowS=true; // permet de savoir si on est en slow start
	static int slowEnd=3; // Seuil du slow Start


	int count=0;// Permet verifier que les envois respectent la taille de la fenetre
	int nano; // Sauvegarde le temps qu'il faut à un segment pour être envoyé et recevoir son ack

	double alpha=0.125;
	double beta=0.25;

	int current=0; // Permet de savoir où on en est dans l'envoi de segment

	public void init(IPLayer ip,Object n) throws Exception // Envoi un segment test pour obtenir le temps neccesaire à l'obtention d'un ack
	{
		t1= (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);
		System.out.println("A envoyer "+n);
		num= (String) n;
		Segment sg=new Segment(-1,-1);
		sendT(sg,ip);
		first=false;
	}

	public void start() throws Exception // commence l'envoi
	{
		toSend=toSplit(num); // Divise le msg en segment

		int help=0;
		t1= (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);
		for (int i=0;i<toSend.size();i++) // Envoi chaque segment
		{
			if(help>=wSize) // Arrete lorsque la taille de la fenetre est atteinte
			{
				break;
			}

			ArrayList c=new ArrayList<>();
			String s= (String) toSend.get(i);
			Integer hI=Integer.parseInt(s);
			Segment sg=new Segment(hI,i);
			c.add(sg);
			c.add(false);

			sendT(sg,host.getIPLayer());
			//System.out.println("ici "+sg);
			checkingT.add(c);
			help++;
			current++;

			//sg.bTimer();
		}
	}

	public void cSend() throws Exception { // continue a envoyer des segment
		int help=0;
		System.out.println("Deplacement de la fentre et envoi de : \n");
		t1= (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);
		for (int i=current;i<toSend.size();i++) // Envoi chaque segment à partir le où on s'est arrêté
		{
			if(help>=wSize) //Arrete lorsque la taille de la fenetre va etre depassé
			{
				//current++;
				break;
			}
			ArrayList c=new ArrayList<>();
			String s= (String) toSend.get(i);
			Integer hI=Integer.parseInt(s);
			Segment sg=new Segment(hI,i);
			c.add(sg);
			c.add(false);

			sendT(sg,host.getIPLayer());
			checkingT.add(c);
			help++;
			current++;
		}
	}

	static int sCount=0;

	static long SRTT=0;
	static long devRTT=3;
	static long RTO=3;


	static boolean nz=false;
	public void sendT(Object m,IPLayer ip) throws Exception // Methode gérant les envois
	{

			if (m.getClass().equals(Segment.class)) // envoi segemnt
			{
				Segment sg = (Segment) m;

				if(sg.getSq()>=0)
				{
					System.out.println("	<--Segment (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
							" host=" + host.name + ", src=" + "192.168.0.1" + ", dgram.dst=" +
							"192.168.0.2, seg =" + sg.getSeg()+" seq= "+sg.getSq());
					sg.bTimer(this, ip);
				}
				ip.send(IPAddress.getByAddress("192.168.0.1"), IPAddress.getByAddress("192.168.0.2"), SRProtocol.IP_PROTO_PINGPONG, sg);

			}
			else // envoi ACK
			{
				//System.out.println("Ici nz "+nz);
				ACK ack = (ACK) m;

				ip.send(IPAddress.getByAddress("192.168.0.2"), IPAddress.getByAddress("192.168.0.1"), SRProtocol.IP_PROTO_PINGPONG, ack);

			}
	}

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

	public ArrayList bufferNoOrder=new ArrayList<>();
	public ArrayList bufferOrder=new ArrayList<>();

	public static int rCount=0;
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {

		Object rec = (Object) datagram.getPayload();
		if (rec.getClass().equals(Segment.class)) // recoit un segment
		{
			Segment msg= (Segment) rec;
			//System.out.println("   expectedSq : " + rCount + ", received : " + msg.getSq() + ", msg : " + msg.getSeg());

			if (msg.getSq()<0)
			{
				ACK ack = new ACK(msg.getSq());
				sendT(ack, host.getIPLayer());
			}
			else
			{
				//System.out.println("   expectedSq : " + rCount + ", received : " + msg.getSq() + ", msg : " + msg.getSeg());

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
				sendT(ack, host.getIPLayer());

			}

		}
		else // recoit un ACK
		{
			long rtt= (long) (host.getNetwork().getScheduler().getCurrentTime()*1000);

			ACK msg = (ACK) rec;

			if(msg.getSq()!=num.length()-1)
			{
				if (msg.getSq() < 0) // message de test
				{
					System.out.println("Temps : " + (int) (host.getNetwork().getScheduler().getCurrentTime() * 1000) + "ms\n");

					if (first)// init rtt et rto
					{
						first = false;
						SRTT = (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);
						devRTT = rtt / 2;
						RTO = (SRTT) + (4 * devRTT);
					}
					start(); // on commence a tout renvoyer
				}
				else {

					if (!ackT.contains(msg)) {

						ackT.add(msg);
						ArrayList a = (ArrayList) checkingT.get(msg.getSq());
						Segment s = (Segment) a.get(0);
						long h = (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);

						System.out.println("->ACK (" + h + "ms)" +
								" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
								datagram.dst + ", iif=" + src + " seq= " + msg.getSq() + "  et Timer annulé pour " + s);
						//System.out.println("Receive for " + s.getSeg() + " seq " + s.getSq() + " ");

						s.sTimer();
						a.set(1, true); // ACK du segment reçu

						count++;
						if (first)// update rtt et rto
						{
							//System.out.println("<Mise à jour RTO>");
							//s.sTimer();
							first = false;
							SRTT = (long) (((1 - alpha) * rtt) + (alpha * h / count));
							devRTT = (long) (((1 - beta) * devRTT) + (beta * Math.abs(SRTT - (h / count))));
							System.out.println("<Mise à jour RTO :"+RTO+"ms -> "+((SRTT) + (4 * devRTT))+"ms >");

							RTO = (SRTT) + (4 * devRTT);
						}

						if (count >= wSize)
						{
							//long h = (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);
							//TimeUnit.MILLISECONDS.sleep(RTO); // Attente que les ACK revienne

							t2=h;

							count = (int) wSize;
							//System.out.println("\n<Taille de la fenetre atteinte " + wSize + " et " + "slowS " + slowS + ">\n");
							int j = 0;


							// Obtenir element a renvoyer
							ArrayList toResend = new ArrayList<>();
							for (int i = 0; i < checkingT.size(); i++)
							{
								ArrayList v = (ArrayList) checkingT.get(i);
								boolean vh = (boolean) v.get(1);
								Segment sg = (Segment) v.get(0);
								if (sg.repeat)
								{
									System.out.println("	<-on doit renvoyer : seg " + sg.getSeg() + " seq : " + sg.getSq() + " " + (host.getNetwork().getScheduler().getCurrentTime() * 1000) + "->");
									toResend.add(v.get(0));
								}
							}

								System.out.println("\nIl n'y a rien a renvoyer!");
								first=true; // aide pour recalculer RTO apres
								if (slowS) // slow start
								{
									wSize++;
									System.out.println("	<slow start : Taille de la fentre " + (wSize - 1) + "->" + wSize + " >\n");
									if (wSize == slowEnd) {
										slowS = false;
										System.out.println("<Slow start fini>\n");
									}
									cSend();
								} else {
									//Additive increase

									//System.out.println("on attend pour le fenetre");

									count = 0;
									//double h1 = (1 - alpha) * wSize;
									//double h2 = (0.125 * wSize);
									//System.out.println("h1 " + h1 + " et h2 : " + h2 + " et wSize : " + wSize);
									System.out.println("	<Additive increase Taille de la fentre " + (wSize) + "->" + (wSize + (1 / wSize)) + " >\n");

									wSize = wSize + (1 / wSize);
									//System.out.println("ok attendu et wSize : " + wSize);
									cSend();
									//TimeUnit.SECONDS.sleep(2);
								}


						}
					} else //Multiplicative decrease
					{
						ackC++;
						if (ackC == 3) {
							System.out.println("	<Multiplicative decrease :Taille de la fentre " + (wSize) + "->" + (wSize / 2) + " >\n");
							wSize = wSize / 2;
							ackC = 0;
						}
					}
				}
			}
			else
			{
				ackT.add(msg.getSq());
				ArrayList a = (ArrayList) checkingT.get(msg.getSq());
				Segment s = (Segment) a.get(0);
				long h = (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);

				System.out.println("->ACK (" + h + "ms)" +
						" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
						datagram.dst + ", iif=" + src + " seq= " + msg.getSq() + "  et Timer annulé pour " + s);

				System.out.println("\n<Envoi completement reçu!>");
				System.exit(0);
			}
		}
	}

}
