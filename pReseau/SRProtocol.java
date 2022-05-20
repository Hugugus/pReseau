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
package reso.examples.pReseau;

import reso.ip.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SRProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_PINGPONG= Datagram.allocateProtocolNumber("PING-PONG");
	
	private final IPHost host;


	public SRProtocol(IPHost host) {
		this.host= host;
	}


	Random random = new Random();

	ArrayList res=new ArrayList<>(); // tableau des valeurs final


	static ArrayList toSend=new ArrayList<>(); // ArrayList qui contiendra chaque segment à envoyer

	static ArrayList checkingT=new ArrayList<>(); // ArrayList qui contiendra pour chaque segment, une arraylist contenant le segment et un boolean qui indique si son ack a été reçu

	static ArrayList ackT=new ArrayList(); // ArrayList qui contiendra chaque ack
	static int ackC=0; //Permet de compter le nombre de fois qu'un ack est repeté (pour faciliter Multiplicative decrease)

	static double wSize=1; // taille de la fenetre initial

	static String num; // sauvegarde ce qu'il va falloir envoyer

	static boolean first=true; // permet de savoir si on est dans l'envoi du 1er packet

	static boolean slowS=true; // permet de savoir si on est en slow start
	static int slowEnd=3; // Seuil du slow Start


	int count=0;// Permet verifier que les envois respectent la taille de la fenetre

	double alpha=0.125;
	double beta=0.25;

	static int resF=0;


	int current=0; // Permet de savoir où on en est dans l'envoi de segment

	public void init(IPLayer ip,Object n) throws Exception // Initie l'envoi de segment
	{
		System.out.println("A envoyer "+n);
		num= (String) n;
		start();
		first=true;
	}

	public void start() throws Exception // commence l'envoi
	{
		toSend=toSplit(num); // Divise le msg en segment

		int help=0;
		for (int i=0;i<toSend.size();i++) // Envoi chaque segment
		{
			if(help>=wSize) // Arrete lorsque la taille de la fenetre est atteinte
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
		for (int i=current+1;i<toSend.size();i++) // Envoi chaque segment à partir là où on s'est arrêté
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


	static long SRTT=0;
	static long devRTT=3;
	static long RTO=3;

	 long getTime() // Obtiens le temps actuel dans la simulation
	{
		long res= (long) (host.getNetwork().getScheduler().getCurrentTime()*1000);
		return res;
	}


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

	public static ArrayList toSplit(Object n) // Permet de diviser un character en segment
	{
		ArrayList res=new ArrayList();
		//System.out.println("init "+n);
		String s=""+n;
		for(int i=0;i<s.length();i++)
		{
			res.add(""+s.charAt(i));
			resF++;
		}
		return res;
	}

	public static ArrayList bufferNoOrder=new ArrayList<>();// list element dans le desordre
	public static ArrayList bufferOrder=new ArrayList<>(); //List d'element en ordre

	public static int rCount=0; // Permet de savoir quel numéro de sequence doit arriver
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {

		Object rec = (Object) datagram.getPayload();
		if (rec.getClass().equals(Segment.class)) // recoit un segment
		{
			Segment msg= (Segment) rec;


				ACK ack = new ACK(msg.getSq());
				//System.out.println("ici rCount= "+rCount+" et reçu= "+ack.getSq());
				if (msg.getSq() == (rCount)) // bon numero de sequence reçu
				{

					bufferOrder.add(msg);
					rCount++;

				}
				else // mauvais numero de sequence reussi
				{
					bufferNoOrder.add(msg);
				}

				sendT(ack, Demo.host2.getIPLayer());





		}
		else // recoit un ACK
		{
			long rtt= (long) (host.getNetwork().getScheduler().getCurrentTime()*1000);

			ACK msg = (ACK) rec;
			ArrayList a = (ArrayList) checkingT.get(msg.getSq());
			Segment s = (Segment) a.get(0);

			if (!s.received)
					{

						resF--; // Permet de savoir le nombre elements restant

						int r=random.nextInt(50); //aleatoire

						if (first) // Evite beug de l'aleatoire quand 1er packet
							r=7;

						System.out.println("Random : "+r);

						if (r%6==0) //Simulation
						{
							System.out.println("---------------------< Simulation de perte d'ack pour sq= "+s.getSq()+">");
						}
						else	// ACK reçu normalement
						{
							s.sTimer();
							s.received = true;
							ackT.add(msg.getSq());
							res.add(s.getSeg());
							long h = (long) (host.getNetwork().getScheduler().getCurrentTime() * 1000);

							System.out.println("->ACK (" + h + "ms)" +
									" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
									datagram.dst + ", iif=" + src + " seg= " + s.getSeg() + "  et Timer annulé pour sq= " + s.getSq());
							//System.out.println("Receive for " + s.getSeg() + " seq " + s.getSq() + " ");


							a.set(1, true); // ACK du segment reçu

							count++;

							if (first)// update rtt et rto
							{

								first = false;
								SRTT = (long) (((1 - alpha) * rtt) + (alpha * h / count));
								devRTT = (long) (((1 - beta) * devRTT) + (beta * Math.abs(SRTT - (h / count))));
								System.out.println("		<SRTT= " + SRTT + " | devRTT= " + devRTT + "| RTO= " + RTO + " >");


								RTO = (SRTT) + (4 * devRTT);
							}

							if (count >= wSize) // taille de la fentre atteinte
							{

								count = (int) wSize;

							}
						}
								boolean w = true;

								// Continue d'envoyer tant que elements manquants
								while (w)
								{
									int check=0;
									for (int i = 0; i < checkingT.size(); i++)
									{
										ArrayList v = (ArrayList) checkingT.get(i);
										boolean vh = (boolean) v.get(1);
										Segment sg = (Segment) v.get(0);
										if (sg.repeat)
										{
											System.out.println("	<-on doit renvoyer : seg " + sg.getSeg() + " seq : " + sg.getSq() + " " + getTime() + "->");
											sendT(sg,Demo.host1.getIPLayer());
											check++;
										}
									}
									if (check == 0)
									{
										break;
									}
									else {
										System.out.println("\n< Il y a des segments a renvoyer! >");
										TimeUnit.MILLISECONDS.sleep(RTO); // attend que les segments arrivent
									}
								}

								System.out.println("\nIl n'y a rien a renvoyer!");

								first = true; // aide pour recalculer RTO apres

								if (slowS) // slow start
								{
									wSize++;
									System.out.println("	<slow start : Taille de la fentre " + (wSize - 1) + "->" + wSize + " >\n");
									if (wSize == slowEnd) {
										slowS = false;
										System.out.println("<Slow start fini>\n");
									}
									cSend();
								}
								else {
									//Additive increase


									count = 0;

									System.out.println("	<Additive increase Taille de la fentre " + (wSize) + "->" + (wSize + (1 / wSize)) + " >\n");

									wSize = wSize + (1 / wSize);
									cSend();
								}



							if (resF == 0)
							{
								ackT.add(msg.getSq());

								System.out.println("\n<Envoi completement reçu!>\n");

								//System.out.println(bufferOrder);
								ArrayList ff=new ArrayList<>();
								ff.addAll(bufferOrder);
								ff.addAll(bufferNoOrder);
								//Collections.shuffle(ff);
								//System.out.println("melangé "+ff);
								Collections.sort(ff, new Comparator<Segment>(){
									@Override
									//Permet de trier les segments en fonction du numéro de sequece dans les bufferOrder et bufferNoOrder
									public int compare(Segment o1, Segment o2) {
										int a=o1.getSq();
										int b=o2.getSq();
										return Integer.compare(a,b);
									}

								});
								String txtf="";
								for (int i=0;i<ff.size();i++)
								{
									Segment v= (Segment) ff.get(i);
									txtf+=v.getSeg();
								}


								System.out.println("Voici le texte reçu : "+txtf);

								System.exit(0);
							}

					}
					else //Multiplicative decrease
					{
						s.sTimer();
						ackC++;
						if (ackC == 3) {
							System.out.println("	<Multiplicative decrease :Taille de la fentre " + (wSize) + "->" + (wSize / 2) + " >\n");
							wSize = wSize / 2;
							ackC = 0;
						}
					}

		}
	}

}
